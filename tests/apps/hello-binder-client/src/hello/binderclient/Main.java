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

    // isRegionReadOnly is @hide (the framework checks it before handing a region on)
    private static boolean readOnly(android.os.SharedMemory m) throws Exception { return (Boolean) android.os.SharedMemory.class.getMethod("isRegionReadOnly").invoke(m); }

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
        long waitStarted = System.nanoTime();
        require(real.getMethod("await", String.class, long.class).invoke(null, "hello.missing", 30L) == null, "missing service deadline");
        long waited = System.nanoTime() - waitStarted;
        require(waited >= 20000000L && waited < 1000000000L, "real monotonic notification deadline");
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

        // SharedMemory across processes: the server's memfd arrives in the reply, mapped here; the region was sealed read-only by the server
        d = Parcel.obtain(); r = Parcel.obtain();
        d.writeInterfaceToken(DESCRIPTOR);
        d.writeString("shared-memory-from-server");
        require(echo.transact(4, d, r, 0), "transact shm");
        r.readException();
        android.os.SharedMemory shm = android.os.SharedMemory.CREATOR.createFromParcel(r);
        d.recycle(); r.recycle();
        java.nio.ByteBuffer map = shm.mapReadOnly();
        byte[] got = new byte[shm.getSize()];
        map.get(got);
        String shmText = new String(got, java.nio.charset.StandardCharsets.UTF_8);
        require("shared-memory-from-server".equals(shmText), "shared memory content " + shmText);
        boolean sealed = false;
        try { shm.mapReadWrite(); } catch (android.system.ErrnoException expected) { sealed = true; }
        require(sealed, "a region sealed read-only refuses a writable mapping");
        require(readOnly(shm), "a sealed region reports read-only");
        android.os.SharedMemory.unmap(map);
        shm.close();
        // and in this process: two mappings of one region see each other's writes
        android.os.SharedMemory own = android.os.SharedMemory.create("hello-own", 4096);
        java.nio.ByteBuffer one = own.mapReadWrite(), two = own.map(android.system.OsConstants.PROT_READ, 4000, 96);
        one.putInt(4000, 0x5eed);
        require(two.getInt(0) == 0x5eed, "two mappings share memory");
        require(!readOnly(own), "a new region is writable");
        own.close();
        System.out.println("[hello-binder-client] shm=" + shmText + " size=" + got.length);

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
