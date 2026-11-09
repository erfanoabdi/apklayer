package apklayer.host;

import android.os.storage.VolumeInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The storage volumes of the instance, as the storage service lists them: the private internal volume ({@code /data}), Internal storage (the emulated
 * primary volume, {@code /storage/emulated/0} served from {@code <storage>/emulated/0} by the path layer, what Environment.getExternalStorageDirectory()
 * returns), the desktop's removable drives (public volumes, {@code /storage/<fs uuid>} as vold mounts them), Home (the Linux user's home) and Linux
 * root ({@code /}, which apklayer can mostly only read). {@code -Dapklayer.storage} is the instance's storage directory (cli/layout).
 */
final class HostVolumes {
    private HostVolumes() {}

    static File internal() {
        String storage = System.getProperty("apklayer.storage");
        if (storage == null) return null;
        // Android's spelling when the path layer serves it from the storage directory (APKLAYER_STORAGE, bridge/native/paths.c): MediaProvider reads volume and relative path from it
        if (System.getenv("APKLAYER_STORAGE") != null) storage = "/storage";
        return new File(storage, "emulated/" + android.os.Process.myUserHandle().getIdentifier());
    }

    static List<VolumeInfo> list() {
        int user = android.os.Process.myUserHandle().getIdentifier();
        List<VolumeInfo> volumes = new ArrayList<>();
        VolumeInfo data = new VolumeInfo(VolumeInfo.ID_PRIVATE_INTERNAL, VolumeInfo.TYPE_PRIVATE, null, null);  // findPrivateForEmulated: the primary's uuid and description
        data.path = "/data";
        data.state = VolumeInfo.STATE_MOUNTED;
        volumes.add(data);
        File internal = internal();
        if (internal != null) {
            VolumeInfo emulated = new VolumeInfo("emulated;" + user, VolumeInfo.TYPE_EMULATED, null, null);
            emulated.path = internal.getParentFile().getPath();  // VolumeInfo appends the user id
            mounted(emulated, user, true);
            volumes.add(emulated);
            volumes.addAll(Removable.list(internal.getParentFile().getParentFile(), user));
        }
        volumes.add(stub("linux-home", "Home", System.getProperty("user.home"), user));  // not "home": DocumentsUI treats that id as the stock Documents folder
        volumes.add(stub("linux-root", "Linux root", "/", user));
        return volumes;
    }

    /** A stub volume keeps its path as it is (an emulated one gets "/<user>" appended). */
    private static VolumeInfo stub(String uuid, String label, String path, int user) {
        VolumeInfo volume = new VolumeInfo("stub:" + uuid, VolumeInfo.TYPE_STUB, null, null);
        volume.fsUuid = uuid;
        volume.fsLabel = label;
        volume.path = path;
        mounted(volume, user, false);
        return volume;
    }

    private static void mounted(VolumeInfo volume, int user, boolean primary) {
        volume.state = VolumeInfo.STATE_MOUNTED;
        volume.mountFlags = (primary ? VolumeInfo.MOUNT_FLAG_PRIMARY : 0) | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_READ | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_WRITE;
        volume.mountUserId = user;
        volume.internalPath = volume.path;
    }

    /**
     * The drives the desktop mounted for this user (udisks2: {@code /media/<user>/<label>}, {@code /run/media/<user>/<label>}) as public volumes, as vold
     * mounts an SD card or USB drive: {@code /storage/<fs uuid>} (and {@code /mnt/media_rw/<fs uuid>}, ExternalStorageProvider's path), a link in the
     * instance's storage directory to the drive. Read again on every change (HubVolumes watches them and tells each app, ShadowHostStorageManager.changed).
     */
    static final class Removable {
        private Removable() {}

        static List<VolumeInfo> list(File storage, int user) {
            List<VolumeInfo> volumes = new ArrayList<>();
            String host = System.getProperty("apklayer.storage"), name = System.getProperty("user.name");
            List<String> lines;
            try { lines = Files.readAllLines(Path.of("/proc/self/mounts")); } catch (IOException | RuntimeException unreadable) { return volumes; }
            for (String line : lines) {
                String[] f = line.split(" ");
                if (f.length < 4 || host == null) continue;
                String source = unescape(f[0]), point = unescape(f[1]);
                if (!point.startsWith("/media/" + name + "/") && !point.startsWith("/run/media/" + name + "/")) continue;
                if (!new File(point).canRead()) continue;  // not in this app's view (the sandbox's mount namespace)
                String uuid = uuid(source);
                if (uuid == null) uuid = new File(point).getName().replaceAll("[^A-Za-z0-9_.-]", "_");
                Path link = Path.of(host, uuid);
                try {
                    if (!Files.isSymbolicLink(link) || !Files.readSymbolicLink(link).toString().equals(point)) {
                        Files.deleteIfExists(link);
                        Files.createSymbolicLink(link, Path.of(point));
                    }
                } catch (IOException | RuntimeException failure) { System.out.println("[apklayer] storage: no volume for " + point + ": " + failure); continue; }
                VolumeInfo volume = new VolumeInfo("public:" + uuid, VolumeInfo.TYPE_PUBLIC, null, null);
                volume.fsUuid = uuid;
                volume.fsLabel = new File(point).getName();
                volume.fsType = f[2];
                volume.path = new File(storage, uuid).getPath();
                mounted(volume, user, false);
                if (f[3].startsWith("ro,") || f[3].equals("ro") || !new File(point).canWrite()) volume.state = VolumeInfo.STATE_MOUNTED_READ_ONLY;  // canWrite: a sandboxed app's uid
                volumes.add(volume);
            }
            return volumes;
        }

        /** The desktop's mount point of the volume (its link's target). */
        static String mountPoint(VolumeInfo volume) {
            try { return Files.readSymbolicLink(Path.of(System.getProperty("apklayer.storage"), volume.fsUuid)).toString(); }
            catch (IOException | RuntimeException gone) { return volume.path; }
        }

        /** Removes the links of drives no longer mounted (a removed drive's {@code /storage/<uuid>}), keeping those in {@code mounted}. */
        static void removeStale(java.util.Set<String> mounted) {
            String host = System.getProperty("apklayer.storage"), name = System.getProperty("user.name");
            if (host == null) return;
            try (var links = Files.list(Path.of(host))) {
                for (Path link : links.filter(Files::isSymbolicLink).toList()) {
                    String target = Files.readSymbolicLink(link).toString();
                    if (mounted.contains(link.getFileName().toString()) || !target.startsWith("/media/" + name + "/") && !target.startsWith("/run/media/" + name + "/")) continue;
                    Files.deleteIfExists(link);
                }
            } catch (IOException | RuntimeException failure) { System.out.println("[apklayer] storage: stale volume links not removed: " + failure); }
        }

        /** The file system uuid udev names the device by (/dev/disk/by-uuid), vold's fsUuid. */
        private static String uuid(String source) {
            try (var links = Files.list(Path.of("/dev/disk/by-uuid"))) {
                Path device = Path.of(source).toRealPath();
                return links.filter(l -> { try { return l.toRealPath().equals(device); } catch (IOException e) { return false; } })
                        .map(l -> l.getFileName().toString()).findFirst().orElse(null);
            } catch (IOException | RuntimeException none) { return null; }
        }

        /** /proc/mounts escapes space, tab, newline and backslash as octal. */
        private static String unescape(String s) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\\' && i + 3 < s.length()) { out.append((char) Integer.parseInt(s.substring(i + 1, i + 4), 8)); i += 3; }
                else out.append(s.charAt(i));
            }
            return out.toString();
        }
    }
}
