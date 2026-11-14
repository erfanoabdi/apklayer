package apklayer.host;

import android.app.INotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.ServiceManager;
import android.view.View;
import android.widget.Toast;
import android.widget.ToastPresenter;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/**
 * Toasts on screen: what NotificationManagerService and SystemUI's ToastUI do with an app's toast, done in the app's process with the framework's
 * ToastPresenter (a TYPE_TOAST window for the toast's duration). As NotificationManagerService's mToastQueue: one toast on screen at a time, the
 * others wait in show() order (at most MAX_PACKAGE_TOASTS), show() of a queued toast keeps its place, cancel() hides it or takes it out of the
 * queue; Toast.Callback hears onToastShown and onToastHidden. Robolectric's shadow only recorded show(), so no app's toast was ever seen.
 * Across processes the hub grants one on-screen slot at a time (HubProtocol.TOAST_SLOT).
 */
@Implements(className = "android.widget.Toast", isInAndroidSdk = false)
public class ShadowHostToast {
    private static final int SHORT_DELAY = 2000, LONG_DELAY = 3500, MAX_PACKAGE_TOASTS = 5;  // NotificationManagerService's
    private record Entry(Toast toast, Context context, View custom, CharSequence text) {}
    private static final List<Entry> queue = new ArrayList<>();  // main thread only; the first one is on screen
    private static Entry waiting;  // asked the hub for the slot
    private static Runnable hide;  // hides the first one
    @RealObject private Toast real;

    private static Handler main() { return new Handler(Looper.getMainLooper()); }

    @Implementation protected void show() {
        Context context = ReflectionHelpers.getField(real, "mContext");
        View custom = ReflectionHelpers.getField(real, "mNextView");
        CharSequence text = ReflectionHelpers.getField(real, "mText");
        if (custom == null && text == null) throw new IllegalStateException("You must either set a text or a view");
        Entry entry = new Entry(real, context, custom, text);
        main().post(() -> {
            for (Entry queued : queue) if (queued.toast == real) return;  // NotificationManagerService updates the record in place
            if (queue.size() >= MAX_PACKAGE_TOASTS) { System.out.println("[apklayer] toast dropped: " + MAX_PACKAGE_TOASTS + " toasts queued"); return; }
            queue.add(entry);
            if (queue.size() == 1 && waiting == null) showNext();
        });
    }

    @Implementation protected void cancel() {
        main().post(() -> {
            if (!queue.isEmpty() && queue.get(0).toast == real && hide != null) hide.run();
            else queue.removeIf(e -> e.toast == real);
        });
    }

    /** The first queued toast on screen for its duration; one that cannot be shown (a finished activity's context) is skipped, never takes the main thread down. */
    private static void showNext() {
        while (!queue.isEmpty()) {
            Entry entry = queue.get(0);
            try { if (HubClient.connected() && !HubHost.hub) { slot(entry); return; } show(entry); return; }
            catch (RuntimeException e) { System.out.println("[apklayer] toast not shown: " + e); queue.remove(0); }
        }
    }

    /** The hub's one toast queue over every app and process: asked off the main thread, the toast shows once the hub grants the slot (it frees it after the duration). */
    private static void slot(Entry entry) {
        waiting = entry;
        long ms = entry.toast.getDuration() == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY;
        new Thread(() -> {
            try { HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.TOAST_SLOT, request -> request.writeLong(ms), reply -> null); }
            catch (RuntimeException hubGone) { /* shown without the slot */ }
            main().post(() -> {
                if (waiting == entry) waiting = null;
                if (queue.isEmpty() || queue.get(0) != entry) { if (hide == null && waiting == null) showNext(); return; }  // cancelled while it waited
                try { show(entry); } catch (RuntimeException e) { System.out.println("[apklayer] toast not shown: " + e); queue.remove(entry); showNext(); }
            });
        }, "toast-slot").start();
    }

    private static void show(Entry entry) {
        Handler main = main();
        Toast toast = entry.toast;
        ToastPresenter presenter = new ToastPresenter(entry.context, null, INotificationManager.Stub.asInterface(ServiceManager.getService(Context.NOTIFICATION_SERVICE)),
                entry.context.getOpPackageName());
        View view = entry.custom != null ? entry.custom : ToastPresenter.getTextToastView(entry.context, entry.text);
        presenter.show(view, new Binder(), new Binder(), toast.getDuration(), toast.getGravity(), toast.getXOffset(), toast.getYOffset(),
                toast.getHorizontalMargin(), toast.getVerticalMargin(), null);
        Runnable gone = new Runnable() {
            @Override public void run() {
                main.removeCallbacks(this);
                if (hide != this) return;
                hide = null;
                queue.remove(entry);
                try { presenter.hide(null); } catch (RuntimeException e) { System.out.println("[apklayer] toast not hidden: " + e); }
                for (Toast.Callback callback : callbacks(toast)) callback.onToastHidden();
                showNext();
            }
        };
        hide = gone;
        main.postDelayed(gone, toast.getDuration() == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY);
        for (Toast.Callback callback : callbacks(toast)) callback.onToastShown();
    }

    /** Toast.addCallback's list (Toast.CallbackBinder copies it the same way before it calls them). */
    private static List<Toast.Callback> callbacks(Toast toast) {
        List<Toast.Callback> list = ReflectionHelpers.getField(toast, "mCallbacks");
        synchronized (list) { return new ArrayList<>(list); }
    }
}
