package apklayer.host;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The native half of the VPN (cli/vpn_guard.cpp, docs/VPN.md): `apklayer vpn-guard` is started with its stdin a pipe once the privileged side made the TUN. It
 * opens the TUN and hands the descriptor over a unix socket here, serves protect() (a socket sent to it is bound to the underlying interface) and ends the
 * VPN by itself when the TUN loses its carrier (the app closed it or died) or this process's end of the pipe closes (the hub died): it runs the teardown
 * command then.
 */
final class HubVpnGuard {
    private final Path dir;
    private final Consumer<String> gone;  // the TUN lost its carrier: the interface name
    private Process process;
    private OutputStream commands;
    private volatile String error;
    private volatile LocalServerSocket server;

    HubVpnGuard(Path dir, Consumer<String> gone) { this.dir = dir; this.gone = gone; }

    private Path protectSocket() { return dir.resolve("protect.sock"); }

    /** The TUN's descriptor (the caller closes it once it has parceled it). {@code teardown}: the command the guard runs when the VPN ends. */
    synchronized FileDescriptor start(String ifname, String underlying, boolean nonblock, List<String> teardown) throws IOException {
        Files.createDirectories(dir);
        Path sock = dir.resolve("hub.sock");
        Files.deleteIfExists(sock);
        LocalSocket listening = new LocalSocket();
        listening.bind(new LocalSocketAddress(sock.toString(), LocalSocketAddress.Namespace.FILESYSTEM));
        LocalServerSocket server = new LocalServerSocket(listening.getFileDescriptor());
        this.server = server;
        error = null;
        List<String> line = new ArrayList<>(List.of(System.getProperty("apklayer.cli"), "vpn-guard", sock.toString(), ifname, underlying, protectSocket().toString(), nonblock ? "1" : "0"));
        if (!teardown.isEmpty()) { line.add("--"); line.addAll(teardown); }
        try {
            process = new ProcessBuilder(line).redirectErrorStream(true).start();
            commands = process.getOutputStream();
            watch(process);
            try (LocalSocket peer = server.accept()) {
                byte[] name = new byte[64];
                int n = peer.getInputStream().read(name);
                FileDescriptor[] fds = peer.getAncillaryFileDescriptors();
                if (n <= 0 || fds == null || fds.length != 1) throw new IOException("the guard sent no TUN");
                peer.getOutputStream().write(1);  // the guard closes its own copy after this
                return fds[0];
            }
        } catch (IOException failure) {
            throw new IOException(error != null ? error : failure.getMessage(), failure);
        } finally {
            this.server = null;
            try { server.close(); listening.close(); } catch (IOException ignored) { /* closed */ }
            Files.deleteIfExists(sock);
        }
    }

    private void watch(Process mine) {
        Thread reader = new Thread(() -> {
            try (BufferedReader out = new BufferedReader(new InputStreamReader(mine.getInputStream()))) {
                for (String line; (line = out.readLine()) != null;) {
                    System.out.println("[apklayer-hub] vpn guard: " + line);
                    if (line.startsWith("error ")) { error = line.substring(6); closeServer(); }
                    else if (line.startsWith("gone ") && process == mine) gone.accept(line.substring(5));
                }
            } catch (IOException ended) { /* the guard ended */ }
            if (error == null) error = "the VPN's guard ended";
            closeServer();
        }, "hub-vpn-guard");
        reader.setDaemon(true);
        reader.start();
    }

    private void closeServer() {
        LocalServerSocket s = server;
        try { if (s != null) s.close(); } catch (IOException ignored) { /* closed */ }
    }

    /** VpnService.protect(): the guard binds the socket to the underlying interface; whether it did. */
    boolean protect(FileDescriptor socket) {
        try (LocalSocket guard = new LocalSocket()) {
            guard.connect(new LocalSocketAddress(protectSocket().toString(), LocalSocketAddress.Namespace.FILESYSTEM));
            guard.setFileDescriptorsForSend(new FileDescriptor[] {socket});
            guard.getOutputStream().write(1);
            return guard.getInputStream().read() == 1;
        } catch (IOException failure) {
            System.out.println("[apklayer-hub] vpn: protect: " + failure);
            return false;
        }
    }

    /** The VPN ends: the guard runs its teardown and exits; the call returns once it has (or after 10 s). */
    synchronized void close() {
        Process mine = process;
        if (mine == null) return;
        process = null;
        try { commands.close(); } catch (IOException ignored) { /* gone */ }
        try { if (!mine.waitFor(10, TimeUnit.SECONDS)) mine.destroyForcibly(); } catch (InterruptedException interrupted) { mine.destroyForcibly(); }
        try { Files.deleteIfExists(protectSocket()); } catch (IOException ignored) { /* none */ }
    }
}
