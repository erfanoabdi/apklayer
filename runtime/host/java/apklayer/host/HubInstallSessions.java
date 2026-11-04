package apklayer.host;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The instance's PackageInstaller sessions, as PackageInstallerService keeps them in system_server: each a directory of the instance's data
 * (install-sessions/<id>: session.properties and the APKs its installer writes through file descriptors the hub opens), so a session outlives
 * its installer's process and the hub. An installer that was restarted finds it again with getMySessions, with its preapproval (HubInstaller
 * keeps that in the session). Ids are random, as Android's; a session left alone for three days goes, as on Android (MAX_AGE_MILLIS).
 */
final class HubInstallSessions {
    /** The INSTALL_SESSION operations (ShadowHostPackageInstaller): op, then its arguments. */
    static final int CREATE = 1, INFO = 2, LIST = 3, OPEN_WRITE = 4, NAMES = 5, REMOVE = 6, ABANDON = 7, COMMIT = 8;
    private static final long MAX_AGE_MS = 3L * 24 * 3600 * 1000;
    private final Path root;
    private final HubInstaller installer;
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    HubInstallSessions(Path instanceData, HubInstaller installer) {
        root = instanceData.resolve("install-sessions"); this.installer = installer;
        try (var old = Files.list(root)) {
            for (Path dir : old.toList()) if (System.currentTimeMillis() - Files.getLastModifiedTime(dir).toMillis() > MAX_AGE_MS) HubInstaller.delete(dir);
        } catch (IOException none) { /* no sessions yet */ }
    }

    void transact(HubDirectory.Peer caller, Parcel request, Parcel reply) throws IOException {
        int op = request.readInt();
        switch (op) {
            case CREATE -> {
                String pkg = request.readString(), label = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(create(caller.name(), pkg, label));
            }
            case INFO -> {
                int id = request.readInt(); request.enforceNoDataAvail();
                reply.writeNoException(); write(reply, id, read(id));
            }
            case LIST -> {
                boolean mine = request.readBoolean(); request.enforceNoDataAvail();
                List<Integer> ids = new ArrayList<>();
                try (var dirs = Files.list(root)) { for (Path dir : dirs.toList()) ids.add(Integer.parseInt(dir.getFileName().toString())); }
                catch (IOException | NumberFormatException none) { /* none */ }
                ids.removeIf(id -> read(id) == null || mine && !caller.name().equals(read(id).getProperty("installer")));
                reply.writeNoException(); reply.writeInt(ids.size());
                for (int id : ids) write(reply, id, read(id));
            }
            case OPEN_WRITE -> {
                int id = request.readInt(); String name = request.readString(); long offset = request.readLong(); request.enforceNoDataAvail();
                if (!name.matches("[A-Za-z0-9_.-]+") || name.matches("\\.\\.?|session\\.properties.*")) throw new IllegalArgumentException("Invalid name: " + name);
                Path dir = owned(caller, id);
                try (RandomAccessFile file = new RandomAccessFile(dir.resolve(name).toFile(), "rw")) {
                    file.seek(Math.max(0, offset));  // the descriptor shares this position: writing goes on from the offset, as the session's file on Android
                    reply.writeNoException(); reply.writeFileDescriptor(file.getFD());
                }
            }
            case NAMES -> {
                int id = request.readInt(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeStringArray(apks(owned(caller, id)).stream().map(p -> p.getFileName().toString()).toArray(String[]::new));
            }
            case REMOVE -> {
                int id = request.readInt(); String name = request.readString(); request.enforceNoDataAvail();
                if (name.matches("[A-Za-z0-9_.-]+") && !name.matches("\\.\\.?|session\\.properties.*")) Files.deleteIfExists(owned(caller, id).resolve(name));
                reply.writeNoException();
            }
            case ABANDON -> {
                int id = request.readInt(); request.enforceNoDataAvail();
                HubInstaller.delete(owned(caller, id)); reply.writeNoException();
            }
            case COMMIT -> {
                int id = request.readInt(); IBinder status = request.readStrongBinder(); request.enforceNoDataAvail();
                Path dir = owned(caller, id);
                Properties session = read(id);
                installer.commitSession(caller, id, session.getProperty("package"), session.getProperty("label"), session.getProperty("preapproved"), dir, apks(dir), status);
                reply.writeNoException();
            }
            default -> throw new IllegalArgumentException("Unknown install session operation " + op);
        }
    }

    /** requestUserPreapproval answered yes: the session's commit of that package needs no question. */
    void preapproved(String installer, int id, String pkg) {
        Properties session = read(id);
        if (session == null || !installer.equals(session.getProperty("installer"))) return;
        session.setProperty("preapproved", pkg);
        store(id, session);
    }

    /** Whether the installer owns this session (requestUserPreapproval is for its own). */
    boolean owns(String installer, int id) { Properties session = read(id); return session != null && installer.equals(session.getProperty("installer")); }

    private int create(String installer, String pkg, String label) throws IOException {
        Files.createDirectories(root);
        for (;;) {
            int id = random.nextInt(Integer.MAX_VALUE - 1) + 1;
            try { Files.createDirectory(root.resolve(Integer.toString(id))); }
            catch (java.nio.file.FileAlreadyExistsException taken) { continue; }
            Properties session = new Properties();
            session.setProperty("installer", installer);
            if (pkg != null) session.setProperty("package", pkg);
            if (label != null) session.setProperty("label", label);
            store(id, session);
            return id;
        }
    }

    private Properties read(int id) {
        Properties session = new Properties();
        try (var in = Files.newBufferedReader(root.resolve(id + "/session.properties"))) { session.load(in); return session; }
        catch (IOException | RuntimeException none) { return null; }
    }

    private void store(int id, Properties session) {
        Path file = root.resolve(id + "/session.properties"), next = root.resolve(id + "/session.properties.new");
        try (var out = Files.newBufferedWriter(next)) { session.store(out, null); Files.move(next, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException failure) { throw new IllegalStateException("Cannot keep install session " + id, failure); }
    }

    private Path owned(HubDirectory.Peer caller, int id) {
        Properties session = read(id);
        if (session == null || !caller.name().equals(session.getProperty("installer"))) throw new SecurityException("Caller has no access to session " + id);
        return root.resolve(Integer.toString(id));
    }

    private static List<Path> apks(Path dir) throws IOException {
        try (var files = Files.list(dir)) { return files.filter(f -> !f.getFileName().toString().startsWith("session.properties")).sorted().toList(); }
    }

    /** A SessionInfo's fields (ShadowHostPackageInstaller makes one of them): present, then id, installer, package, label. */
    private static void write(Parcel reply, int id, Properties session) {
        reply.writeBoolean(session != null);
        if (session == null) return;
        reply.writeInt(id); reply.writeString(session.getProperty("installer")); reply.writeString(session.getProperty("package")); reply.writeString(session.getProperty("label"));
    }
}
