// android.system.StructCmsghdr -- C's struct cmsghdr, the ancillary-data
// element of StructMsghdr.msg_control (API 33).
//   AOSP: libcore/luni/src/main/java/android/system/StructCmsghdr.java
//         (android14-release), mirrored member for member. The short
//         constructor stores the value in NATIVE byte order, as AOSP does; on
//         arm64 that is little-endian on both a device and this Mac.
package android.system;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Corresponds to C's {@code struct cmsghdr}.
 */
public final class StructCmsghdr {
    /** Originating protocol */
    public final int cmsg_level;

    /** Protocol-specific type */
    public final int cmsg_type;

    /** message data sent/received */
    public final byte[] cmsg_data;

    public StructCmsghdr(int cmsg_level, int cmsg_type, short value) {
        // Short.Size unit is bits, ByteBuffer data unit is bytes
        ByteBuffer buf = ByteBuffer.allocate(Short.SIZE / 8);
        buf.order(ByteOrder.nativeOrder());
        buf.putShort(value);

        this.cmsg_level = cmsg_level;
        this.cmsg_type = cmsg_type;
        this.cmsg_data = buf.array();
    }

    public StructCmsghdr(int cmsg_level, int cmsg_type, byte[] value) {
        this.cmsg_level = cmsg_level;
        this.cmsg_type = cmsg_type;
        this.cmsg_data = value;
    }
}
