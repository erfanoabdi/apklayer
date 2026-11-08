package hello.system;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.Os;
import java.io.FileDescriptor;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Os.accept fills in the peer's address as libcore does: a bound LocalSocket's abstract name in the UnixSocketAddress the caller passes. */
final class AcceptCheck implements Check {
    @Override public String run(Context context) throws Exception {
        String name = "apklayer-accept-" + android.os.Process.myPid(), own = "apklayer-peer-" + android.os.Process.myPid();
        try (LocalServerSocket server = new LocalServerSocket(name); LocalSocket client = new LocalSocket()) {
            client.bind(new LocalSocketAddress(own));
            client.connect(new LocalSocketAddress(name));
            Class<?> unix = Class.forName("android.system.UnixSocketAddress");
            SocketAddress peer = (SocketAddress) unix.getMethod("createUnnamed").invoke(null);
            // Os.accept(FileDescriptor, SocketAddress) is the hidden overload LocalSocketImpl uses (the SDK shows the InetSocketAddress one)
            FileDescriptor accepted = (FileDescriptor) Os.class.getMethod("accept", FileDescriptor.class, SocketAddress.class).invoke(null, server.getFileDescriptor(), peer);
            java.lang.reflect.Field path = unix.getDeclaredField("sun_path");
            path.setAccessible(true);
            byte[] got = (byte[]) path.get(peer), want = ("\0" + own).getBytes(StandardCharsets.US_ASCII);
            Os.close(accepted);
            Check.require(Arrays.equals(got, want), "peer sun_path " + Arrays.toString(got));
            return "peer \\0" + own;
        }
    }
}
