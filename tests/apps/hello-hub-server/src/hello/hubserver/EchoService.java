package hello.hubserver;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import hello.shared.IEcho;
import hello.shared.IReply;

/** A real declared Service returning SDK-generated AIDL, without any launcher activity. */
public class EchoService extends Service {
    private IReply observer;
    private final IEcho.Stub binder = new IEcho.Stub() {
        public String echo(String text) { return "echo:" + text; }
        public Bundle inspect(Bundle value) {
            Bundle result = new Bundle(value);
            result.putInt("caller", Binder.getCallingPid()); result.putInt("server", Process.myPid());
            return result;
        }
        public int pid() { return Process.myPid(); }
        public int callerPid() { return Binder.getCallingPid(); }
        public void callback(IReply reply) throws RemoteException { reply.onReply("callback-😀", Binder.getCallingPid()); }
    };
    @Override public void onCreate() { super.onCreate(); System.out.println("[hello-hub-server] create " + getClass().getName() + " pid=" + Process.myPid()); }
    @Override public IBinder onBind(Intent intent) {
        if (intent.getExtras() != null) observer = IReply.Stub.asInterface(intent.getExtras().getBinder("lifecycle"));
        System.out.println("[hello-hub-server] bind " + getClass().getName()); return binder;
    }
    @Override public boolean onUnbind(Intent intent) { System.out.println("[hello-hub-server] unbind " + getClass().getName()); return false; }
    @Override public void onDestroy() {
        System.out.println("[hello-hub-server] destroy " + getClass().getName());
        if (observer != null) try { observer.onReply("destroy:" + getClass().getName(), Process.myPid()); }
        catch (RemoteException disconnected) { }
        super.onDestroy();
    }
    public static class Allowed extends EchoService {}
    public static class Denied extends EchoService {}
    public static class Private extends EchoService {}
    public static class Empty extends EchoService { @Override public IBinder onBind(Intent intent) { return null; } }
}
