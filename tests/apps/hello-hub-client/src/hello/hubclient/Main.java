package hello.hubclient;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.widget.TextView;
import hello.shared.IEcho;
import hello.shared.IReply;
import java.util.Arrays;

/** Uses ordinary Context.bindService and generated AIDL; the runtime launches the other APK. */
public class Main extends Activity {
    private int firstPid, connections, reconnectPid;
    private volatile boolean disconnected, death;
    private TextView status;
    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }
    private Intent service(String suffix) {
        Intent intent = new Intent().setComponent(new ComponentName("hello.hubserver", "hello.hubserver.EchoService" + suffix));
        if (suffix.isEmpty()) { Bundle extras = new Bundle(); extras.putBinder("lifecycle", lifecycle.asBinder()); intent.putExtras(extras); }
        return intent;
    }
    private final IReply lifecycle = new IReply.Stub() {
        public void onReply(String value, int server) {
            runOnUiThread(() -> {
                try {
                    require(connections == 2 && server == reconnectPid && value.equals("destroy:hello.hubserver.EchoService"), "server lifecycle after unbind");
                    System.out.println("[hello-hub-client] BIND PASS reconnectPid=" + server); finish();
                } catch (Throwable error) { fail(error); }
            });
        }
    };
    private void fail(Throwable error) { error.printStackTrace(System.out); System.out.println("[hello-hub-client] FAIL " + error); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); status = new TextView(this); status.setText("Binding declared service through instance hub…"); setContentView(status);
        try {
            for (String suffix : new String[]{"$Private", "$Denied"}) {
                try { bindService(service(suffix), new NoConnection(), BIND_AUTO_CREATE); throw new AssertionError("restricted service accepted " + suffix); }
                catch (SecurityException expected) { System.out.println("[hello-hub-client] denied " + suffix); }
            }
            require(!bindService(new Intent().setComponent(new ComponentName("hello.hubserver", "hello.hubserver.Missing")), new NoConnection(), BIND_AUTO_CREATE), "missing service rejected");
            require(bindService(service("$Empty"), new NoConnection() {
                @Override public void onNullBinding(ComponentName name) {
                    unbindService(this); System.out.println("[hello-hub-client] null binding");
                    try {
                        for (int n = 0; n < 20; n++) {
                            ServiceConnection canceled = new NoConnection() {
                                @Override public void onServiceConnected(ComponentName name, IBinder binder) { fail(new AssertionError("callback after unbind")); }
                            };
                            Intent immediate = service("").replaceExtras((Bundle) null);
                            require(bindService(immediate, canceled, BIND_AUTO_CREATE), "accepted immediate bind"); unbindService(canceled);
                        }
                        System.out.println("[hello-hub-client] canceled 20 pending bindings"); bindAllowed();
                    } catch (Throwable error) { fail(error); }
                }
            }, BIND_AUTO_CREATE), "bind null-returning service");
        } catch (Throwable error) { fail(error); }
    }
    private void bindAllowed() {
        try {
            require(bindService(service("$Allowed"), new NoConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                    try {
                        require("echo:permission".equals(IEcho.Stub.asInterface(binder).echo("permission")), "normal permission granted");
                        unbindService(this); System.out.println("[hello-hub-client] allowed permission");
                        require(bindService(service(""), connection, BIND_AUTO_CREATE), "bind exported service");
                    } catch (Throwable error) { fail(error); }
                }
            }, BIND_AUTO_CREATE), "bind permission-protected service");
        } catch (Throwable error) { fail(error); }
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                require(Looper.myLooper() == Looper.getMainLooper(), "framework dispatcher main thread");
                IEcho echo = IEcho.Stub.asInterface(binder);
                require(binder.queryLocalInterface("hello.shared.IEcho") == null, "actual remote AIDL proxy");
                int pid = echo.pid(); require(pid != Process.myPid() && echo.callerPid() == Process.myPid(), "native caller identity");
                require("echo:تست-😀".equals(echo.echo("تست-😀")), "generated AIDL Unicode");
                Bundle input = new Bundle(); input.putString("text", "hello-😀"); input.putIntArray("array", new int[]{3, -4, 5});
                Bundle output = echo.inspect(input);
                require("hello-😀".equals(output.getString("text")) && Arrays.equals(input.getIntArray("array"), output.getIntArray("array"))
                        && output.getInt("caller") == Process.myPid() && output.getInt("server") == pid, "native Bundle AIDL payload");
                java.util.concurrent.CountDownLatch callbackDone = new java.util.concurrent.CountDownLatch(1);
                echo.callback(new IReply.Stub() {
                    @Override public void onReply(String value, int caller) {
                        try { require("callback-😀".equals(value) && caller == Process.myPid() && Binder.getCallingPid() == 0, "oneway native callback value=" + value + " caller=" + caller + " callingPid=" + Binder.getCallingPid() + " callingUid=" + Binder.getCallingUid() + " myUid=" + Process.myUid() + " myPid=" + Process.myPid());
                            System.out.println("[hello-hub-client] callback pid=" + Binder.getCallingPid()); }
                        catch (Throwable error) { fail(error); }
                        finally { callbackDone.countDown(); }
                    }
                });
                require(callbackDone.await(2, java.util.concurrent.TimeUnit.SECONDS), "oneway callback completion");
                connections++;
                if (connections == 1) {
                    firstPid = pid;
                    binder.linkToDeath(() -> { death = true; System.out.println("[hello-hub-client] binderDied"); }, 0);
                    System.out.println("[hello-hub-client] READY serverPid=" + pid);
                } else {
                    require(pid != firstPid && disconnected && death, "native death and framework reconnect");
                    reconnectPid = pid;
                    unbindService(this); status.setText("PASS: Context.bindService + generated AIDL\npermissions, callbacks, death and reconnect");
                    System.out.println("[hello-hub-client] UNBOUND reconnectPid=" + pid);
                }
            } catch (Throwable error) { fail(error); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected = true; System.out.println("[hello-hub-client] disconnected"); }
        @Override public void onBindingDied(ComponentName name) { fail(new AssertionError("binding unexpectedly died")); }
        @Override public void onNullBinding(ComponentName name) { fail(new AssertionError("public echo binder is null")); }
    };
    private static class NoConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder binder) {}
        public void onServiceDisconnected(ComponentName name) {}
    }
}
