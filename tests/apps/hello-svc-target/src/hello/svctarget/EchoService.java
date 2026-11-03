package hello.svctarget;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * A second service, so one connection can be bound to two (scenario "filters" of hello-svc-caller). Its binder answers PROBE (scenario "oneway"): the
 * caller's identity and checkCallingPermission of HELD (the caller requests it) and UNHELD (it does not), sent back on the caller's callback binder.
 */
public class EchoService extends Service {
    static final int PROBE = 2;
    static final String DESCRIPTOR = "hello.svctarget.IProbe";

    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code != PROBE) return super.onTransact(code, data, reply, flags);
                Parcel answer = Parcel.obtain();
                try {
                    data.enforceInterface(DESCRIPTOR);  // as an AIDL stub does: the header names the caller's uid as its work source
                    IBinder callback = data.readStrongBinder();
                    answer.writeInt(Binder.getCallingPid());
                    answer.writeInt(Binder.getCallingUid());
                    answer.writeInt(checkCallingPermission("hello.svctarget.permission.HELD"));
                    answer.writeInt(checkCallingPermission("hello.svctarget.permission.UNHELD"));
                    callback.transact(1, answer, null, FLAG_ONEWAY);
                    System.out.println("[svc-t] probe flags=" + flags + " answered");
                } catch (RuntimeException | RemoteException failure) { failure.printStackTrace(System.out); throw failure; }
                finally { answer.recycle(); }
                return true;
            }
        };
    }
}
