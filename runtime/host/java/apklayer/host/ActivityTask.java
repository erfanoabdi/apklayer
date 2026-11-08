package apklayer.host;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.View;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

/**
 * The app process's task (one per process): its back stack of activities, the launches the app's startActivity() calls and the hub ask for
 * (with Android's task rules), finish(), and the results that go back to the activity that asked. AppHost's run loop drives it.
 */
final class ActivityTask {

    /** The window's focus (AppHost's window state), which a started activity takes over. */
    private final BooleanSupplier windowActive;
    /** Until then an empty stack does not end the run (a launcher activity that finished itself, its real screen still to come). */
    long graceUntilMs;

    ActivityTask(BooleanSupplier windowActive) {
        this.windowActive = windowActive;
    }

    private static volatile Activity taskRoot;
    private static volatile boolean taskRootBuilding;
    /** Activity.isTaskRoot(): launcher activities that finish themselves when they are not the root (AnkiDroid's IntentHandler) must see the task start here. */
    static boolean isTaskRoot(Activity activity) { return activity == taskRoot || (taskRootBuilding && taskRoot == null) || taskRootBuilding; }
    static final Deque<ActivityController<? extends Activity>> stack = new ArrayDeque<>();  // one task per app process
    /** The task's activities, the top one first; a finishing one is null (Android's activity starter and navigateUpTo pass over it). */
    static List<Activity> task() { return stack.stream().map(c -> c.get().isFinishing() ? null : (Activity) c.get()).toList(); }
    /** Intents handed to other apps that will answer: routed handle -> {requesting activity, request intent}. */
    private final java.util.Map<HostApps.Routed, Object[]> crossApp = new java.util.HashMap<>();
    /** The activity another app started this process for (its result goes back, PLAN P6). */
    private Activity incomingActivity;
    /** Activities started with startActivityForResult -> the request intent, to return their setResult. */
    private final java.util.Map<Activity, Intent> resultFor = new java.util.IdentityHashMap<>();
    final Set<Activity> stopped = new HashSet<>();
    /** Activities another app started through the hub for a result -> where their result goes. */
    private final java.util.Map<Activity, android.os.ResultReceiver> hubResults = new java.util.IdentityHashMap<>();

    /** The first activity: the app's launcher one, or the one another app started this process for. */
    void launchFirst(Intent intent, boolean incoming) {
        finishedInCreate = null;
        launch(intent);
        if (!incoming) { finishedInCreate = null; return; }
        if (finishedInCreate == null && !stack.isEmpty()) incomingActivity = stack.peek().get();
        answerWhenDone(HubActivityClient.takeReceiver());
    }

    /** The activity of the launch under way that finish()ed in onCreate (microG's GamesSignInActivity without an account): its result is final already. */
    private Activity finishedInCreate;

    /** Where the launched activity's result goes: answered now when it finished in onCreate, else when it finishes (followNavigation). */
    private void answerWhenDone(android.os.ResultReceiver resultTo) {
        Activity done = finishedInCreate;
        finishedInCreate = null;
        if (resultTo == null) return;
        if (done != null) {
            ShadowActivity answered = Shadow.extract(done);
            HubActivityClient.answer(resultTo, answered.getResultCode(), answered.getResultIntent());
            // its answer is final and nothing of this process is on screen: no trampoline grace, the process (and its window, still the
            // splash) ends now, as Android removes the starting window (microG's GamesSignInActivity without an account over OTR2)
            if (stack.isEmpty()) graceUntilMs = 0;
        } else if (!stack.isEmpty()) hubResults.put(stack.peek().get(), resultTo);
    }

    /** An intent another app sent here through the hub: started like one of our own, its result goes to the sender's receiver. */
    void startForHub(Intent intent, android.os.ResultReceiver resultTo) {
        if (launching) { waitingForHub.add(new Object[] {intent, resultTo}); return; }  // planned once the activity being built is on the stack
        finishedInCreate = null;
        launch(intent);
        answerWhenDone(resultTo);
    }

    /** A launch runs the activity's lifecycle, which runs the main looper: a hub intent coming then (a second `apklayer run` for a singleTask
     *  activity) would be planned against a stack without the activity being built and start a second one instead of reaching onNewIntent. */
    private boolean launching;
    private final Deque<Object[]> waitingForHub = new ArrayDeque<>();

