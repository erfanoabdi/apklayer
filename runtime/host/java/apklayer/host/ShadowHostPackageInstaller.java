package apklayer.host;

import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.VersionedPackage;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ClassName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageInstaller;

/**
 * An app's PackageInstaller over the instance's installer: the sessions are the hub's (HubInstallSessions, kept across the app's and the hub's restarts
 * as system_server keeps them), the APKs are written into them through descriptors the hub opens, a commit hands the session to HubInstaller and the
 * status comes back through the app's IntentSender. SessionCallbacks hear of this app's own sessions. Without a hub (an app run on its own) there is no
 * installer: createSession fails.
 */
@Implements(value = PackageInstaller.class, isInAndroidSdk = false)
public class ShadowHostPackageInstaller extends ShadowPackageInstaller {
    /** The SessionCallbacks, kept here: Robolectric's list is private and its abandonSession reports every end as a failure. */
    private final Map<PackageInstaller.SessionCallback, Handler> callbacks = new java.util.concurrent.ConcurrentHashMap<>();

    @Implementation
    @Override protected void registerSessionCallback(PackageInstaller.SessionCallback callback, Handler handler) { callbacks.put(callback, handler); }

    @Implementation
    @Override protected void unregisterSessionCallback(PackageInstaller.SessionCallback callback) { callbacks.remove(callback); }

    private void each(java.util.function.Consumer<PackageInstaller.SessionCallback> call) { callbacks.forEach((callback, handler) -> handler.post(() -> call.accept(callback))); }

    private static HubClient hub() {
        if (!HubClient.connected()) throw new SecurityException("No package installer: the app runs without its instance hub");
        return HubClient.current();
    }

    @Implementation
    @Override protected int createSession(PackageInstaller.SessionParams params) throws IOException {
        if (!HubClient.connected()) throw new IOException("No package installer: the app runs without its instance hub");
        int id = hub().installSession(HubInstallSessions.CREATE, request -> {
            request.writeString(params.appPackageName); request.writeString(params.appLabel == null ? null : params.appLabel.toString());
        }, reply -> reply.readInt());
        each(callback -> callback.onCreated(id));
        return id;
    }

    @Implementation
    @Override protected PackageInstaller.SessionInfo getSessionInfo(int sessionId) {
        return HubClient.connected() ? hub().installSession(HubInstallSessions.INFO, request -> request.writeInt(sessionId), ShadowHostPackageInstaller::info) : null;
    }

    @Implementation
    @Override protected List<PackageInstaller.SessionInfo> getAllSessions() { return sessions(false); }

    @Implementation
    @Override protected List<PackageInstaller.SessionInfo> getMySessions() { return sessions(true); }

    private static List<PackageInstaller.SessionInfo> sessions(boolean mine) {
        if (!HubClient.connected()) return List.of();
        return hub().installSession(HubInstallSessions.LIST, request -> request.writeBoolean(mine), reply -> {
            List<PackageInstaller.SessionInfo> all = new ArrayList<>();
            for (int n = reply.readInt(); n > 0; n--) all.add(info(reply));
            return all;
        });
    }

    /** A SessionInfo from the hub's fields (HubInstallSessions.write). */
    private static PackageInstaller.SessionInfo info(android.os.Parcel reply) {
        if (!reply.readBoolean()) return null;
        PackageInstaller.SessionInfo info = new PackageInstaller.SessionInfo();
        info.sessionId = reply.readInt(); info.installerPackageName = reply.readString(); info.appPackageName = reply.readString(); info.appLabel = reply.readString();
        info.active = true; info.mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
        return info;
    }

    @Implementation
    @Override protected PackageInstaller.Session openSession(int sessionId) throws IOException {
        PackageInstaller.SessionInfo info = getSessionInfo(sessionId);
        if (info == null || !RuntimeEnvironment.getApplication().getPackageName().equals(info.installerPackageName)) throw new SecurityException("Caller has no access to session " + sessionId);
        PackageInstaller.Session session = new PackageInstaller.Session(null);
        ((Session) Shadow.extract(session)).bind(sessionId, this);
        return session;
    }

    @Implementation
    @Override protected void abandonSession(int sessionId) {
        hub().installSession(HubInstallSessions.ABANDON, request -> request.writeInt(sessionId), reply -> null);
        finished(sessionId, false);
    }

