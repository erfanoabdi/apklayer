package apklayer.host;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;

/** App-side connection to the instance directory, using actual Binder/Parcel objects. */
public final class HubClient {
    private static HubClient current;
    private final IBinder hub;
    private final HubEndpoint endpoint;
    private volatile boolean alive = true;
    private final int hubPid;
    /** The hub JVM is the system: its pid is how a call from it (a oneway callback, a lifecycle command) is recognised as uid 1000. */
    int hubPid() { return hubPid; }
    private HubClient(Context context) {
        hub = HubStartup.connect();
        hubPid = HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {}, reply -> reply.readInt());
        endpoint = new HubEndpoint(context, hubPid);
        try { hub.linkToDeath(() -> alive = false, 0); }
        catch (android.os.RemoteException failure) { throw new IllegalStateException("Hub disconnected during registration", failure); }
        int pid = HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.REGISTER, request -> {
            request.writeString(context.getPackageName()); request.writeStrongBinder(endpoint); request.writeBoolean(Boolean.getBoolean("apklayer.componenthost"));
            request.writeStrongBinder(((android.app.IApplicationThread) android.app.ActivityThread.currentActivityThread().getApplicationThread()).asBinder());
            request.writeInt(android.os.Process.myUid()); request.writeString(System.getProperty("apklayer.isolated"));
        }, reply -> reply.readInt());
        if (pid != android.os.Process.myPid()) throw new IllegalStateException("Hub caller identity mismatch");
        HubPermissionChecker.install();
        HubProviders_registerRemote(context);
        System.out.println("[apklayer] hub registered " + context.getPackageName() + " pid=" + pid);
    }
    /** Providers of the other installed apps, registered under their authorities in this app's ContentResolver. */
    private void HubProviders_registerRemote(Context context) {
        java.util.List<String[]> providers = new java.util.ArrayList<>();
        org.robolectric.shadows.ShadowPackageManager packageManager = org.robolectric.Shadows.shadowOf(context.getPackageManager());
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.LIST_PROVIDERS, request -> {}, reply -> {
            for (int n = reply.readInt(); n > 0; n--) {
                String[] provider = {reply.readString(), reply.readString(), reply.readString(), reply.readString()};
                android.content.pm.ProviderInfo info = reply.readTypedObject(android.content.pm.ProviderInfo.CREATOR);
                java.util.List<android.content.IntentFilter> filters = new java.util.ArrayList<>();
                for (int f = reply.readInt(); f > 0; f--) filters.add(reply.readTypedObject(android.content.IntentFilter.CREATOR));
                if (provider[1].equals(context.getPackageName())) continue;
                providers.add(provider);
                // the package manager answers for other apps' providers too (DocumentsUI finds document providers by their filter)
                packageManager.addOrUpdateProvider(info);
                for (android.content.IntentFilter filter : filters) packageManager.addIntentFilterForProvider(new android.content.ComponentName(info.packageName, info.name), filter);
            }
            return null;
        });
        int count = 0;
        for (String[] provider : providers) {
            if (provider[1].equals(context.getPackageName())) continue;
            for (String authority : provider[0].split(";")) {
                org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority, new RemoteProvider(context, authority, provider[2], provider[3]));
                count++;
            }
        }
        System.out.println("[apklayer] remote providers registered: " + count);
    }

    /** What the hub says about a process (a caller's pid): whether its package holds the permission. */
    void notifyChange(android.net.Uri uri, int flags) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.NOTIFY_CHANGE, request -> { request.writeTypedObject(uri, 0); request.writeInt(flags); }, reply -> null);
    }
    void grantUri(String target, android.net.Uri uri, int mode, boolean revoke) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.GRANT_URI, request -> { request.writeString(target); request.writeTypedObject(uri, 0); request.writeInt(mode); request.writeBoolean(revoke); }, reply -> null);
    }
    boolean uriGranted(android.net.Uri uri, int mode) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CHECK_URI, request -> { request.writeTypedObject(uri, 0); request.writeInt(mode); }, reply -> reply.readInt()) == 0;
    }
    void postNotification(String pkg, String tag, int id, android.app.Notification notification, int importance) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.NOTIFY_POST, request -> {
            request.writeString(pkg); request.writeString(tag); request.writeInt(id); request.writeTypedObject(notification, 0); request.writeInt(importance);
        }, reply -> null);
    }
    void cancelNotification(String tag, int id, boolean all) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.NOTIFY_CANCEL, request -> { request.writeString(tag); request.writeInt(id); request.writeBoolean(all); }, reply -> null);
    }
    /** The app uid of another app's process (-1 when the hub does not know the pid). */
    int uidOf(int pid) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.UID_OF_PID, request -> request.writeInt(pid), reply -> reply.readInt());
    }
    int checkPermission(int pid, String permission) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CHECK_PERMISSION, request -> { request.writeInt(pid); request.writeString(permission); }, reply -> reply.readInt());
    }

    static synchronized HubClient attach(Context context) {
        if (current == null) current = new HubClient(context.getApplicationContext());
        return current;
    }
    static boolean connected() { return current != null; }
    static HubClient current() {
        if (current == null) throw new IllegalStateException("App has no hub connection");
        return current;
    }
    boolean alive() { return alive; }
    IBinder binder() { return hub; }
    public static int[] status() {
        if (current == null) throw new IllegalStateException("App is not registered with its hub");
        return HubProtocol.call(current.hub, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {},
                reply -> new int[]{reply.readInt(), reply.readInt()});
    }
}
