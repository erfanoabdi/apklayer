package apklayer.host;

import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.VersionedPackage;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ClassName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageInstaller;

/**
 * An app's PackageInstaller with the instance's installer behind it (HubInstaller): Robolectric keeps the session list and its callbacks, the session's
 * APKs are written to the app's own cache and handed to the hub at commit, the status comes back through the app's IntentSender. Without a hub (an
 * app run on its own) there is no installer: commit fails. shortcut: sessions live in the app process that made them (Android keeps them in
 * system_server across the app's restarts); move them to the hub if an installer resumes sessions after it was restarted.
 */
@Implements(value = PackageInstaller.class, isInAndroidSdk = false)
public class ShadowHostPackageInstaller extends ShadowPackageInstaller {
    private final Map<Integer, PackageInstaller.SessionParams> params = new HashMap<>();

    @Implementation
    @Override protected int createSession(PackageInstaller.SessionParams sessionParams) throws IOException {
        int id = super.createSession(sessionParams);
        params.put(id, sessionParams);
        return id;
    }

    @Implementation
    @Override protected PackageInstaller.Session openSession(int sessionId) throws IOException {
        PackageInstaller.Session session = super.openSession(sessionId);
        ((Session) Shadow.extract(session)).bind(sessionId, this);
        return session;
    }

    @Implementation
    @Override protected void abandonSession(int sessionId) {
        super.abandonSession(sessionId);
        params.remove(sessionId);
        delete(dir(sessionId));
    }

    @Implementation(minSdk = 34)
    @Override protected void uninstall(VersionedPackage versionedPackage, int flags, IntentSender statusReceiver) { uninstall(versionedPackage.getPackageName(), statusReceiver); }

    @Implementation
    @Override protected void uninstall(VersionedPackage versionedPackage, IntentSender statusReceiver) { uninstall(versionedPackage.getPackageName(), statusReceiver); }

    @Implementation
    @Override protected void uninstall(String packageName, IntentSender statusReceiver) {
        if (!HubClient.connected()) throw new SecurityException("No package installer: the app runs without its instance hub");
        HubClient.current().uninstall(packageName, statusReceiver, false);
    }

    /** Where a session's APKs are written until its commit hands them to the hub. */
    static File dir(int sessionId) { return new File(RuntimeEnvironment.getApplication().getCacheDir(), "apklayer-install/" + sessionId); }

    static void delete(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }

    /** PackageInstaller.Session over a directory of the app's cache. */
    @Implements(value = PackageInstaller.Session.class, isInAndroidSdk = false)
    public static class Session extends ShadowPackageInstaller.ShadowSession {
        private int sessionId;
        private ShadowHostPackageInstaller installer;
        private final List<OutputStream> open = new ArrayList<>();

        void bind(int id, ShadowHostPackageInstaller owner) { sessionId = id; installer = owner; }

        @Implementation
        @Override protected OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
            if (!name.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Invalid name: " + name);  // FileUtils.isValidExtFilename, as the platform checks it
            File dir = dir(sessionId);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            // shortcut: offsetBytes > 0 (resuming a write) appends; Android seeks. Seek if an installer resumes at an offset other than the end.
            FileOutputStream out = new FileOutputStream(new File(dir, name), offsetBytes > 0) {
                @Override public void close() throws IOException { super.close(); synchronized (open) { open.remove(this); } }
            };
            synchronized (open) { open.add(out); }
            return out;
        }

        @Implementation
        @Override protected void fsync(OutputStream out) throws IOException { if (out instanceof FileOutputStream file) file.getFD().sync(); }

        @Implementation
        protected String[] getNames() {
            String[] names = dir(sessionId).list();
            return names == null ? new String[0] : names;
        }

        @Implementation
        protected void removeSplit(String splitName) { new File(dir(sessionId), splitName).delete(); }

        @Implementation(minSdk = 34)
        @Override protected void requestUserPreapproval(@ClassName("android.content.pm.PackageInstaller$PreapprovalDetails") Object details, IntentSender statusReceiver) {
            PackageInstaller.PreapprovalDetails approval = (PackageInstaller.PreapprovalDetails) details;
            if (!HubClient.connected()) throw new SecurityException("No package installer: the app runs without its instance hub");
            HubClient.current().preapproveInstall(sessionId, approval.getPackageName(), String.valueOf(approval.getLabel()), statusReceiver);
        }

        @Implementation
        @Override protected void commit(IntentSender statusReceiver) {
            synchronized (open) { if (!open.isEmpty()) throw new SecurityException("Files still open"); }
            if (!HubClient.connected()) throw new SecurityException("No package installer: the app runs without its instance hub");
            File[] files = dir(sessionId).listFiles();
            if (files == null || files.length == 0) throw new IllegalStateException("Session " + sessionId + " has no APK");
            java.util.Arrays.sort(files);
            PackageInstaller.SessionParams p = installer.params.get(sessionId);
            List<String> names = new ArrayList<>();
            List<ParcelFileDescriptor> fds = new ArrayList<>();
            try {
                for (File f : files) { names.add(f.getName()); fds.add(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)); }
                HubClient.current().commitInstall(sessionId, p == null ? null : p.appPackageName, p == null || p.appLabel == null ? null : p.appLabel.toString(), names, fds, statusReceiver, false);
            } catch (IOException failure) { throw new IllegalStateException("Cannot read the session's APKs", failure); }
            finally {
                for (ParcelFileDescriptor fd : fds) { try { fd.close(); } catch (IOException ignored) { /* closed */ } }
                delete(dir(sessionId));  // the hub has its own copy
            }
        }

        @Implementation
        @Override protected void close() {}

        @Implementation
        @Override protected void abandon() { if (installer != null) installer.abandonSession(sessionId); }
    }
}