    private static ActivityInfo resolved(Intent intent) throws android.content.pm.PackageManager.NameNotFoundException {
        android.content.pm.ResolveInfo found = RuntimeEnvironment.getApplication().getPackageManager().resolveActivity(intent, 0);
        if (found == null) throw new android.content.pm.PackageManager.NameNotFoundException("no activity of this app handles it");
        return found.activityInfo;
    }

    /** Builds and fully starts the activity, as the simulator does (Robolectric.setupActivity). */
    private void launch(Intent intent) {
        launch(intent, stack.isEmpty() ? null : ((ShadowActivity) Shadow.extract(stack.peek().get())).getNextStartedActivityForResult());
    }

    /** @param request the start's record, with its request code: startActivityForResult is only recorded by Robolectric, the host delivers the result (PLAN P5.3) */
    private void launch(Intent intent, ShadowActivity.IntentForResult request) {
        boolean outer = !launching;
        launching = true;
        try {
            launchNow(intent, request);
        } finally {
            if (outer) launching = false;
        }
        if (outer) while (!launching && !waitingForHub.isEmpty()) { Object[] next = waitingForHub.poll(); startForHub((Intent) next[0], (android.os.ResultReceiver) next[1]); }
    }

    private void launchNow(Intent intent, ShadowActivity.IntentForResult request) {
        if (Boolean.getBoolean("apklayer.tracestart")) System.out.println("[apklayer] launch " + intent + " flags=0x" + Integer.toHexString(intent.getFlags()));
        boolean wantsResult = request != null && request.requestCode >= 0;
        Class<? extends Activity> cls;
        ActivityInfo info;
        try {
            info = intent.getComponent() != null
                    ? RuntimeEnvironment.getApplication().getPackageManager().getActivityInfo(intent.getComponent(), 0)
                    : resolved(intent);
            // an <activity-alias> names its real class in targetActivity
            cls = Class.forName(info.targetActivity != null ? info.targetActivity : info.name)
                    .asSubclass(Activity.class);
        } catch (ClassNotFoundException | NullPointerException | android.content.pm.PackageManager.NameNotFoundException e) {
            if (HostInstallIntents.handle(intent, wantsResult ? stack.peek().get() : null)) return;  // the package installer's activities are the hub's
            if (HostPortals.open(intent)) return;
            if (HostPortals.requestPermissions(intent, request == null ? -1 : request.requestCode, stack.peek().get())) return;
            if (HostPortals.requestRole(intent, stack.peek().get(), wantsResult)) return;
            // the Storage Access Framework pickers belong to the installed DocumentsUI (through the hub); the desktop's file dialog answers only when no installed app does
            // (a picker command of the user's, APKLAYER_PICKER, is the desktop's dialog by choice: it answers first)
            if (HostPortals.isPicker(intent) && System.getenv("APKLAYER_PICKER") == null && HubActivityClient.route(intent, wantsResult ? stack.peek().get() : null, intent)) return;
            Intent picked = HostPortals.pick(intent);
            if (picked != null || HostPortals.isPicker(intent)) { // a desktop file dialog answers the document picker
                if (wantsResult) ((ShadowActivity) Shadow.extract(stack.peek().get()))
                        .receiveResult(intent, picked != null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, picked);
                return;
            }
            if (HubActivityClient.route(intent, wantsResult ? stack.peek().get() : null, intent)) return; // through the hub
            HostApps.Routed routed = HubActivityClient.enabled() ? null : HostApps.route(intent, wantsResult); // another installed app handles it
            if (routed != null) {
                if (wantsResult) crossApp.put(routed, new Object[] {stack.peek().get(), intent});
                return;
            }
            // Android would crash the app here; on the desktop the app stays on its current screen.
            System.out.println("[apklayer] cannot start " + intent + ": " + e);
            return;
        }
        // task rules: reuse an instance already on the stack, clear what is above it, or clear everything
        List<Class<?>> classes = task().stream().<Class<?>>map(a -> a == null ? null : a.getClass()).toList();
        if (LaunchRules.bringsForward(classes, cls, intent)) { System.out.println("[apklayer] brought forward " + cls.getName() + " depth=" + stack.size()); return; }
        LaunchRules.Plan plan = LaunchRules.plan(classes, cls, intent.getFlags(), info.launchMode);
        if (plan.clearAll()) {
            while (!stack.isEmpty()) destroyTop();
        } else if (plan.above() >= 0) {
            for (int i = 0; i < plan.above(); i++) destroyTop();
            if (plan.reuse()) {
                ActivityController<? extends Activity> target = stack.peek();
                target.newIntent(intent);
                if (stopped.remove(target.get())) setVisible(target.restart().resume().visible(), true);
                else target.resume();
                System.out.println("[apklayer] reused " + cls.getName() + " depth=" + stack.size());
                return;
            }
            destroyTop(); // CLEAR_TOP on a standard activity: it is recreated
        }
        ActivityController<? extends Activity> previous = stack.peek();
        if (previous != null) previous.pause();
        boolean bottom = stack.isEmpty();  // the first activity of the task answers isTaskRoot() already while it is created
        taskRootBuilding = bottom;
        HostRoles.install();
        ActivityController<? extends Activity> built = Robolectric.buildActivity(cls, intent);
        String caller = intent.getStringExtra(HubActivityClient.CALLER);  // the app that started it for a result (Activity.getCallingPackage)
        if (caller != null) ((ShadowActivity) Shadow.extract(built.get())).setCallingPackage(caller);
        // the manifest's screenOrientation is the activity's requested orientation from the start (Robolectric starts it at -1): AppHost.followOrientation
        // reshapes the window, so a landscape-locked game gets a landscape surface and landscape pointer coordinates
        if (info.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) built.get().setRequestedOrientation(info.screenOrientation);
        ActivityController<? extends Activity> c = built.create();
        taskRootBuilding = false;
        if (bottom) taskRoot = c.get();
        if (c.get().isFinishing()) {  // finish() in onCreate (Telegram starts its intro screen so): Android destroys it without start or resume
            c.destroy();
            System.out.println("[apklayer] finished in onCreate: " + c.get().getClass().getName());
            if (finishedInCreate == null) finishedInCreate = c.get();
            Intent next = ((ShadowApplication) Shadow.extract(RuntimeEnvironment.getApplication())).getNextStartedActivity();  // what it started before (the intro screen)
            if (next != null) launchStarted(next);
            else if (previous != null) previous.resume();
            else graceUntilMs = System.nanoTime() / 1_000_000 + 20000;  // a trampoline that starts the real screen from a coroutine (AnkiDroid's IntentHandler)
            return;
        }
        c.start().postCreate(null).resume().visible();  // the rest of ActivityController.setup(), focus as the window has it
        HostWindows.focus(c, windowActive.getAsBoolean());
        // As in Android, the previous activity is stopped only once an opaque activity covers it;
        // behind a translucent one (dialog-style) it stays paused and visible.
        if (previous != null && !HostWindows.isTranslucent(c.get())) {
            setVisible(previous.stop(), false);
            stopped.add(previous.get());
        }
        stack.push(c);
        // finish() and then startActivity() (an activity restarting itself): Android destroys the finished one once the new one is up
        for (ActivityController<? extends Activity> old : List.copyOf(stack))
            if (old != c && old.get().isFinishing()) { stack.remove(old); if (!stopped.remove(old.get())) old.stop(); old.destroy(); }
        if (wantsResult) resultFor.put(c.get(), intent);
        // An activity that adds a window in onCreate (e.g. a dialog) otherwise never records its
        // own decor's first draw, and the composed screen would show nothing behind the dialog.
        c.get().getWindow().getDecorView().invalidate();
        System.out.println("[apklayer] started " + c.get().getClass().getName() + " depth=" + stack.size());
        StartTrace.mark("activity resumed " + c.get().getClass().getName());
        CdsDump.afterStart();  // an app window's classes (views, widgets, drawing), not only a component host's
        AppSpare.launchStarted();
    }

