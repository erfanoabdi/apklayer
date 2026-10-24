// android.system.StructMsghdr -- C's struct msghdr, the argument of
// Os.sendmsg / Os.recvmsg (API 33).
//   AOSP: libcore/luni/src/main/java/android/system/StructMsghdr.java
//         (android14-release), mirrored member for member (msg_iov is the one
//         final field).
package android.system;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * Corresponds to C's {@code struct msghdr}.
 */
public final class StructMsghdr {
    /** Optional address. Sendmsg: target address; recvmsg: source address. */
    public SocketAddress msg_name;

    /** Scatter/gather array: the buffers to send from or receive into. */
    public final ByteBuffer[] msg_iov;

    /** Ancillary data. */
    public StructCmsghdr[] msg_control;

    /** Flags on received message. */
    public int msg_flags;

    public StructMsghdr(SocketAddress msg_name, ByteBuffer[] msg_iov,
                        StructCmsghdr[] msg_control, int msg_flags) {
        this.msg_name = msg_name;
        this.msg_iov = msg_iov;
        this.msg_control = msg_control;
        this.msg_flags = msg_flags;
    }
}
