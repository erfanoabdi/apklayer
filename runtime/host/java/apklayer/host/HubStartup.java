package apklayer.host;

import android.os.IBinder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Start the shared instance daemon on demand and wait for real Binder publication. */
final class HubStartup {
    private HubStartup() {}
    static IBinder connect() {
        IBinder hub = RealBinder.lookup(HubProtocol.SERVICE);
        if (hub != null && hub.isBinderAlive() && hub.pingBinder()) return hub;
        Path logs = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", System.getProperty("user.home") + "/.local/state/apklayer/default"));
        try {
            Files.createDirectories(logs);
            Process launcher = new ProcessBuilder("setsid", System.getProperty("apklayer.cli"), "hub")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logs.resolve("hub.log").toFile())).start();
            System.out.println("[apklayer] starting hub launcherPid=" + launcher.pid());
            // Like waiting for init's service property: block until the hub sets apklayer.hub.ready. A launcher that exits with an error
            // (the hub died) ends the wait; exit 0 means another hub already holds the lock and is on its way up.
            Process ready = new ProcessBuilder(System.getProperty("apklayer.cli"), "wait", "hub").inheritIO().start();
            launcher.onExit().thenAccept(p -> { if (p.exitValue() != 0) ready.destroy(); });
            if (ready.waitFor() != 0) throw new IllegalStateException("Hub did not start; inspect " + logs.resolve("hub.log"));
            hub = RealBinder.lookup(HubProtocol.SERVICE);
            if (hub == null) throw new IllegalStateException("Hub is ready but not published; inspect " + logs.resolve("hub.log"));
            return hub;
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while starting the instance hub", interrupted); }
        catch (IOException failure) { throw new IllegalStateException("Cannot start instance hub", failure); }
    }
}
