package hello.binderclient;

import android.app.Activity;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.widget.TextView;

/** Finds the server's binder through the service manager, calls it (with a callback binder of its own), and watches it die. */
public class Main extends Activity {
    static final String DESCRIPTOR = "hello.IEcho", CALLBACK = "hello.ICallback";

    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("binder client");
        setContentView(view);
        new Thread(() -> {
            try {
                run();
            } catch (Throwable e) {
                e.printStackTrace(System.out);
                System.out.println("[hello-binder-client] FAIL " + e);
            }
        }, "client").start();
    }

    private void run() throws Exception {
        Class<?> real = Class.forName("apklayer.host.RealBinder");
        IBinder echo = (IBinder) real.getMethod("lookup", String.class).invoke(null, "hello.echo");
        require(echo != null, "service hello.echo not found");
        System.out.println("[hello-binder-client] found " + echo.getClass().getName() + " ping=" + echo.pingBinder() + " alive=" + echo.isBinderAlive());
        require(DESCRIPTOR.equals(echo.getInterfaceDescriptor()), "remote interface descriptor");
        require(echo.pingBinder() && echo.isBinderAlive(), "server alive");
        require(echo.queryLocalInterface(DESCRIPTOR) == null, "the binder is a remote proxy");

        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        d.writeInterfaceToken(DESCRIPTOR);
        d.writeString("ping");
        require(echo.transact(1, d, r, 0), "transact echo");
        r.readException();
        String reply = r.readString();
        require("echo:ping".equals(reply), "echo reply " + reply);
        d.recycle(); r.recycle();

        final int[] callerPid = {-1};
        Binder callback = new Binder() {
            { attachInterface(null, CALLBACK); }
            @Override protected boolean onTransact(int code, Parcel data, Parcel out, int flags) throws android.os.RemoteException {
                if (code < IBinder.FIRST_CALL_TRANSACTION || code > IBinder.LAST_CALL_TRANSACTION) return super.onTransact(code, data, out, flags);
                data.enforceInterface(CALLBACK);
                callerPid[0] = Binder.getCallingPid();
                out.writeNoException();
                out.writeString("cb:" + data.readString());
                return true;
            }
        };
        d = Parcel.obtain(); r = Parcel.obtain();
        d.writeInterfaceToken(DESCRIPTOR);
        d.writeStrongBinder(callback);
        d.writeString("hi");
        require(echo.transact(2, d, r, 0), "transact call");
        r.readException();
        String called = r.readString();
        d.recycle(); r.recycle();

        d = Parcel.obtain(); r = Parcel.obtain();
        d.writeInterfaceToken(DESCRIPTOR);
        require(echo.transact(3, d, r, 0), "transact pid");
        r.readException();
        int serverPid = r.readInt(), seenByServer = r.readInt();
        d.recycle(); r.recycle();

        System.out.println("[hello-binder-client] reply=" + reply + " called=\"" + called + "\" serverPid=" + serverPid + " myPid=" + Process.myPid()
                + " serverSawCaller=" + seenByServer + " callbackCaller=" + callerPid[0]);
        require(serverPid != Process.myPid(), "the server is another process");
        require(seenByServer == Process.myPid(), "the server sees this process as the caller");
        require(callerPid[0] == serverPid, "the callback was called by the server's process");
        require(called.startsWith("server got cb:hi from pid " + Process.myPid()), "callback round trip: " + called);

        java.util.concurrent.CountDownLatch death = new java.util.concurrent.CountDownLatch(1);
        echo.linkToDeath(() -> { System.out.println("[hello-binder-client] binderDied"); death.countDown(); }, 0);
        System.out.println("[hello-binder-client] PASS echo, callback, identities; waiting for the server to die");
        require(death.await(30, java.util.concurrent.TimeUnit.SECONDS), "real death notification");
        runOnUiThread(this::finish);
    }
}
