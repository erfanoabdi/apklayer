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
    /** REGISTER's refusal by a hub that is ending (HubStartup then waits for the next one). */
    static final String EXITING = "Instance hub is exiting";
    static final int REGISTER = 1, STATUS = 2, BIND = 3, UNBIND = 4, START = 5, STOP = 6;
    static final int REGISTER_RECEIVER = 7, UNREGISTER_RECEIVER = 8, BROADCAST = 9, FINISH_RECEIVER = 10, UNBROADCAST = 11,
            LIST_PROVIDERS = 12, GET_PROVIDER = 13, CHECK_PERMISSION = 14, NOTIFY_CHANGE = 15, GRANT_URI = 16, CHECK_URI = 17, START_ACTIVITY = 18, TAKE_LAUNCH = 19, START_SERVICE = 20, STOP_SERVICE = 21, SERVICE_STOPPED = 22, PI_GET = 23, PI_SEND = 24, PI_CANCEL = 25, PI_INFO = 26, NOTIFY_POST = 27, NOTIFY_CANCEL = 28, UID_OF_PID = 29, LIST_PACKAGES = 30, GET_PACKAGE = 31, INPUT_METHODS = 32,
            INSTALL_COMMIT = 33, INSTALL_PREAPPROVE = 34, UNINSTALL = 35, INSTALL_SOURCE = 36,  // the package installer (HubInstaller)
            RELAUNCH = 37,
            INSTALL_SESSION = 38,  // the PackageInstaller sessions (HubInstallSessions): {op, arguments}
            RESOLVE_ACTIVITY = 39,
            START_INPUT = 40, FINISH_INPUT = 41,
            HOST_SETTINGS = 42,  // {} -> the instance's host settings Bundle (HubHostSettings), pushed again on a change (ENDPOINT_CONFIGURATION)
            SETTINGS_CHANGED = 43,  // `apklayer settings set|unset` (the CLI, through servicetool call): {} -> the hub rereads the instance's overrides  // the input method binding (HubInputMethods): {IRemoteInputConnection, EditorInfo} / {}  // {intent} -> 1 when an installed app (or the installer's confirmation) handles it: startActivity's ActivityNotFoundException
            BLUETOOTH = 44,  // {address, uuid} (both null: a session) -> two descriptors to a BlueZ helper (HubBluetooth)
            USB_PERMISSION = 45,  // {device name, ask} -> whether the caller may open the USB device (HubUsb); ask: the person decides now
            GNSS = 46,  // {follow} -> true: a pipe of the gps fixes from the hub's one GNSS HAL client (HubGnss); false: the caller's pipes end (null)
            PI_SEND_FOR_RESULT = 47,  // {sender, fill-in, ResultReceiver} -> status: Activity.startIntentSenderForResult (an activity PendingIntent answers the receiver)
            ALARM_SET = 48,  // {operation (a hub PendingIntent), delay ms, interval ms}: HubScheduler fires it even when no app process runs
            ALARM_CANCEL = 49,  // {operation}
            JOB_SCHEDULE = 50,  // {JobInfo, delay ms}: HubScheduler has the app run the job (ENDPOINT_JOB) when due
            JOB_CANCEL = 51,  // {job id}
            PERMISSIONS_REQUEST = 52,  // {names} -> per name HostRuntimePermissions' GRANTED, DENIED_ONCE or DENIED: Activity.requestPermissions (HubPermissionPrompt asks)
            ROLE_REQUEST = 53,  // {role} -> whether the person makes the caller the role's holder (HubPermissionPrompt)
            AUDIO_MODE = 54,  // {mode, MODE_CURRENT to read} -> the instance's audio mode (HubCallAudio)
            AUDIO_COMMUNICATION_DEVICE = 55;  // {set, AudioDeviceAttributes or null} -> the communication device in force (HubCallAudio)
            // RELAUNCH: `apklayer run` of a running app (the CLI, through servicetool call): {package, intent file or ""} -> 0 delivered, 1 not running
    static final int ENDPOINT_BIND = 1, ENDPOINT_UNBIND = 2, ENDPOINT_START = 3, ENDPOINT_STOP = 4, ENDPOINT_GET_PROVIDER = 5, ENDPOINT_NOTIFY = 6, ENDPOINT_START_ACTIVITY = 7, ENDPOINT_EXIT_IF_IDLE = 8, ENDPOINT_PACKAGE_CHANGED = 9, ENDPOINT_CONFIGURATION = 10, ENDPOINT_JOB = 11;
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
