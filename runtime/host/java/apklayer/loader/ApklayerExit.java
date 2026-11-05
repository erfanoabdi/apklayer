package apklayer.loader;

/**
 * Target of the converter's exit redirect (dex2jvm HostDefaults.REDIRECT_EXIT): an app's System.exit, Runtime.exit and Runtime.halt arrive here.
 * On Android they end the app's process and the ActivityManager relaunches it when an activity start is pending: the "restart myself" idiom
 * (JakeWharton's ProcessPhoenix, shipped by Aurora Store and NewPipe: it starts PhoenixActivity, then exits). Here one JVM is the whole app, so
 * an exit within RESTART_MS of an activity start (ShadowHostInstrumentation sets the property apklayer.laststart) keeps the JVM: the started
 * activities are the relaunch. shortcut: they run in this process with its old statics, not a fresh one. Any other exit ends the JVM as before.
 */
public final class ApklayerExit {
    private ApklayerExit() {}

    private static final long RESTART_MS = 5000;

    private static boolean restarting() {
        String last = System.getProperty("apklayer.laststart");
        return last != null && System.nanoTime() / 1_000_000 - Long.parseLong(last) < RESTART_MS;
    }

    public static void systemExit(int code) {
        if (restarting()) { System.out.println("[apklayer] exit(" + code + ") after an activity start: the restart is the started activity"); return; }
        System.exit(code);
    }

    public static void runtimeExit(Runtime runtime, int code) {
        if (restarting()) { System.out.println("[apklayer] exit(" + code + ") after an activity start: the restart is the started activity"); return; }
        runtime.exit(code);
    }

    public static void runtimeHalt(Runtime runtime, int code) {
        if (restarting()) { System.out.println("[apklayer] halt(" + code + ") after an activity start: the restart is the started activity"); return; }
        runtime.halt(code);
    }
}
