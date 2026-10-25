// The cross-app DOORBELL: an AF_UNIX socket per installed bundle that tells a
// RUNNING target "drain your intent inbox now".
//
// docs/cross-app-launch-and-implicit-start-design_2026-09-26.md section 3. It
// carries NO data -- only the word DRAIN -- so a forged ring can do nothing but
// make the target drain its own inbox, which it also does on every unhide and
// activation. The envelope itself always reaches disk first, so the doorbell is
// an optimisation (warm delivery without waiting for an activation), never the
// transport: every path that can lose the ring has a backstop.
//
// Path: ~/Library/Application Support/Mac.apk/run/door-<first 16 hex of
// SHA-256(canonical bundle path)>.sock. Both sides compute it from the bundle
// path. AF_UNIX paths are limited to 103 bytes incl. NUL on Darwin (104-byte
// sun_path); past 102 characters the doorbell is skipped and the backstops carry
// delivery. The socket is chmod 0600 after bind; the run dir is under ~/Library.
package com.macapk.loader;

import java.io.File;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;

final class CrossAppDoorbell {
    private static final String TAG = "Mac.apk.XAPP";
    static final int MAX_PATH = 102;

    private CrossAppDoorbell() {}

    static File runDir() {
        return new File(System.getProperty("user.home"),
                "Library/Application Support/Mac.apk/run");
    }

    /** The doorbell path for a bundle, or null when it would not fit. */
    static String socketPath(String bundlePath) {
        try {
            String canon = new File(bundlePath).getCanonicalPath();
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(canon.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", d[i] & 0xff));
            String p = new File(runDir(), "door-" + hex + ".sock").getPath();
            return p.length() <= MAX_PATH ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Sender side: ring once. Returns the target's answer ("ACK") or null when
     *  nobody is listening (a cold target; the stub's LaunchServices open and the
     *  Boot drain deliver it). Never blocks for long: a UNIX connect to a
     *  listening socket completes at once, and the reply is written before the
     *  drain runs. */
    static String ring(String bundlePath) {
        String path = socketPath(bundlePath);
        if (path == null || !new File(path).exists()) return null;
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(path));
            ch.write(ByteBuffer.wrap("DRAIN\n".getBytes(StandardCharsets.US_ASCII)));
            ch.shutdownOutput();
            ByteBuffer buf = ByteBuffer.allocate(64);
            long deadline = System.currentTimeMillis() + 200;
            ch.configureBlocking(false);
            while (buf.hasRemaining() && System.currentTimeMillis() < deadline) {
                int n = ch.read(buf);
                if (n < 0) break;
                if (n == 0) Thread.sleep(5);
                else if (new String(buf.array(), 0, buf.position(), StandardCharsets.US_ASCII).contains("\n")) break;
            }
            String ans = new String(buf.array(), 0, buf.position(), StandardCharsets.US_ASCII).trim();
            return ans.isEmpty() ? "no answer" : ans;
        } catch (Throwable t) {
            // Connection refused (a stale file of a dead target) or absent: the
            // envelope is on disk; the cold path delivers it.
            return null;
        }
    }

    /** Receiver side: bind this bundle's doorbell and serve rings on a daemon
     *  thread, calling `onRing` for each. The stub's flock guarantees one live
     *  instance per bundle, so a stale socket file is unlinked, not respected. */
    static boolean serve(String bundlePath, Runnable onRing) {
        String path = socketPath(bundlePath);
        if (path == null) {
            NativeLog.i(TAG, "doorbell skipped (path would exceed " + MAX_PATH
                    + " chars); unhide/activation drains carry delivery");
            return false;
        }
        try {
            File sock = new File(path);
            File dir = sock.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            Files.deleteIfExists(sock.toPath());
            final ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(path));
            try {
                Files.setPosixFilePermissions(sock.toPath(), PosixFilePermissions.fromString("rw-------"));
            } catch (Throwable ignored) {}
            Thread t = new Thread(() -> {
                while (true) {
                    try (SocketChannel c = server.accept()) {
                        ByteBuffer in = ByteBuffer.allocate(16);
                        c.read(in);
                        String cmd = new String(in.array(), 0, in.position(), StandardCharsets.US_ASCII).trim();
                        if ("DRAIN".equals(cmd)) {
                            try { onRing.run(); } catch (Throwable ignored) {}
                            c.write(ByteBuffer.wrap("ACK\n".getBytes(StandardCharsets.US_ASCII)));
                        }
                    } catch (Throwable e) {
                        if (!server.isOpen()) return;
                    }
                }
            }, "macapk-xapp-door");
            t.setDaemon(true);
            t.start();
            NativeLog.i(TAG, "doorbell listening at " + path);
            return true;
        } catch (Throwable t) {
            NativeLog.w(TAG, "doorbell bind failed (" + t + "); unhide/activation drains carry delivery");
            return false;
        }
    }
}
