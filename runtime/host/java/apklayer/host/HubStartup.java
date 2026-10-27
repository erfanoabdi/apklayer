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
        Path logs = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", "/tmp/apklayer-stack"));
        try {
            Files.createDirectories(logs);
            Process launcher = new ProcessBuilder("setsid", System.getProperty("apklayer.cli"), "hub")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logs.resolve("hub.log").toFile())).start();
            System.out.println("[apklayer] starting hub launcherPid=" + launcher.pid());
            hub = RealBinder.await(HubProtocol.SERVICE, 3000);
            if (hub == null) throw new IllegalStateException("Hub did not publish within its startup deadline; inspect " + logs.resolve("hub.log"));
            return hub;
        } catch (IOException failure) { throw new IllegalStateException("Cannot start instance hub", failure); }
    }
}
