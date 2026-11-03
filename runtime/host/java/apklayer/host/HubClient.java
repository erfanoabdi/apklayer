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
        IBinder found;
        HubEndpoint own;
        int pid;
        for (;;) {  // a hub that is ending refuses the registration: wait for it to go and attach to the next one
            found = HubStartup.connect();
            own = new HubEndpoint(context, HubProtocol.call(found, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {}, reply -> reply.readInt()));
            try { pid = register(context, found, own); break; }
            catch (IllegalStateException refused) { if (!HubProtocol.EXITING.equals(refused.getMessage())) throw refused; HubStartup.awaitDeath(found); }
        }
        hub = found; endpoint = own; hubPid = own.hubPid();
        try { hub.linkToDeath(() -> alive = false, 0); }
        catch (android.os.RemoteException failure) { throw new IllegalStateException("Hub disconnected during registration", failure); }
        if (pid != android.os.Process.myPid()) throw new IllegalStateException("Hub caller identity mismatch");
        HubPermissionChecker.install();
        registerPackages(context);
        HubProviders_registerRemote(context);
        HostInputMethods.install(context, HubProtocol.call(found, HubProtocol.DESCRIPTOR, HubProtocol.INPUT_METHODS, request -> {}, reply -> reply.readString()));
        System.out.println("[apklayer] hub registered " + context.getPackageName() + " pid=" + pid);
    }
    private static int register(Context context, IBinder hub, HubEndpoint endpoint) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.REGISTER, request -> {
            request.writeString(context.getPackageName()); request.writeStrongBinder(endpoint); request.writeBoolean(Boolean.getBoolean("apklayer.componenthost"));
            request.writeStrongBinder(((android.app.IApplicationThread) android.app.ActivityThread.currentActivityThread().getApplicationThread()).asBinder());
            request.writeInt(android.os.Process.myUid()); request.writeString(System.getProperty("apklayer.isolated"));
        }, reply -> reply.readInt());
    }

    /**
     * The other installed apps as this app's package manager sees them: their package info (components, metadata, permissions) and the intent filters of their
     * activities and services, so getPackageInfo, getInstalledPackages and resolveActivity answer for them as on a device.
     */
    private void registerPackages(Context context) {
        org.robolectric.shadows.ShadowPackageManager packageManager = org.robolectric.Shadows.shadowOf(context.getPackageManager());
        java.util.List<String> names = HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.LIST_PACKAGES, request -> {}, reply -> reply.createStringArrayList());
        int count = 0;
        for (String name : names) {
            if (name.equals(context.getPackageName())) continue;
            try { if (registerPackage(packageManager, name)) count++; }
            catch (RuntimeException failure) { System.out.println("[apklayer] package " + name + " not registered: " + failure); }
        }
        System.out.println("[apklayer] packages registered: " + count);
    }

    /** One other installed app into this app's package manager (also for an app installed after this JVM attached); false when the hub does not know it. */
    boolean registerPackage(org.robolectric.shadows.ShadowPackageManager packageManager, String name) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.GET_PACKAGE, request -> request.writeString(name), reply -> {
            android.content.pm.PackageInfo info = reply.readTypedObject(android.content.pm.PackageInfo.CREATOR);
            if (info == null) return false;
            // the components go in one by one: installPackage keeps an array of them without the intent filter entries that addIntentFilterFor... needs
            android.content.pm.ActivityInfo[] activities = info.activities, receivers = info.receivers;
            android.content.pm.ServiceInfo[] serviceInfos = info.services;
            info.activities = null; info.services = null; info.receivers = null; info.providers = null;
            packageManager.installPackage(info);
            if (activities != null) for (android.content.pm.ActivityInfo a : activities) packageManager.addOrUpdateActivity(a);
            if (serviceInfos != null) for (android.content.pm.ServiceInfo a : serviceInfos) packageManager.addOrUpdateService(a);
            if (receivers != null) for (android.content.pm.ActivityInfo a : receivers) packageManager.addOrUpdateReceiver(a);
            for (boolean services : new boolean[] {false, true}) for (int n = reply.readInt(); n > 0; n--) {
                android.content.ComponentName component = new android.content.ComponentName(name, reply.readString());
                for (android.content.IntentFilter filter : reply.createTypedArrayList(android.content.IntentFilter.CREATOR)) {
                    try { if (services) packageManager.addIntentFilterForService(component, filter); else packageManager.addIntentFilterForActivity(component, filter); }
                    catch (IllegalArgumentException disabled) { /* a disabled component is not in the package info */ }
                }
            }
            return true;
        });
    }

    /**
     * The hub installed, updated or removed another package (HubInstaller): this app's package manager and content resolver follow before the package
     * broadcast arrives, as both read PackageManagerService's state on a phone.
     */
    void packageChanged(Context context, String name, boolean removed) {
        org.robolectric.shadows.ShadowPackageManager packageManager = org.robolectric.Shadows.shadowOf(context.getPackageManager());
        packageManager.removePackage(name);
        for (String authority : remoteAuthorities.getOrDefault(name, java.util.List.of())) org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority, null);
        remoteAuthorities.remove(name);
        if (!removed) { registerPackage(packageManager, name); HubProviders_registerRemote(context, name); }
        System.out.println("[apklayer] package " + name + (removed ? " removed" : " (re)installed"));
    }

    /** The authorities registered for each other package (packageChanged replaces them). */
    private final java.util.Map<String, java.util.List<String>> remoteAuthorities = new java.util.HashMap<>();

    private void HubProviders_registerRemote(Context context) { HubProviders_registerRemote(context, null); }

    /** Providers of the other installed apps (or of the one named), registered under their authorities in this app's ContentResolver. */
    private void HubProviders_registerRemote(Context context, String only) {
        java.util.List<String[]> providers = new java.util.ArrayList<>();
        org.robolectric.shadows.ShadowPackageManager packageManager = org.robolectric.Shadows.shadowOf(context.getPackageManager());
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.LIST_PROVIDERS, request -> {}, reply -> {
            for (int n = reply.readInt(); n > 0; n--) {
                String[] provider = {reply.readString(), reply.readString(), reply.readString(), reply.readString()};
                android.content.pm.ProviderInfo info = reply.readTypedObject(android.content.pm.ProviderInfo.CREATOR);
                java.util.List<android.content.IntentFilter> filters = new java.util.ArrayList<>();
                for (int f = reply.readInt(); f > 0; f--) filters.add(reply.readTypedObject(android.content.IntentFilter.CREATOR));
                if (provider[1].equals(context.getPackageName()) || (only != null && !only.equals(provider[1]))) continue;
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
                remoteAuthorities.computeIfAbsent(provider[1], key -> new java.util.ArrayList<>()).add(authority);
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
    boolean uriGranted(android.net.Uri uri, int mode) { return checkUri(uri, android.os.Process.myPid(), android.os.Process.myUid(), mode) == 0; }
    /** Whether a process (named as for checkPermission) holds a grant on the uri: what a provider asks about its caller (checkUriPermission). */
    int checkUri(android.net.Uri uri, int pid, int uid, int mode) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CHECK_URI, request -> { request.writeTypedObject(uri, 0); request.writeInt(mode); request.writeInt(pid); request.writeInt(uid); },
                reply -> reply.readInt());
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
    /** Whether another app's caller holds the permission: the kernel's pid of a synchronous call names it, the uid its header carried a oneway one (pid 0 or -1). */
    int checkPermission(int pid, int uid, String permission) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CHECK_PERMISSION, request -> { request.writeInt(pid); request.writeInt(uid); request.writeString(permission); }, reply -> reply.readInt());
    }

    /** A committed install session (ShadowHostPackageInstaller) or a legacy install intent ({@code now}): the APKs go to the hub's installer as file descriptors. */
    void commitInstall(int sessionId, String expected, String label, java.util.List<String> names, java.util.List<android.os.ParcelFileDescriptor> files, android.content.IntentSender status, boolean now) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.INSTALL_COMMIT, request -> {
            request.writeInt(sessionId); request.writeString(expected); request.writeString(label); request.writeInt(files.size());
            for (int i = 0; i < files.size(); i++) { request.writeString(names.get(i)); request.writeFileDescriptor(files.get(i).getFileDescriptor()); }
            request.writeStrongBinder(status == null ? null : status.getTarget().asBinder()); request.writeBoolean(now);
        }, reply -> null);
    }
    void preapproveInstall(int sessionId, String pkg, String label, android.content.IntentSender status) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.INSTALL_PREAPPROVE, request -> {
            request.writeInt(sessionId); request.writeString(pkg); request.writeString(label); request.writeStrongBinder(status.getTarget().asBinder());
        }, reply -> null);
    }
    void uninstall(String pkg, android.content.IntentSender status, boolean now) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.UNINSTALL, request -> {
            request.writeString(pkg); request.writeStrongBinder(status == null ? null : status.getTarget().asBinder()); request.writeBoolean(now);
        }, reply -> null);
    }
    /** canRequestPackageInstalls; {@code ask}: let the person decide now (ACTION_MANAGE_UNKNOWN_APP_SOURCES). */
    boolean installSource(boolean ask) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.INSTALL_SOURCE, request -> request.writeBoolean(ask), reply -> reply.readBoolean());
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
