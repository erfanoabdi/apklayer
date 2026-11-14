package apklayer.host;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
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
 * The channel groups are kept with them (create replaces, a listed group carries its channels, deleting a group deletes its channels).
 */
final class HubNotificationChannels {
    static final int CREATE = 0, GET = 1, LIST = 2, DELETE = 3, CREATE_GROUPS = 4, GET_GROUP = 5, LIST_GROUPS = 6, DELETE_GROUP = 7;
    private HubNotificationChannels() {}

    /** The package's live channels (the settings page lists them). */
    static synchronized java.util.List<NotificationChannel> list(String pkg) {
        Map<String, NotificationChannel> live = new LinkedHashMap<>();
        read(pkg, live, new LinkedHashMap<>(), new LinkedHashMap<>());
        return new ArrayList<>(live.values());
    }

    /** One NOTIFY_CHANNELS call of the package's process: {op, arguments} -> the reply. */
    static synchronized void call(String pkg, int op, Parcel request, Parcel reply) {
        Map<String, NotificationChannel> live = new LinkedHashMap<>(), deleted = new LinkedHashMap<>();
        Map<String, NotificationChannelGroup> groups = new LinkedHashMap<>();
        read(pkg, live, deleted, groups);
        switch (op) {
            case CREATE -> {
                for (NotificationChannel channel : request.createTypedArrayList(NotificationChannel.CREATOR)) create(channel, live, deleted);
                write(pkg, live, deleted, groups);
                reply.writeNoException();
            }
            case GET -> { String id = request.readString(); reply.writeNoException(); reply.writeTypedObject(live.get(id), 0); }
            case LIST -> { reply.writeNoException(); reply.writeTypedList(new ArrayList<>(live.values())); }
            case DELETE -> {
                NotificationChannel gone = live.remove(request.readString());
                if (gone != null) { gone.setDeleted(true); deleted.put(gone.getId(), gone); write(pkg, live, deleted, groups); }
                reply.writeNoException();
            }
            case CREATE_GROUPS -> {
                for (NotificationChannelGroup group : request.createTypedArrayList(NotificationChannelGroup.CREATOR)) groups.put(group.getId(), group);  // PreferencesHelper.createNotificationChannelGroup: the later one replaces
                write(pkg, live, deleted, groups);
                reply.writeNoException();
            }
            case GET_GROUP -> { NotificationChannelGroup group = groups.get(request.readString()); reply.writeNoException(); reply.writeTypedObject(group == null ? null : withChannels(group, live), 0); }
            case LIST_GROUPS -> {
                ArrayList<NotificationChannelGroup> listed = new ArrayList<>();
                for (NotificationChannelGroup group : groups.values()) listed.add(withChannels(group, live));
                reply.writeNoException(); reply.writeTypedList(listed);
            }
            case DELETE_GROUP -> {  // the group's channels go with it
                String id = request.readString();
                if (groups.remove(id) != null) {
                    for (NotificationChannel channel : new ArrayList<>(live.values())) if (id.equals(channel.getGroup())) { live.remove(channel.getId()); channel.setDeleted(true); deleted.put(channel.getId(), channel); }
                    write(pkg, live, deleted, groups);
                }
                reply.writeNoException();
            }
            default -> throw new IllegalArgumentException("notification channels: unknown op " + op);
        }
    }

    /** The group as NotificationManagerService lists it: with its live channels. */
    private static NotificationChannelGroup withChannels(NotificationChannelGroup group, Map<String, NotificationChannel> live) {
        NotificationChannelGroup copy = group.clone();
        for (NotificationChannel channel : live.values()) if (group.getId().equals(channel.getGroup())) copy.addChannel(channel);
        return copy;
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

    private static void read(String pkg, Map<String, NotificationChannel> live, Map<String, NotificationChannel> deleted, Map<String, NotificationChannelGroup> groups) {
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
            if (parcel.dataAvail() > 0) for (NotificationChannelGroup g : parcel.createTypedArrayList(NotificationChannelGroup.CREATOR)) groups.put(g.getId(), g);  // a file of before the groups ends here
        } catch (RuntimeException corrupt) { System.out.println("[apklayer-hub] " + file + " unreadable, channels start anew: " + corrupt); live.clear(); deleted.clear(); groups.clear(); }
        finally { parcel.recycle(); }
    }

    private static void write(String pkg, Map<String, NotificationChannel> live, Map<String, NotificationChannel> deleted, Map<String, NotificationChannelGroup> groups) {
        Path file = file(pkg);
        if (file == null) return;
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeTypedList(new ArrayList<>(live.values()));
            parcel.writeTypedList(new ArrayList<>(deleted.values()));
            parcel.writeTypedList(new ArrayList<>(groups.values()));
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(pkg + ".tmp");
            Files.write(tmp, parcel.marshall());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) { System.out.println("[apklayer-hub] cannot write " + file + ": " + failure); }
        finally { parcel.recycle(); }
    }
}
