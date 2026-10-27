package apklayer.host;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Instance-owned process directory. Identities come from the kernel Binder caller. */
final class HubDirectory extends Binder {
    record Peer(int pid, int uid, String name, IBinder endpoint) {}
    private final HubPackages packages;
    private final Map<Integer, Peer> peers = new HashMap<>();
    HubDirectory(Path apps) { packages = new HubPackages(apps); attachInterface(null, HubProtocol.DESCRIPTOR); }
    @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
        if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) return super.onTransact(code, request, reply, flags);
        request.enforceInterface(HubProtocol.DESCRIPTOR);
        switch (code) {
            case HubProtocol.REGISTER -> {
                String name = request.readString(); IBinder endpoint = request.readStrongBinder(); request.enforceNoDataAvail();
                if (packages.get(name) == null || endpoint == null || !HubProtocol.ENDPOINT.equals(endpoint.getInterfaceDescriptor()))
                    throw new SecurityException("Unknown app endpoint");
                int pid = Binder.getCallingPid(), uid = Binder.getCallingUid();
                Peer peer = new Peer(pid, uid, name, endpoint);
                synchronized (peers) {
                    Peer old = peers.get(pid);
                    if (old != null) {
                        if (!old.name.equals(name) || !old.endpoint.equals(endpoint)) throw new SecurityException("Process already registered");
                        reply.writeNoException(); reply.writeInt(pid); return true;
                    }
                    endpoint.linkToDeath(() -> remove(peer), 0);
                    peers.put(pid, peer);
                }
                System.out.println("[apklayer-hub] registered " + name + " pid=" + pid + " uid=" + uid);
                reply.writeNoException(); reply.writeInt(pid); return true;
            }
            case HubProtocol.STATUS -> {
                request.enforceNoDataAvail(); reply.writeNoException(); reply.writeInt(android.os.Process.myPid());
                synchronized (peers) { reply.writeInt(peers.size()); }
                return true;
            }
            default -> { return super.onTransact(code, request, reply, flags); }
        }
    }
    private void remove(Peer peer) {
        synchronized (peers) { peers.remove(peer.pid, peer); }
        System.out.println("[apklayer-hub] died " + peer.name + " pid=" + peer.pid);
    }
}
