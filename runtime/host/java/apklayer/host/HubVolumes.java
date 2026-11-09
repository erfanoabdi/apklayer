package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In the hub: the desktop's removable drives as they come and go, StorageManagerService's part. Changes arrive from `udisksctl monitor` (the desktop
 * mounts drives through udisks2: every mount, unmount and device add or removal is a line there; the line only says "look again"). Each look compares
 * the drives mounted now (HostVolumes.Removable) with the last look and, per changed volume, as onVolumeStateChangedLocked: every running app's
 * StorageManager learns the new list and its callbacks run (ENDPOINT_VOLUME), then ACTION_VOLUME_STATE_CHANGED (system receivers holding
 * WRITE_MEDIA_STORAGE: ExternalStorageProvider's roots) and the public broadcast of the new state (ACTION_MEDIA_MOUNTED, then on removal
 * ACTION_MEDIA_EJECT, ACTION_MEDIA_UNMOUNTED and, once the device is gone, ACTION_MEDIA_REMOVED), and MediaProvider's ExternalStorageService
 * learns the state (it scans a mounted volume: HubStorageService); HubMediaWatch has it index what changes on the mounted volumes afterwards.
 */
final class HubVolumes {
    private final HubDirectory directory;
    private final HubMediaWatch media;
    /** fs uuid -> the volume as last seen mounted; and the uuids unmounted whose device is still there (vold's STATE_UNMOUNTED). */
    private final Map<String, VolumeInfo> mounted = new HashMap<>();
    private final Map<String, VolumeInfo> unmounted = new HashMap<>();

    HubVolumes(HubDirectory directory) { this.directory = directory; this.media = new HubMediaWatch(directory); }

