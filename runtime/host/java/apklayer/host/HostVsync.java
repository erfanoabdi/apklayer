package apklayer.host;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The display's real vsync for the native runtime's Choreographer (PLAN P7). The shell owns the Wayland connection; this
 * relays between it and libnativedisplay (bridge/ndk/libnativedisplay.c), which connects to {@code
 * $XDG_RUNTIME_DIR/apklayer/vsync-<pid>.sock}. Native to Java: a 4-byte request per frame wanted. Java to native: 8-byte
 * records {u32 kind, u32 value}, kind 1 = the output's refresh rate in mHz, 2 = a frame callback of the compositor (ms).
 */
final class HostVsync {
    private final ShellClient shell;
    private volatile SocketChannel peer;
    private volatile int refreshMhz;

    private HostVsync(ShellClient shell) {
        this.shell = shell;
    }

    static HostVsync start(ShellClient shell) {
        String rt = System.getenv("XDG_RUNTIME_DIR");
        if (rt == null) return null;
        Path path = Path.of(rt, "apklayer", "vsync-" + ProcessHandle.current().pid() + ".sock");
        try {
            Files.deleteIfExists(path);
            ServerSocketChannel server = ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(path));
            path.toFile().deleteOnExit();
            HostVsync v = new HostVsync(shell);
            Thread t = new Thread(() -> v.serve(server), "apklayer-vsync");
            t.setDaemon(true);
            t.start();
            return v;
        } catch (IOException e) {
            System.out.println("[apklayer] vsync socket unavailable: " + e);
            return null;
        }
    }

    private void serve(ServerSocketChannel server) {
        try {
            while (true) {
                SocketChannel ch = server.accept();
                peer = ch;
                if (refreshMhz != 0) send(1, refreshMhz);
                ByteBuffer req = ByteBuffer.allocate(4);
                while (ch.read(req) >= 0) {
                    if (!req.hasRemaining()) {
                        shell.sendVsyncRequest();
                        req.clear();
                    }
                }
            }
        } catch (IOException e) {
            // the app process is going away
        }
    }

    void display(int mhz) {
        refreshMhz = mhz;
        send(1, mhz);
    }

    void vsync(int ms) {
        send(2, ms);
    }

    private void send(int kind, int value) {
        SocketChannel ch = peer;
        if (ch == null) return;
        try {
            ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putInt(kind).putInt(value).flip();
            synchronized (this) {
                while (b.hasRemaining()) ch.write(b);
            }
        } catch (IOException e) {
            peer = null;
        }
    }
}
