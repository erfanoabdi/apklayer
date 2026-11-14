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
        for (;;) {  // a hub that is ending refuses the registration, or is gone after the lookup found it alive: wait for it to go and attach to the next one
            found = HubStartup.connect();
            try {
                own = new HubEndpoint(context, HubProtocol.call(found, HubProtocol.DESCRIPTOR, HubProtocol.STATUS, request -> {}, reply -> reply.readInt()));
                pid = register(context, found, own);
                break;
            } catch (IllegalStateException refused) {
                if (!HubProtocol.EXITING.equals(refused.getMessage()) && !(refused.getCause() instanceof android.os.DeadObjectException)) throw refused;
                HubStartup.awaitDeath(found);
            }
        }
        hub = found; endpoint = own; hubPid = own.hubPid();
        try { hub.linkToDeath(() -> { alive = false; hubDied(); }, 0); }
        catch (android.os.RemoteException failure) { throw new IllegalStateException("Hub disconnected during registration", failure); }
        if (pid != android.os.Process.myPid()) throw new IllegalStateException("Hub caller identity mismatch");
        HubPermissionChecker.install();
        registerPackages(context);
        HubProviders_registerRemote(context);
        android.os.Bundle settings = HubProtocol.call(found, HubProtocol.DESCRIPTOR, HubProtocol.HOST_SETTINGS, request -> {}, reply -> reply.readBundle(HubClient.class.getClassLoader()));
        // on the main thread later (an early attachment runs inside the application's creation), ahead of the first launch (AppHost.run)
        AppHost.beforeFirstLaunch(() -> AppHost.configurationChanged(HostConfiguration.update(settings)));
        HostInputMethods.install(context,HubProtocol.call(found, HubProtocol.DESCRIPTOR, HubProtocol.INPUT_METHODS, request -> {}, reply -> reply.readString()));
        System.out.println("[apklayer] hub registered " + context.getPackageName() + " pid=" + pid);
    }
    /**
     * The hub is the instance's system_server: what this process registered there (activities, bindings, receivers, alarms, notifications) went with
     * it, so a next hub (the always-on unit's taking over) cannot be rejoined. As an app process when system_server dies, the process ends; a
     * component host ends from its loop (ComponentHost), after its due writes.
     */
    private static void hubDied() {
        if (Boolean.getBoolean("apklayer.componenthost") || HubHost.hub) return;
        System.out.println("[apklayer] the instance hub ended: this app process ends with it");
        System.out.flush();
        System.exit(0);
    }

    private static int register(Context context, IBinder hub, HubEndpoint endpoint) {
        // the first call waits for the hub's boot (1.4 s on an idle machine, several seconds on a busy one): say so every 2 s, the launch is not hung
        long since = System.nanoTime();
        java.util.concurrent.ScheduledExecutorService ticker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hub-wait"); t.setDaemon(true); return t; });
        ticker.scheduleAtFixedRate(() -> System.out.println("[apklayer] waiting for the instance hub (" + (System.nanoTime() - since) / 1_000_000_000 + " s)"),
                2, 2, java.util.concurrent.TimeUnit.SECONDS);
        try { return registerNow(context, hub, endpoint); } finally { ticker.shutdownNow(); }
    }

    private static int registerNow(Context context, IBinder hub, HubEndpoint endpoint) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.REGISTER, request -> {
            request.writeString(context.getPackageName()); request.writeStrongBinder(endpoint); request.writeBoolean(Boolean.getBoolean("apklayer.componenthost") || HubHost.hub);
            request.writeStrongBinder(((android.app.IApplicationThread) android.app.ActivityThread.currentActivityThread().getApplicationThread()).asBinder());
            // the hub's key of this process: an isolated service instance, or the android:process of a window process started for an activity
            String isolated = System.getProperty("apklayer.isolated");
            request.writeInt(android.os.Process.myUid()); request.writeString(isolated != null ? isolated : HostProcessName.windowProcess());
            request.writeString(System.getProperty("apklayer.processname", context.getPackageName()));  // its name (getRunningAppProcesses)
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
        String version;
        try { version = removed ? "" : " version " + context.getPackageManager().getPackageInfo(name, 0).getLongVersionCode(); } catch (android.content.pm.PackageManager.NameNotFoundException gone) { version = " (not found)"; }
        System.out.println("[apklayer] package " + name + (removed ? " removed" : " (re)installed") + version);
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
    void startInput(IBinder remoteInputConnection, android.view.inputmethod.EditorInfo editor) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.START_INPUT, request -> { request.writeStrongBinder(remoteInputConnection); request.writeTypedObject(editor, 0); }, reply -> null);
    }
    void finishInput() { HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.FINISH_INPUT, request -> {}, reply -> null); }
    /** This package's notification channels in the hub (HubNotificationChannels): {op, arguments} -> what read takes from the reply. */
    <T> T notificationChannels(int op, java.util.function.Consumer<android.os.Parcel> write, java.util.function.Function<android.os.Parcel, T> read) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.NOTIFY_CHANNELS, request -> { request.writeInt(op); write.accept(request); }, read);
    }
    void cancelNotification(String tag, int id, boolean all) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.NOTIFY_CANCEL, request -> { request.writeString(tag); request.writeInt(id); request.writeBoolean(all); }, reply -> null);
    }
    /** The app uid of another app's process (-1 when the hub does not know the pid). */
    int uidOf(int pid) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.UID_OF_PID, request -> request.writeInt(pid), reply -> reply.readInt());
    }
    /** Whether another app's caller holds the permission: the kernel's pid of a synchronous call names it, the uid its header carried a oneway one (pid 0 or -1). */
    /** A BlueZ helper of this process's own (HubBluetooth): {its stdin, its stdout}; an RFCOMM connection when address and uuid (or a channel) are given,
     *  a server socket's listener for the service when only uuid and name are; secure: authenticated and encrypted (an insecure one asks neither). */
    android.os.ParcelFileDescriptor[] bluetooth(String address, String uuid, String name, boolean secure) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.BLUETOOTH, request -> { request.writeString(address); request.writeString(uuid); request.writeString(name); request.writeBoolean(secure); },
                reply -> new android.os.ParcelFileDescriptor[] {reply.readTypedObject(android.os.ParcelFileDescriptor.CREATOR), reply.readTypedObject(android.os.ParcelFileDescriptor.CREATOR)});
    }
    /** The answer to a pairing BlueZ asks about (BluetoothDevice.PAIRING_VARIANT_*), from the person in the hub's dialog: the PIN or passkey, "yes" to a
     *  comparison of {@code key}; null when they refused or for a key only shown. */
    String bluetoothPin(String address, String name, int variant, String key) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.BLUETOOTH_PIN, request -> { request.writeString(address); request.writeString(name); request.writeInt(variant); request.writeString(key); },
                reply -> reply.readString());
    }
    /** The gps fixes from the hub's GNSS HAL client (HubGnss): {@code follow}, a pipe of `ready` and `location LAT LON ACCURACY` lines; else null,
     *  and this process's pipes end (their reader sees the end of the stream). */
    android.os.ParcelFileDescriptor gnss(boolean follow) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.GNSS, request -> request.writeBoolean(follow),
                reply -> reply.readTypedObject(android.os.ParcelFileDescriptor.CREATOR));
    }
    int checkPermission(int pid, int uid, String permission) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CHECK_PERMISSION, request -> { request.writeInt(pid); request.writeInt(uid); request.writeString(permission); }, reply -> reply.readInt());
    }

    /** A committed install session (ShadowHostPackageInstaller) or a legacy install intent ({@code now}): the APKs go to the hub's installer as file descriptors. */
    /** A legacy install intent's APKs (the hub asks the person at once). */
    void commitInstall(java.util.List<String> names, java.util.List<android.os.ParcelFileDescriptor> files, android.content.IntentSender status) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.INSTALL_COMMIT, request -> {
            request.writeInt(files.size());
            for (int i = 0; i < files.size(); i++) { request.writeString(names.get(i)); request.writeFileDescriptor(files.get(i).getFileDescriptor()); }
            request.writeStrongBinder(status == null ? null : status.getTarget().asBinder());
        }, reply -> null);
    }
    /** One operation on the hub's install sessions (HubInstallSessions). */
    <T> T installSession(int op, java.util.function.Consumer<android.os.Parcel> write, java.util.function.Function<android.os.Parcel, T> read) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.INSTALL_SESSION, request -> { request.writeInt(op); write.accept(request); }, read);
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

    /** UsbManager.hasPermission ({@code ask} false) and requestPermission ({@code ask} true: waits for the person's answer). */
    boolean usbPermission(String device, boolean ask) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.USB_PERMISSION, request -> { request.writeString(device); request.writeBoolean(ask); }, reply -> reply.readBoolean());
    }

    /** Activity.requestPermissions: per name HostRuntimePermissions' GRANTED, DENIED_ONCE or DENIED; waits for the person's answers. */
    int[] requestPermissions(String[] names) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.PERMISSIONS_REQUEST, request -> request.writeStringArray(names), reply -> reply.createIntArray());
    }
    /** RoleManager's request intent: whether the person makes this app the role's holder; waits for the answer. */
    boolean requestRole(String role) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.ROLE_REQUEST, request -> request.writeString(role), reply -> reply.readBoolean());
    }

    /** KeyguardManager's confirm intent ({@code challenge} 0) and BiometricPrompt's credential page (its operation id): whether the person confirmed. */
    boolean confirmCredential(String title, String description, long challenge) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CONFIRM_CREDENTIAL, request -> { request.writeString(title); request.writeString(description); request.writeLong(challenge); },
                reply -> reply.readBoolean());
    }
    /** The app cancelled its BiometricPrompt: the hub closes the password dialog of this app's open confirmCredential. */
    void cancelCredential() { HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.CANCEL_CREDENTIAL, request -> { }, reply -> null); }
    /** AudioManager.setMicrophoneMute: the mute in force after the request (HubCallAudio). */
    boolean microphoneMute(boolean on) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.MICROPHONE_MUTE, request -> request.writeBoolean(on), reply -> reply.readBoolean());
    }
    /** AudioManager.setMode/getMode (MODE_CURRENT): the instance's mode after the request (HubCallAudio). */
    int audioMode(int mode) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.AUDIO_MODE, request -> request.writeInt(mode), reply -> reply.readInt());
    }
    /** setCommunicationDevice ({@code set}; null clears this app's choice) or only the read: the communication device in force, null for none. */
    android.media.AudioDeviceAttributes communicationDevice(boolean set, android.media.AudioDeviceAttributes device) {
        return HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.AUDIO_COMMUNICATION_DEVICE, request -> { request.writeBoolean(set); request.writeTypedObject(device, 0); },
                reply -> reply.readTypedObject(android.media.AudioDeviceAttributes.CREATOR));
    }

    /** Alarms and jobs live in the hub (HubScheduler). */
    void setAlarm(android.app.PendingIntent operation, long delayMs, long intervalMs) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.ALARM_SET, request -> { request.writeStrongBinder(operation.getTarget().asBinder()); request.writeLong(delayMs); request.writeLong(intervalMs); }, reply -> null);
    }
    void cancelAlarm(android.app.PendingIntent operation) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.ALARM_CANCEL, request -> request.writeStrongBinder(operation.getTarget().asBinder()), reply -> null);
    }
    void scheduleJob(android.app.job.JobInfo job, long delayMs) {
        HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.JOB_SCHEDULE, request -> { request.writeTypedObject(job, 0); request.writeLong(delayMs); }, reply -> null);
    }
    void retryJob(int jobId, boolean systemStop) { HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.JOB_RETRY, request -> { request.writeInt(jobId); request.writeBoolean(systemStop); }, reply -> null); }
    void cancelJob(int jobId) { HubProtocol.call(hub, HubProtocol.DESCRIPTOR, HubProtocol.JOB_CANCEL, request -> request.writeInt(jobId), reply -> null); }

    static synchronized HubClient attach(Context context) {
        Context app = context.getApplicationContext();  // null while Application.attach runs (LoadedApk has no Application yet): the Application itself
        if (current == null) current = new HubClient(app != null ? app : context);
        return current;
    }

    private static boolean attaching;

    /**
     * Android attaches a process to the system (attachApplication) before any of its code runs; callApplicationOnCreate attaches here, which is later
     * than Application.attachBaseContext: an app reaching a system service there (Nextcloud's accounts, Wikipedia's PendingIntent) joins the hub at that
     * first use. False outside an app process, while installing, or while the attachment is under way.
     */
    static synchronized boolean attachEarly() {
        if (current == null && !attaching && !HubHost.booting && System.getProperty("apklayer.apps") != null && System.getProperty("apklayer.package") != null && !AppEnvironment.installing) {
            android.app.Application app = android.app.ActivityThread.currentApplication();
            if (app != null) {
                attaching = true;
                try { attach(app); }
                catch (RuntimeException failure) { System.out.println("[apklayer] hub not available yet: " + failure); }
                finally { attaching = false; }
            }
        }
        return current != null;
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
