// android.net.LocalServerSocket
//
// A listening Unix-domain socket. Backed by a JDK ServerSocketChannel with
// StandardProtocolFamily.UNIX for the FILESYSTEM namespace (real local IPC
// where the platform allows it). The String ctor uses Android's ABSTRACT
// namespace by default, which macOS/JDK cannot bind, so that path throws
// IOException -- the honest "unsupported namespace" result. Mirrors
// frameworks/base/core/java/android/net/LocalServerSocket.java.
package android.net;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

public class LocalServerSocket implements java.io.Closeable {

    private final ServerSocketChannel mChannel;
    private final LocalSocketAddress mLocalAddress;

    /** AOSP binds in the ABSTRACT namespace; the JDK cannot, so we surface the
     *  documented failure rather than silently mis-binding to a file. */
    public LocalServerSocket(String name) throws IOException {
        throw new IOException("ABSTRACT namespace local sockets are not supported");
    }

    public LocalServerSocket(java.io.FileDescriptor fd) throws IOException {
        throw new IOException("fd-based local server socket not supported");
    }

    /** Mac.apk extension entry point: bind a FILESYSTEM-namespace path with a
     *  real JDK Unix-domain server channel. */
    public LocalServerSocket(LocalSocketAddress address) throws IOException {
        if (address.getNamespace() != LocalSocketAddress.Namespace.FILESYSTEM) {
            throw new IOException("unsupported local socket namespace: "
                    + address.getNamespace());
        }
        mChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        mChannel.bind(UnixDomainSocketAddress.of(Path.of(address.getName())));
        mLocalAddress = address;
    }

    public LocalSocket accept() throws IOException {
        SocketChannel ch = mChannel.accept();
        return new LocalSocket(ch);
    }

    public LocalSocketAddress getLocalSocketAddress() { return mLocalAddress; }

    public java.io.FileDescriptor getFileDescriptor() { return null; }

    public void close() throws IOException {
        if (mChannel != null) mChannel.close();
    }
}
