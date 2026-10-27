package apklayer.host;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;

/** App-side connection to the instance directory, using actual Binder/Parcel objects. */
public final class HubClient {
    private static HubClient current;
    private final IBinder hub;
    private final Binder endpoint;
    private HubClient(Context context) {
        if (!Boolean.getBoolean("apklayer.realbinder")) throw new IllegalStateException("Hub requires real Binder mode");
        hub = RealBinder.lookup(HubProtocol.SERVICE);
        if (hub == null) throw new IllegalStateException("Instance hub is not running");
        endpoint = new Binder(); endpoint.attachInterface(null, HubProtocol.ENDPOINT);
        int pid = HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.REGISTER, request -> {
            request.writeString(context.getPackageName()); request.writeStrongBinder(endpoint);
        }, reply -> reply.readInt());
        if (pid != android.os.Process.myPid()) throw new IllegalStateException("Hub caller identity mismatch");
        System.out.println("[apklayer] hub registered " + context.getPackageName() + " pid=" + pid);
    }
    static synchronized HubClient attach(Context context) {
        if (current == null) current = new HubClient(context.getApplicationContext());
        return current;
    }
    public static int[] status() {
        if (current == null) throw new IllegalStateException("App is not registered with its hub");
        return HubProtocol.call(current.hub, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {},
                reply -> new int[]{reply.readInt(), reply.readInt()});
    }
}