    /** The session ended (its final status came, or it was abandoned): SessionCallback.onFinished says how. */
    private void finished(int sessionId, boolean success) { each(callback -> callback.onFinished(sessionId, success)); }

    @Implementation(minSdk = 34)
    @Override protected void uninstall(VersionedPackage versionedPackage, int flags, IntentSender statusReceiver) { uninstall(versionedPackage.getPackageName(), statusReceiver); }

    @Implementation
    @Override protected void uninstall(VersionedPackage versionedPackage, IntentSender statusReceiver) { uninstall(versionedPackage.getPackageName(), statusReceiver); }

    @Implementation
    @Override protected void uninstall(String packageName, IntentSender statusReceiver) { hub().uninstall(packageName, statusReceiver, false); }

    /** PackageInstaller.Session over one of the hub's sessions. */
    @Implements(value = PackageInstaller.Session.class, isInAndroidSdk = false)
    public static class Session extends ShadowPackageInstaller.ShadowSession {
        private int sessionId;
        private ShadowHostPackageInstaller installer;
        private final List<OutputStream> open = new ArrayList<>();

        void bind(int id, ShadowHostPackageInstaller owner) { sessionId = id; installer = owner; }

        @Implementation
        @Override protected OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
            if (!name.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Invalid name: " + name);  // FileUtils.isValidExtFilename, as the platform checks it
            ParcelFileDescriptor file = hub().installSession(HubInstallSessions.OPEN_WRITE, request -> {
                request.writeInt(sessionId); request.writeString(name); request.writeLong(offsetBytes);
            }, reply -> reply.readFileDescriptor());
            OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(file) {
                @Override public void close() throws IOException { super.close(); synchronized (open) { open.remove(this); } }
            };
            synchronized (open) { open.add(out); }
            return out;
        }

        @Implementation
        @Override protected void fsync(OutputStream out) throws IOException { if (out instanceof java.io.FileOutputStream file) file.getFD().sync(); }

        @Implementation
        protected String[] getNames() { return hub().installSession(HubInstallSessions.NAMES, request -> request.writeInt(sessionId), reply -> reply.createStringArray()); }

        @Implementation
        protected void removeSplit(String splitName) {
            hub().installSession(HubInstallSessions.REMOVE, request -> { request.writeInt(sessionId); request.writeString(splitName); }, reply -> null);
        }

        @Implementation(minSdk = 34)
        @Override protected void requestUserPreapproval(@ClassName("android.content.pm.PackageInstaller$PreapprovalDetails") Object details, IntentSender statusReceiver) {
            PackageInstaller.PreapprovalDetails approval = (PackageInstaller.PreapprovalDetails) details;
            hub().preapproveInstall(sessionId, approval.getPackageName(), String.valueOf(approval.getLabel()), statusReceiver);
        }

        @Implementation
        @Override protected void commit(IntentSender statusReceiver) {
            synchronized (open) { if (!open.isEmpty()) throw new SecurityException("Files still open"); }
            if (getNames().length == 0) throw new IllegalStateException("Session " + sessionId + " has no APK");
            IntentSender status = status(statusReceiver);
            hub().installSession(HubInstallSessions.COMMIT, request -> { request.writeInt(sessionId); request.writeStrongBinder(status.getTarget().asBinder()); }, reply -> null);
        }

        /** The installer's status receiver behind one that also reports the session's end when the status is final. */
        private IntentSender status(IntentSender receiver) {
            return new IntentSender((android.content.IIntentSender) new android.content.IIntentSender.Stub() {
                @Override public void send(int code, android.content.Intent fill, String type, android.os.IBinder token, android.content.IIntentReceiver finished, String permission, android.os.Bundle options) {
                    int status = fill.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    try { receiver.sendIntent(RuntimeEnvironment.getApplication(), code, fill, null, null, permission); }
                    catch (IntentSender.SendIntentException canceled) { System.out.println("[apklayer] the status receiver of session " + sessionId + " is canceled"); }
                    if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) new Handler(Looper.getMainLooper()).post(() -> installer.finished(sessionId, status == PackageInstaller.STATUS_SUCCESS));
                }
            });
        }

        @Implementation
        @Override protected void close() {}

        @Implementation
        @Override protected void abandon() { if (installer != null) installer.abandonSession(sessionId); }
    }
}