    /**
     * Robolectric hands started activities back newest first; Android starts them in call order (startActivities: the last one ends on top).
     * Each start's for-result record (a second list, also newest first) stays with its intent.
     */
    private void launchStarted(Intent newest) {
        ShadowApplication shadowApp = Shadow.extract(RuntimeEnvironment.getApplication());
        ShadowActivity top = stack.isEmpty() ? null : Shadow.extract(stack.peek().get());
        java.util.ArrayDeque<Object[]> started = new java.util.ArrayDeque<>();
        for (Intent i = newest; i != null; i = shadowApp.getNextStartedActivity()) started.push(new Object[] {i, top == null ? null : top.getNextStartedActivityForResult()});
        while (!started.isEmpty()) { Object[] start = started.pop(); launch((Intent) start[0], (ShadowActivity.IntentForResult) start[1]); }
    }

    /** Hands an answer from another app (or its disappearance, as RESULT_CANCELED) to the activity that asked. */
    void deliverCrossAppResults() {
        for (java.util.Iterator<java.util.Map.Entry<HostApps.Routed, Object[]>> it = crossApp.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<HostApps.Routed, Object[]> e = it.next();
            Object[] answer = HostApps.readResult(e.getKey());
            if (answer == null && e.getKey().process() != null && e.getKey().process().isAlive()) continue;
            if (answer == null) answer = new Object[] {Activity.RESULT_CANCELED, null};
            it.remove();
            Activity requester = (Activity) e.getValue()[0];
            if (stack.stream().anyMatch(c -> c.get() == requester))
                ((ShadowActivity) Shadow.extract(requester)).receiveResult((Intent) e.getValue()[1], (Integer) answer[0], (Intent) answer[1]);
        }
    }

