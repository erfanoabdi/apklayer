package apklayer.host;

import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The GNSS HAL's one client for every app (the HAL keeps one IGnssCallback, as on Android, where only the system's GnssLocationProvider holds it):
 * the HAL's client (`libapklayer_gnss.so --client`) runs here while any app follows the gps provider, and each of its lines (`ready`, then
 * `location LAT LON ACCURACY`) goes to every follower's pipe (HostLocation reads it as from its own helper). A follower is dropped when its
 * process dies or its pipe is closed; the client ends with the last follower.
 */
final class HubGnss {
    private final HubDirectory directory;
    private record Follower(int pid, FileOutputStream out) {}
    private final List<Follower> followers = new ArrayList<>();
    private Process client;
    private boolean ready;

    HubGnss(HubDirectory directory) { this.directory = directory; }

    /** The read end of a pipe carrying the gps fixes for the caller. */
    synchronized ParcelFileDescriptor follow(HubDirectory.Peer caller) throws IOException {
        if (!directory.packages().holds(caller.name(), "android.permission.ACCESS_FINE_LOCATION"))
            throw new SecurityException(caller.name() + " does not hold ACCESS_FINE_LOCATION");
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
        if (client == null) start();
        if (ready) out.write("ready\n".getBytes(StandardCharsets.UTF_8));
        followers.add(new Follower(caller.pid(), out));
        return pipe[0];
    }

    private void start() throws IOException {
        Process started = new ProcessBuilder(HostProcess.dying(System.getProperty("apklayer.cli", "apklayer"), "exec", "libapklayer_gnss.so", "--client"))
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        client = started;
        ready = false;
        BufferedReader in = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> {
            try {
                for (String line; (line = in.readLine()) != null && send(started, line); ) {}
            } catch (IOException ended) { /* the client went away */ }
            end(started);
        }, "hub-gnss");
        reader.setDaemon(true);
        reader.start();
        System.out.println("[apklayer-hub] gnss: client started");
    }

    /** One client line to every follower; false once none is left (the client is ended). */
    private synchronized boolean send(Process from, String line) {
        if (client != from) return false;
        if (line.equals("ready")) ready = true;
        else if (!line.startsWith("location ")) return true;  // btload's and the session's status lines
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        followers.removeIf(f -> {
            try { f.out.write(bytes); return false; } catch (IOException closed) { close(f.out); return true; }
        });
        return !followers.isEmpty();
    }

    /** The client ended (or has no follower left): followers see the end of their pipe, a later follower starts a new client. */
    private synchronized void end(Process from) {
        from.destroy();
        if (client != from) return;
        System.out.println("[apklayer-hub] gnss: client ended (" + followers.size() + " followers)");
        client = null;
        if (followers.isEmpty()) return;
        followers.forEach(f -> close(f.out));
        followers.clear();
    }

    /** An app process died: its pipe goes, and the client with the last one. */
    synchronized void died(HubDirectory.Peer peer) {
        if (!followers.removeIf(f -> f.pid == peer.pid() && close(f.out)) || !followers.isEmpty() || client == null) return;
        end(client);
    }

    private static boolean close(FileOutputStream out) { try { out.close(); } catch (IOException ignored) { } return true; }
}
