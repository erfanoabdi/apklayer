package apklayer.host;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;

/** App-side connection to the instance directory, using actual Binder/Parcel objects. */
public final class HubClient {
    private static HubClient current;
    private final IBinder hub;
    private final HubEndpoint endpoint;
    private volatile boolean alive = true;
    private HubClient(Context context) {
        if (!Boolean.getBoolean("apklayer.realbinder")) throw new IllegalStateException("Hub requires real Binder mode");
        hub = HubStartup.connect();
        int hubPid = HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {}, reply -> reply.readInt());
        endpoint = new HubEndpoint(context, hubPid);
        try { hub.linkToDeath(() -> alive = false, 0); }
        catch (android.os.RemoteException failure) { throw new IllegalStateException("Hub disconnected during registration", failure); }
        int pid = HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.REGISTER, request -> {
            request.writeString(context.getPackageName()); request.writeStrongBinder(endpoint);
            request.writeStrongBinder(((android.app.IApplicationThread) android.app.ActivityThread.currentActivityThread().getApplicationThread()).asBinder());
        }, reply -> reply.readInt());
        if (pid != android.os.Process.myPid()) throw new IllegalStateException("Hub caller identity mismatch");
        System.out.println("[apklayer] hub registered " + context.getPackageName() + " pid=" + pid);
    }
    static synchronized HubClient attach(Context context) {
        if (current == null) current = new HubClient(context.getApplicationContext());
        return current;
    }
    static boolean connected() { return current != null; }
    static HubClient current() {
        if (current == null) throw new IllegalStateException("App has no hub connection");
        return current;
    }
    boolean alive() { return alive; }
    IBinder binder() { return hub; }
    public static int[] status() {
        if (current == null) throw new IllegalStateException("App is not registered with its hub");
        return HubProtocol.call(current.hub, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {},
                reply -> new int[]{reply.readInt(), reply.readInt()});
    }
}