    /** Destroys the top activity (the paused/stopped state it is in decides which lifecycle calls remain). */
    private void destroyTop() {
        ActivityController<? extends Activity> top = stack.pop();
        resultFor.remove(top.get());
        android.os.ResultReceiver abandoned = hubResults.remove(top.get());
        if (abandoned != null) HubActivityClient.answer(abandoned, Activity.RESULT_CANCELED, null);
        if (stopped.remove(top.get())) top.destroy();
        else top.pause().stop().destroy();
    }

    /** Follows the app's startActivity() calls and finish() of the top activity. */
    void followNavigation() {
        ShadowApplication shadowApp = Shadow.extract(RuntimeEnvironment.getApplication());
        Intent next = shadowApp.getNextStartedActivity();
        if (next != null) {
            launchStarted(next);
            return;
        }
        if (stack.isEmpty()) return;
        Activity top = stack.peek().get();
        if (top.isFinishing()) {
            stack.pop().pause().stop().destroy();
            if (top == incomingActivity) {
                ShadowActivity answered = Shadow.extract(top);
                HostApps.writeResult(answered.getResultCode(), answered.getResultIntent());
            }
            android.os.ResultReceiver answerTo = hubResults.remove(top);
            if (answerTo != null) HubActivityClient.answer(answerTo, ((ShadowActivity) Shadow.extract(top)).getResultCode(), ((ShadowActivity) Shadow.extract(top)).getResultIntent());
            Intent request = resultFor.remove(top);
            if (request != null && !stack.isEmpty()) {
                ShadowActivity child = Shadow.extract(top);
                ((ShadowActivity) Shadow.extract(stack.peek().get())).receiveResult(request, child.getResultCode(), child.getResultIntent());
            }
            System.out.println("[apklayer] finished " + top.getClass().getName() + " depth=" + stack.size());
            if (!stack.isEmpty()) {
                ActivityController<? extends Activity> c = stack.peek();
                if (stopped.remove(c.get())) setVisible(c.restart().resume().visible(), true);
                else c.resume();
            }
        }
    }

    /**
     * ActivityThread#updateVisibility: a stopped activity's decor is INVISIBLE and becomes VISIBLE
     * again when it returns. Stopped windows then stay out of the composed screen, and the
     * visibility change redraws, which makes ScreenUpdateHandler capture a fresh frame.
     */
    static void setVisible(ActivityController<? extends Activity> c, boolean visible) {
        c.get().getWindow().getDecorView().setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        HostWindows.setAttachedVisible(c.get(), visible);
    }

    /** What was kept for an activity that configurationChange replaced follows the new instance. */
    void recreated(Activity old, Activity now) {
        for (java.util.Map<Activity, ?> kept : List.<java.util.Map<Activity, ?>>of(resultFor, hubResults)) if (kept.containsKey(old)) move(kept, old, now);
        if (incomingActivity == old) incomingActivity = now;
        if (taskRoot == old) taskRoot = now;
    }

    @SuppressWarnings("unchecked")
    private static <V> void move(java.util.Map<Activity, V> map, Activity from, Activity to) { map.put(to, map.remove(from)); }
}
