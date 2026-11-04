package apklayer.host;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;
import com.android.internal.inputmethod.IInputMethod;
import com.android.internal.inputmethod.IRemoteInputConnection;
import org.robolectric.RuntimeEnvironment;

/**
 * The instance's input method binding, what InputMethodManagerService and its InputMethodBindingController are: when an app's text field takes focus
 * (HostInputMethodClient), the hub binds the user's default input method (Settings.Secure.DEFAULT_INPUT_METHOD, an enabled IME of an installed package,
 * HubBootSettings), initializes it with a window token, binds the client's input and starts input on the field's IRemoteInputConnection, then asks it
 * to show. The IME talks to the field directly through that connection (commitText ...), as on a device. A field that loses focus unbinds the input.
 */
final class HubInputMethods {
    private final HubServiceBindings bindings;
    private final HubPackages packages;
    private final IBinder windowToken = new Binder();  // the IME window's token (InputMethodBindingController.mCurToken)
    // InputMethodPrivilegedOperationsImpl's place: what the IME asks the system for itself (window status, switching IMEs, hiding itself) is accepted
    // and not acted on yet (the desktop has no IME switcher or status bar icon). shortcut: every call answered empty, upgrade when an IME needs one
    private final IBinder privileged = new Binder() {
        @Override protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) {
            if (reply != null) reply.writeNoException();
            return true;
        }
    };
    private ComponentName bound;
    private IServiceConnection.Stub connection;
    private IInputMethod method;
    private HubDirectory.Peer client;
    private Runnable whenConnected;  // the start that waits for the IME's binder

    HubInputMethods(HubServiceBindings bindings, HubPackages packages) { this.bindings = bindings; this.packages = packages; }

    private static final HubDirectory.Peer SYSTEM = new HubDirectory.Peer(-1, 1000, "android", null, null, true, 1000, null);

    /** The user's default input method, or the first enabled one; null without one. */
    private ComponentName chosen() {
        String enabled = HubBootSettings.inputMethods(packages);
        String id = null;
        try { id = Settings.Secure.getString(RuntimeEnvironment.getApplication().getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD); }
        catch (RuntimeException unreadable) { /* the settings provider is not up: the enabled list decides */ }
        if (id == null || id.isEmpty() || !java.util.List.of(enabled.split(":")).contains(id)) id = enabled.isEmpty() ? null : enabled.split(":")[0];
        return id == null ? null : ComponentName.unflattenFromString(id);
    }

    /** A field of the caller took focus: its connection and EditorInfo (IMMS.startInputOrWindowGainedFocus). */
    synchronized void startInput(HubDirectory.Peer caller, IBinder remote, EditorInfo editor) {
        ComponentName ime = chosen();
        if (ime == null) return;  // no Android IME: the desktop's input method serves the field (HostTextBridge)
        if (!ime.equals(bound)) bind(ime);
        client = caller;
        IRemoteInputConnection input = IRemoteInputConnection.Stub.asInterface(remote);
        Runnable start = () -> {
            try {
                method.bindInput(new InputBinding(null, remote, caller.appUid(), caller.pid()));
                IInputMethod.StartInputParams params = new IInputMethod.StartInputParams();
                params.startInputToken = new Binder();
                params.remoteInputConnection = input;
                params.editorInfo = editor;
                method.startInput(params);
                method.showSoftInput(null);
                System.out.println("[apklayer-hub] input method " + bound.flattenToShortString() + " started input for " + caller.name() + " (" + editor.packageName + ")");
            } catch (RemoteException | RuntimeException failure) { System.out.println("[apklayer-hub] input method " + bound + ": " + failure); }
        };
        if (method != null) start.run(); else whenConnected = start;
    }

    /** The caller's field lost focus, or the caller is gone. */
    synchronized void finishInput(HubDirectory.Peer caller) {
        if (client == null || client.pid() != caller.pid()) return;
        client = null;
        whenConnected = null;
        if (method == null) return;
        try { method.hideSoftInput(null); method.unbindInput(); }
        catch (RemoteException gone) { /* the IME's process is gone: the next start binds it again */ }
        System.out.println("[apklayer-hub] input method unbound from " + caller.name());
    }

    private void bind(ComponentName ime) {
        if (connection != null) bindings.unbind(SYSTEM, connection.asBinder());
        bound = ime;
        method = null;
        connection = new IServiceConnection.Stub() {
            @Override public void connected(ComponentName name, IBinder service, android.app.IBinderSession session, boolean dead) {
                synchronized (HubInputMethods.this) {
                    if (connection != this) return;
                    if (service == null) { method = null; return; }  // the IME's process died: it is bound again when it comes back
                    method = IInputMethod.Stub.asInterface(service);
                    try {
                        IInputMethod.InitParams init = new IInputMethod.InitParams();
                        init.token = windowToken;
                        init.privilegedOperations = com.android.internal.inputmethod.IInputMethodPrivilegedOperations.Stub.asInterface(privileged);
                        method.initializeInternal(init);
                    } catch (RemoteException failure) { System.out.println("[apklayer-hub] input method " + name + ": " + failure); method = null; return; }
                    System.out.println("[apklayer-hub] input method " + name.flattenToShortString() + " bound");
                    Runnable start = whenConnected;
                    whenConnected = null;
                    if (start != null) start.run();
                }
            }
        };
        Intent intent = new Intent(HubBootSettings.INPUT_METHOD_ACTION).setComponent(ime);
        if (!bindings.bind(SYSTEM, intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_NOT_VISIBLE, null))
            System.out.println("[apklayer-hub] cannot bind the input method " + ime.flattenToShortString());
    }
}
