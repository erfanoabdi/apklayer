package hello.binderserver;

import android.app.Activity;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.widget.TextView;

/** A hand-written "AIDL" service published under a name: echo(String), call(IBinder callback, String) that calls the client back, pid(). */
public class Main extends Activity {
    static final String DESCRIPTOR = "hello.IEcho", CALLBACK = "hello.ICallback";

    static final class Echo extends Binder {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                data.enforceInterface(DESCRIPTOR);
                switch (code) {
                    case 1: { // echo(String): String
                        String s = data.readString();
                        reply.writeNoException();
                        reply.writeString("echo:" + s);
                        return true;
                    }
                    case 2: { // call(IBinder callback, String): String, the callback answers
                        IBinder callback = data.readStrongBinder();
                        String s = data.readString();
                        Parcel d = Parcel.obtain(), r = Parcel.obtain();
                        try {
                            d.writeInterfaceToken(CALLBACK);
                            d.writeString(s);
                            callback.transact(1, d, r, 0);
                            r.readException();
                            reply.writeNoException();
                            reply.writeString("server got " + r.readString() + " from pid " + Binder.getCallingPid());
                        } finally { d.recycle(); r.recycle(); }
                        return true;
                    }
                    case 3: // pid(): int, and who is calling
                        reply.writeNoException();
                        reply.writeInt(Process.myPid());
                        reply.writeInt(Binder.getCallingPid());
                        return true;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
            } catch (android.os.RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("binder server");
        setContentView(view);
        try {
            Class<?> real = Class.forName("apklayer.host.RealBinder");
            boolean ok = (Boolean) real.getMethod("publish", String.class, IBinder.class).invoke(null, "hello.echo", new Echo());
            System.out.println("[hello-binder-server] published=" + ok + " pid=" + Process.myPid());
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            System.out.println("[hello-binder-server] FAIL " + e);
        }
    }
}
