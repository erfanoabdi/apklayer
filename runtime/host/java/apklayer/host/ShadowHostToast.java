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
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/**
 * Toasts on screen: what NotificationManagerService and SystemUI's ToastUI do with an app's toast, done in the app's process with the framework's
 * ToastPresenter (a TYPE_TOAST window for the toast's duration). Robolectric's shadow only recorded show(), so no app's toast was ever seen.
 */
@Implements(className = "android.widget.Toast", isInAndroidSdk = false)
public class ShadowHostToast {
    private static final int SHORT_DELAY = 2000, LONG_DELAY = 3500;  // NotificationManagerService's
    private static Toast showing;
    private static Runnable hide;
    @RealObject private Toast real;

    // shortcut: a new toast replaces the one on screen instead of waiting in NotificationManagerService's queue; Toast.Callback is not called
    @Implementation protected void show() {
        Context context = ReflectionHelpers.getField(real, "mContext");
        View custom = ReflectionHelpers.getField(real, "mNextView");
        CharSequence text = ReflectionHelpers.getField(real, "mText");
        if (custom == null && text == null) throw new IllegalStateException("You must either set a text or a view");
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
          try {  // as Toast.TN.handleShow: a toast that cannot be shown (a finished activity's context) never takes the main thread down
            if (hide != null) hide.run();
            ToastPresenter presenter = new ToastPresenter(context, null, INotificationManager.Stub.asInterface(ServiceManager.getService(Context.NOTIFICATION_SERVICE)),
                    context.getOpPackageName());
            View view = custom != null ? custom : ToastPresenter.getTextToastView(context, text);
            presenter.show(view, new Binder(), new Binder(), real.getDuration(), real.getGravity(), real.getXOffset(), real.getYOffset(),
                    real.getHorizontalMargin(), real.getVerticalMargin(), null);
            Runnable gone = new Runnable() {
                @Override public void run() {
                    main.removeCallbacks(this);
                    if (hide == this) { hide = null; showing = null; }
                    try { presenter.hide(null); } catch (RuntimeException e) { System.out.println("[apklayer] toast not hidden: " + e); }
                }
            };
            hide = gone;
            showing = real;
            main.postDelayed(gone, real.getDuration() == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY);
          } catch (RuntimeException e) { System.out.println("[apklayer] toast not shown: " + e); }
        });
    }

    @Implementation protected void cancel() {
        new Handler(Looper.getMainLooper()).post(() -> { if (showing == real && hide != null) hide.run(); });
    }
}
