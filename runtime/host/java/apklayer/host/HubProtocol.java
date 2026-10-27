package apklayer.host;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.function.Consumer;
import java.util.function.Function;

/** Instance-directory protocol; framework Binder interfaces remain actual Binder objects. */
final class HubProtocol {
    static final String SERVICE = "apklayer.instance", DESCRIPTOR = "apklayer.IInstanceHub";
    static final String ENDPOINT = "apklayer.IAppEndpoint";
    static final int REGISTER = 1, STATUS = 2, BIND = 3, UNBIND = 4, START = 5, STOP = 6;
    static final int REGISTER_RECEIVER = 7, UNREGISTER_RECEIVER = 8, BROADCAST = 9, FINISH_RECEIVER = 10, UNBROADCAST = 11,
            LIST_PROVIDERS = 12, GET_PROVIDER = 13, CHECK_PERMISSION = 14, NOTIFY_CHANGE = 15, GRANT_URI = 16, CHECK_URI = 17, START_ACTIVITY = 18, TAKE_LAUNCH = 19, START_SERVICE = 20, STOP_SERVICE = 21, SERVICE_STOPPED = 22, PI_GET = 23, PI_SEND = 24, PI_CANCEL = 25, PI_INFO = 26;
    static final int ENDPOINT_BIND = 1, ENDPOINT_UNBIND = 2, ENDPOINT_START = 3, ENDPOINT_STOP = 4, ENDPOINT_GET_PROVIDER = 5, ENDPOINT_NOTIFY = 6, ENDPOINT_START_ACTIVITY = 7;
    private HubProtocol() {}
    static <T> T call(IBinder target, String descriptor, int code, Consumer<Parcel> write, Function<Parcel, T> read) {
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(descriptor); write.accept(request);
            if (!target.transact(code, request, reply, 0)) throw new IllegalStateException("Unknown hub transaction " + code);
            reply.readException(); return read.apply(reply);
        } catch (RemoteException failure) {
            throw new IllegalStateException("Instance hub disconnected", failure);
        } finally { request.recycle(); reply.recycle(); }
    }
}
