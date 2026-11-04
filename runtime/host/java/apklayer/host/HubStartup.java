package apklayer.host;

import android.os.IBinder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Start the shared instance daemon on demand and wait for real Binder publication: events only (the hub's ready property, its death), no timers. */
final class HubStartup {
    private HubStartup() {}

    static IBinder connect() {
        Path logs = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", System.getProperty("user.home") + "/.local/state/apklayer/default"));
        for (;;) {
            IBinder hub = RealBinder.lookup(HubProtocol.SERVICE);
            if (hub != null && hub.isBinderAlive() && hub.pingBinder()) return hub;
            try {
                Files.createDirectories(logs);
                // `apklayer hub` starts the hub, or returns once the running one is ready, or takes over from one that is going down (cli runHub)
                // a sandboxed app (cli/sandbox.cpp) sees the instance's logs read-only: apklayerd runs the hub, with this app's output
                ProcessBuilder.Redirect log = "1".equals(System.getenv("APKLAYER_SANDBOX")) ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.appendTo(logs.resolve("hub.log").toFile());
                Process launcher = new ProcessBuilder("setsid", System.getProperty("apklayer.cli"), "hub").redirectErrorStream(true).redirectOutput(log).start();
                System.out.println("[apklayer] starting hub launcherPid=" + launcher.pid());
                // Like waiting for init's service property: block until apklayer.hub.ready=1. A launcher that fails (the hub died) ends the wait.
                Process ready = new ProcessBuilder(System.getProperty("apklayer.cli"), "wait", "hub").inheritIO().start();
                launcher.onExit().thenAccept(p -> { if (p.exitValue() != 0) ready.destroy(); });
                if (ready.waitFor() != 0) throw new IllegalStateException("Hub did not start; inspect " + logs.resolve("hub.log"));
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while starting the instance hub", interrupted); }
            catch (IOException failure) { throw new IllegalStateException("Cannot start instance hub", failure); }
            // ready: the lookup above now finds it (the property is cleared before a gone hub's lock is released, so it never names a dead one)
        }
    }

    /** Blocks until the hub process is gone: one that refused a registration because it is ending (HubProtocol.EXITING). */
    static void awaitDeath(IBinder hub) {
        CountDownLatch gone = new CountDownLatch(1);
        try { hub.linkToDeath(gone::countDown, 0); }
        catch (android.os.RemoteException dead) { return; }
        try { gone.await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting for the instance hub", interrupted); }
    }
}
