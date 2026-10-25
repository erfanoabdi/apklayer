// android.system.VmSocketAddress -- a virtio-vsock address, C's struct
// sockaddr_vm (API 34).
//   AOSP: libcore/luni/src/main/java/android/system/VmSocketAddress.java
//         (android14-release), mirrored member for member. It is a plain value
//         class; nothing here can open an AF_VSOCK socket (macOS has no
//         virtio-vsock for a process to talk to), so any Os call taking one
//         must fail the way a device without vsock does, not pretend.
package android.system;

import java.net.SocketAddress;

/**
 * A virtio-vsock address {@link VmSocketAddress}, corresponding to
 * {@code struct sockaddr_vm} in bionic/libc/kernel/uapi/linux/vm_sockets.h.
 */
public final class VmSocketAddress extends SocketAddress {
    /** sockaddr_vm::svmPort. */
    private int svmPort;

    /** sockaddr_vm::svmCid. */
    private int svmCid;

    /**
     * Creates a new instance of VmSocketAddress.
     *
     * @param svmPort The svmPort field value.
     * @param svmCid  The svmCid field value.
     */
    public VmSocketAddress(int svmPort, int svmCid) {
        this.svmPort = svmPort;
        this.svmCid = svmCid;
    }

    /** Returns the value of the svmPort field */
    public int getSvmPort() {
        return svmPort;
    }

    /** Returns the value of the svmCid field */
    public int getSvmCid() {
        return svmCid;
    }
}
