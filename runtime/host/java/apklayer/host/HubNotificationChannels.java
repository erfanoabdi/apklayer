package apklayer.host;

import android.app.NotificationChannel;
import android.os.Parcel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An app's notification channels, per package for all its processes, kept by the hub as NotificationManagerService's PreferencesHelper keeps
 * them (in the instance, `notification-channels/<package>`; uninstall and clear-data remove the file): created once, a later create changes only
 * the name, the description, a lower importance and an unset group; a deleted channel comes back with its settings when it is created again.
 */
final class HubNotificationChannels {
    static final int CREATE = 0, GET = 1, LIST = 2, DELETE = 3;
    private HubNotificationChannels() {}

    /** One NOTIFY_CHANNELS call of the package's process: {op, arguments} -> the reply. */
    static synchronized void call(String pkg, int op, Parcel request, Parcel reply) {
        Map<String, NotificationChannel> live = new LinkedHashMap<>(), deleted = new LinkedHashMap<>();
        read(pkg, live, deleted);
        switch (op) {
            case CREATE -> {
                for (NotificationChannel channel : request.createTypedArrayList(NotificationChannel.CREATOR)) create(channel, live, deleted);
                write(pkg, live, deleted);
                reply.writeNoException();
            }
            case GET -> { String id = request.readString(); reply.writeNoException(); reply.writeTypedObject(live.get(id), 0); }
            case LIST -> { reply.writeNoException(); reply.writeTypedList(new ArrayList<>(live.values())); }
            case DELETE -> {
                NotificationChannel gone = live.remove(request.readString());
                if (gone != null) { gone.setDeleted(true); deleted.put(gone.getId(), gone); write(pkg, live, deleted); }
                reply.writeNoException();
            }
            default -> throw new IllegalArgumentException("notification channels: unknown op " + op);
        }
    }

    /** PreferencesHelper.createNotificationChannel's rules for an app's own call. */
    private static void create(NotificationChannel channel, Map<String, NotificationChannel> live, Map<String, NotificationChannel> deleted) {
        NotificationChannel existing = live.get(channel.getId());
        if (existing == null && (existing = deleted.remove(channel.getId())) != null) { existing.setDeleted(false); live.put(existing.getId(), existing); }
        if (existing == null) { live.put(channel.getId(), channel); return; }
        existing.setName(channel.getName());
        existing.setDescription(channel.getDescription());
        if (channel.getImportance() < existing.getImportance()) existing.setImportance(channel.getImportance());
        if (existing.getGroup() == null || existing.getGroup().isEmpty()) existing.setGroup(channel.getGroup());
    }

    private static Path file(String pkg) {
        if (pkg.contains("/") || pkg.startsWith(".")) throw new SecurityException("not a package name: " + pkg);
        Path dir = HostRuntimePermissions.file("notification-channels");
        return dir == null ? null : dir.resolve(pkg);
    }

    private static void read(String pkg, Map<String, NotificationChannel> live, Map<String, NotificationChannel> deleted) {
        Path file = file(pkg);
        byte[] bytes;
        try { bytes = file == null || !Files.exists(file) ? null : Files.readAllBytes(file); }
        catch (IOException unreadable) { System.out.println("[apklayer-hub] " + file + ": " + unreadable); return; }
        if (bytes == null) return;
        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            for (NotificationChannel c : parcel.createTypedArrayList(NotificationChannel.CREATOR)) live.put(c.getId(), c);
            for (NotificationChannel c : parcel.createTypedArrayList(NotificationChannel.CREATOR)) deleted.put(c.getId(), c);
        } catch (RuntimeException corrupt) { System.out.println("[apklayer-hub] " + file + " unreadable, channels start anew: " + corrupt); live.clear(); deleted.clear(); }
        finally { parcel.recycle(); }
    }

    private static void write(String pkg, Map<String, NotificationChannel> live, Map<String, NotificationChannel> deleted) {
        Path file = file(pkg);
        if (file == null) return;
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeTypedList(new ArrayList<>(live.values()));
            parcel.writeTypedList(new ArrayList<>(deleted.values()));
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(pkg + ".tmp");
            Files.write(tmp, parcel.marshall());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) { System.out.println("[apklayer-hub] cannot write " + file + ": " + failure); }
        finally { parcel.recycle(); }
    }
}