    /**
     * The first look, then the watch, on this thread until the hub ends (it started the monitor: setpriv's parent-death signal is this thread's).
     * A boot (HubBootBroadcasts) announces what is mounted. Any other hub start catches up with what happened while no hub ran: the drives
     * mounted when the last hub ended are recorded ({@code volumes-mounted}), so a drive mounted or removed since is announced as on a hot plug, and
     * what changed on internal storage and the drives still mounted since the last scan is scanned (HubMediaWatch). An instance's first hub start
     * (nothing recorded) announces its drives and has MediaProvider scan internal storage, as a first boot would.
     */
    void watch() {
        boolean boot = HubBootBroadcasts.pending(directory);
        long since = boot ? -1 : media.stamp();
        List<VolumeInfo> now = removable();
        synchronized (this) {
            if (boot) {
                for (VolumeInfo volume : now) mounted.put(volume.fsUuid, volume);
                HostVolumes.Removable.removeStale(mounted.keySet());
            } else if (since >= 0) mounted.putAll(recorded());
            for (VolumeInfo volume : now)  // the drives still mounted as they were: what changed on them; a new one is scanned whole when it is announced
                if (mounted.containsKey(volume.fsUuid) && mounted.get(volume.fsUuid).path.equals(volume.path))
                    media.add(Path.of(HostVolumes.Removable.mountPoint(volume)), volume.path, since);
        }
        File primary = HostVolumes.internal();
        String storage = System.getProperty("apklayer.storage");
        if (storage != null && primary != null) media.add(Path.of(storage, "emulated", primary.getName()), primary.getPath(), since);
        if (!boot) {
            HubBootSettings.awaitBoot();  // the system's package attached, as for the boot's broadcasts
            try { look(); } catch (RuntimeException failure) { System.out.println("[apklayer-hub] volumes: " + failure); }
            if (since < 0 && primary != null) {
                System.out.println("[apklayer-hub] volumes: the instance's first scan of internal storage");
                mountedVolumes().stream().filter(v -> v.getType() == VolumeInfo.TYPE_EMULATED).forEach(v -> HubStorageService.notify(directory, storageVolume(v)));
            }
        }
        record();
        media.settle();
        Process monitor;
        try {  // line by line: its stdout into a pipe is block-buffered
            monitor = new ProcessBuilder(HostProcess.dying("stdbuf", "-oL", "udisksctl", "monitor")).redirectErrorStream(true).start();
        } catch (IOException noUdisks) { System.out.println("[apklayer-hub] volumes: no udisksctl, removable drives are read once"); return; }
        Runtime.getRuntime().addShutdownHook(new Thread(monitor::destroy));
        System.out.println("[apklayer-hub] volumes: watching the desktop's drives (" + mounted.size() + " mounted)");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
            while (in.readLine() != null) {
                try { look(); } catch (RuntimeException failure) { System.out.println("[apklayer-hub] volumes: " + failure); failure.printStackTrace(System.out); }
            }
        } catch (IOException ended) { /* stopped with the JVM */ }
        System.out.println("[apklayer-hub] volumes: udisksctl monitor ended");
    }

    /** The mounted volumes (primary and drives) for the boot's ACTION_MEDIA_MOUNTED (HubBootBroadcasts), as vold mounts them at a device's boot. */
    List<VolumeInfo> mountedVolumes() {
        return HostVolumes.list().stream().filter(v -> (v.getType() == VolumeInfo.TYPE_EMULATED || v.getType() == VolumeInfo.TYPE_PUBLIC) && v.isMountedReadable()).toList();
    }

    private static List<VolumeInfo> removable() {
        File internal = HostVolumes.internal();
        if (internal == null) return List.of();
        return HostVolumes.Removable.list(internal.getParentFile().getParentFile(), android.os.Process.myUserHandle().getIdentifier());
    }

    private synchronized void look() {
        Map<String, VolumeInfo> now = new HashMap<>();
        for (VolumeInfo volume : removable()) now.put(volume.fsUuid, volume);
        for (VolumeInfo volume : now.values()) {
            VolumeInfo before = mounted.get(volume.fsUuid);
            if (before != null && before.path.equals(volume.path) && before.state == volume.state) continue;
            unmounted.remove(volume.fsUuid);
            mounted.put(volume.fsUuid, volume);
            changed(volume, VolumeInfo.STATE_UNMOUNTED);
            media.add(Path.of(HostVolumes.Removable.mountPoint(volume)), volume.path, -1);
        }
        for (VolumeInfo gone : List.copyOf(mounted.values())) {
            if (now.containsKey(gone.fsUuid)) continue;
            mounted.remove(gone.fsUuid);
            media.remove(gone.path);
            changed(state(gone, VolumeInfo.STATE_EJECTING), VolumeInfo.STATE_MOUNTED);
            changed(state(gone, VolumeInfo.STATE_UNMOUNTED), VolumeInfo.STATE_EJECTING);
            unmounted.put(gone.fsUuid, state(gone, VolumeInfo.STATE_UNMOUNTED));
        }
        for (VolumeInfo idle : List.copyOf(unmounted.values())) {
            if (Files.exists(Path.of("/dev/disk/by-uuid", idle.fsUuid))) continue;  // unmounted, the drive still plugged in
            unmounted.remove(idle.fsUuid);
            changed(state(idle, VolumeInfo.STATE_REMOVED), VolumeInfo.STATE_UNMOUNTED);
        }
        HostVolumes.Removable.removeStale(mounted.keySet());
        record();
    }

    private static Path recordFile() { return Path.of(System.getProperty("apklayer.storage", "/nonexistent")).getParent().resolve("volumes-mounted"); }

    /** The drives mounted as the hub last saw them: "uuid TAB label TAB fs type TAB state" per line. */
    private synchronized void record() {
        StringBuilder text = new StringBuilder();
        for (VolumeInfo v : mounted.values()) text.append(v.fsUuid).append('\t').append(v.fsLabel).append('\t').append(v.fsType).append('\t').append(v.state).append('\n');
        try { Files.writeString(recordFile(), text); } catch (IOException | RuntimeException failure) { System.out.println("[apklayer-hub] volumes: not recorded: " + failure); }
    }

    private static Map<String, VolumeInfo> recorded() {
        Map<String, VolumeInfo> volumes = new HashMap<>();
        File internal = HostVolumes.internal();
        if (internal == null) return volumes;
        try {
            for (String line : Files.readAllLines(recordFile())) {
                String[] f = line.split("\t", -1);
                if (f.length != 4 || f[0].isEmpty()) continue;
                VolumeInfo volume = HostVolumes.Removable.volume(internal.getParentFile().getParentFile(), android.os.Process.myUserHandle().getIdentifier(), f[0], f[1], f[2]);
                volume.state = Integer.parseInt(f[3]);
                volumes.put(f[0], volume);
            }
        } catch (IOException | RuntimeException none) { /* none recorded */ }
        return volumes;
    }

    private static VolumeInfo state(VolumeInfo volume, int state) {
        VolumeInfo copy = new VolumeInfo(volume);
        copy.state = state;
        return copy;
    }

    /** StorageManagerService.onVolumeStateChangedLocked and its H_VOLUME_BROADCAST, for the owner (user 0). */
    private void changed(VolumeInfo volume, int oldState) {
        System.out.println("[apklayer-hub] volume " + volume.id + " " + VolumeInfo.getEnvironmentForState(oldState) + " -> " + VolumeInfo.getEnvironmentForState(volume.state));
        directory.volumeChanged(volume, oldState);
        int flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND;
        directory.sendSystem(new Intent(VolumeInfo.ACTION_VOLUME_STATE_CHANGED).putExtra(VolumeInfo.EXTRA_VOLUME_ID, volume.id)
                .putExtra(VolumeInfo.EXTRA_VOLUME_STATE, volume.state).putExtra(android.os.storage.VolumeRecord.EXTRA_FS_UUID, volume.fsUuid).addFlags(flags),
                android.Manifest.permission.WRITE_MEDIA_STORAGE);
        announce(directory, volume);
    }

    /**
     * The public broadcast of the volume's state (ACTION_MEDIA_MOUNTED, ...) with its StorageVolume, as H_VOLUME_BROADCAST sends it, and the state
     * told to MediaProvider's ExternalStorageService (StorageSessionController: it attaches and scans a mounted volume, detaches a gone one).
     */
    static void announce(HubDirectory directory, VolumeInfo volume) {
        StorageVolume user = storageVolume(volume);
        String action = VolumeInfo.getBroadcastForEnvironment(user.getState());
        if (action != null) directory.sendSystem(new Intent(action, Uri.fromFile(user.getPathFile())).putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, user)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND), false);
        HubStorageService.notify(directory, user);
    }

    private static StorageVolume storageVolume(VolumeInfo volume) {
        return volume.buildStorageVolume(org.robolectric.RuntimeEnvironment.getApplication(), android.os.Process.myUserHandle().getIdentifier(), false);
    }
}
