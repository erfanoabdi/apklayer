package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * In the hub: files that appear, change or go on a mounted volume (the instance's internal storage, the desktop's drives) reach MediaStore. On a device
 * the FUSE daemon tells MediaProvider of each file an app writes; here apps and the desktop write the files directly, so inotify (the JDK's WatchService,
 * a watch per directory) stands in for it, and each changed path goes to MediaProvider's own scanner as ACTION_MEDIA_SCANNER_SCAN_FILE (MediaReceiver,
 * ModernMediaScanner.scanFile: a new or changed file is indexed, a gone one removed, a directory scanned whole). A path is sent once it has
 * been quiet for QUIET_MS, so a file being copied is scanned when the copy is done. App-private Android/ is not watched (MediaProvider does not scan it).
 * <p>
 * What changed while no hub ran: the instance keeps the time up to which every change was sent ({@code media-scanned}); a volume's walk at hub start
 * sends each directory and file changed since (mtime; a directory's changes when an entry came or went, so its scan finds the gone ones too).
 * <p>
 * Watches are bounded (inotify's per-user limit is shared with the desktop): the volume's top and its media directories first, the rest while the budget
 * lasts (APKLAYER_MEDIA_WATCHES, else a quarter of fs.inotify.max_user_watches). What is not watched is caught by the next hub start's walk or a mount's
 * scan; while some directory is unwatched (or inotify dropped events) the time kept stays at this hub's start. fanotify's filesystem marks would need
 * CAP_SYS_ADMIN (unprivileged fanotify, Linux 5.13+, has inode marks only). The counts are in {@code <state>/media-watch} (`apklayer status`).
 */
final class HubMediaWatch {
    private static final long QUIET_MS = 1500, SLACK_MS = 2000;  // SLACK: vfat keeps modification times to 2 s
    private static final List<String> MEDIA = List.of("DCIM", "Pictures", "Movies", "Music", "Download", "Documents", "Recordings", "Podcasts", "Audiobooks",
            "Ringtones", "Alarms", "Notifications");  // Environment's standard directories
    private final HubDirectory directory;
    private final WatchService watcher;
    private final int budget = budget();
    /** Watched directory (host path) -> the Android path of the volume root it is under, and that root's host path. */
    private final Map<WatchKey, Path> dirs = new HashMap<>();
    private final Map<Path, String> roots = new HashMap<>();
    private final Set<Path> watching = new java.util.HashSet<>(), over = new java.util.HashSet<>();  // directories watched; those over the budget
    private final Set<String> due = new LinkedHashSet<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "hub-media-scan"); t.setDaemon(true); return t; });
    private final Path stampFile = Path.of(System.getProperty("apklayer.storage", "/nonexistent")).getParent().resolve("media-scanned");
    private final long started = System.currentTimeMillis() - SLACK_MS;
    private long floor = Long.MAX_VALUE;  // the kept time goes no further (unwatched directories, dropped events)
    private ScheduledFuture<?> flush;

    HubMediaWatch(HubDirectory directory) {
        this.directory = directory;
        WatchService service = null;
        try { service = FileSystems.getDefault().newWatchService(); } catch (IOException none) { System.out.println("[apklayer-hub] media: no file watch: " + none); }
        watcher = service;
        if (watcher == null) return;
        Thread thread = new Thread(this::run, "hub-media-watch");
        thread.setDaemon(true);
        thread.start();
    }

    /** The time up to which every change was sent to the scanner, -1 before the instance's first one. */
    long stamp() {
        try { return Long.parseLong(Files.readString(stampFile).trim()); } catch (IOException | RuntimeException none) { return -1; }
    }

    /**
     * Watches the volume mounted at {@code host}, which apps see at {@code android}; with {@code since} >= 0 also has what changed since then
     * scanned (sent with the next flush, {@link #settle}).
     */
    synchronized void add(Path host, String android, long since) {
        if (watcher == null || roots.containsKey(host)) return;
        roots.put(host, android);
        register(host, -1, false);  // the top only
        for (String name : MEDIA) if (Files.isDirectory(host.resolve(name))) register(host.resolve(name), -1, true);
        register(host, since, true);
        status();
    }

    /** Stops watching the volume apps saw at {@code android} (it was unmounted). */
    synchronized void remove(String android) {
        roots.entrySet().removeIf(root -> root.getValue().equals(android));
        over.removeIf(dir -> roots.keySet().stream().noneMatch(dir::startsWith));
        dirs.entrySet().removeIf(dir -> {
            if (roots.keySet().stream().anyMatch(dir.getValue()::startsWith)) return false;
            dir.getKey().cancel();
            watching.remove(dir.getValue());
            return true;
        });
        status();
    }

    /** After the hub start's walks: sends what they found changed, else keeps this start's time. */
    synchronized void settle() {
        if (!due.isEmpty()) schedule();
        else keep(started);
    }

    /** Watches {@code top} (and below it when {@code deep}); with {@code since} >= 0 queues what changed since. */
    private void register(Path top, long since, boolean deep) {
        try {
            Files.walkFileTree(top, new SimpleFileVisitor<>() {
                private Path covered;  // a directory queued whole: its scan covers what is below it
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    String android = android(dir);
                    if (android == null) return FileVisitResult.SKIP_SUBTREE;
                    if (!watching.contains(dir) && !over.contains(dir)) watch(dir);
                    if (since >= 0 && covered == null && attributes.lastModifiedTime().toMillis() >= since) { due.add(android); covered = dir; }
                    return deep || dir.equals(top) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (since >= 0 && covered == null && attributes.isRegularFile() && attributes.lastModifiedTime().toMillis() >= since) due.add(android(file));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) {
                    if (dir.equals(covered)) covered = null;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException unreadable) { return FileVisitResult.CONTINUE; }
            });
        } catch (IOException unreadable) { System.out.println("[apklayer-hub] media: not watching " + top + ": " + unreadable); }
    }

    private void watch(Path dir) {
        String full = "budget " + budget;
        if (watching.size() < budget) {
            try {
                dirs.put(dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE), dir);
                watching.add(dir);
                return;
            } catch (IOException limit) { full = limit.toString(); }  // inotify's per-user limit
        }
        if (over.isEmpty()) System.out.println("[apklayer-hub] media: " + dir + " and what follows not watched (" + full + "): scanned at the next hub start or mount");
        over.add(dir);
        floor = Math.min(floor, System.currentTimeMillis() - SLACK_MS);
    }

    private void run() {
        try {
            while (true) {
                WatchKey key = watcher.take();
                synchronized (this) {
                    Path dir = dirs.get(key);
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) { floor = Math.min(floor, System.currentTimeMillis() - SLACK_MS); continue; }  // events lost: the next start's walk
                        if (dir == null) continue;
                        Path changed = dir.resolve((Path) event.context());
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed) && !Files.isSymbolicLink(changed)) { register(changed, -1, true); status(); }
                        String android = android(changed);
                        if (android != null) due.add(android);
                    }
                    if (!key.reset()) watching.remove(dirs.remove(key));
                    schedule();
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException ended) { /* the hub ends */ }
    }

    private void schedule() {
        if (flush != null) flush.cancel(false);
        flush = timer.schedule(this::send, QUIET_MS, TimeUnit.MILLISECONDS);
    }

    private String android(Path host) {
        for (Map.Entry<Path, String> root : roots.entrySet()) {
            if (!host.startsWith(root.getKey())) continue;
            Path relative = root.getKey().relativize(host);
            if (relative.toString().isEmpty()) return root.getValue();
            if (relative.getName(0).toString().equals("Android")) return null;
            return root.getValue() + "/" + relative;
        }
        return null;
    }

    private void send() {
        long sent = System.currentTimeMillis() - SLACK_MS;  // what changes from here on is in a later flush (or the next start's walk)
        Map<String, List<String>> byParent = new LinkedHashMap<>();
        synchronized (this) {
            for (String path : due) byParent.computeIfAbsent(new File(path).getParent(), parent -> new ArrayList<>()).add(path);
            due.clear();
        }
        // several changes in one directory are one scan of it (MediaProvider's default folders on a new drive, a copied folder): each scan is a work
        // item of MediaProvider's, and its process may start for each
        for (Map.Entry<String, List<String>> dir : byParent.entrySet())
            for (String path : dir.getValue().size() > 1 ? List.of(dir.getKey()) : dir.getValue())  // the scanner's own trigger, as an app's MediaScannerConnection.scanFile reaches the same scanFile
                directory.sendSystem(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(path))).addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND), false);
        keep(sent);
    }

    /** Keeps {@code time} (no later than the floor) as the time up to which every change was sent. */
    private synchronized void keep(long time) {
        try { Files.writeString(stampFile, Long.toString(Math.min(time, floor)) + "\n"); }
        catch (IOException | RuntimeException failure) { System.out.println("[apklayer-hub] media: cannot keep the scan time: " + failure); }
    }

    /** `apklayer status`'s line (cli/manage.cpp). */
    private void status() {
        try {
            Files.writeString(HubDirectory.LOGS.resolve("media-watch"), watching.size() + " directories watched, " + over.size() + " not (budget " + budget
                    + " of inotify's " + LIMIT + " per user; the rest is scanned at hub start)\n");
        } catch (IOException | RuntimeException ignored) { /* no state directory */ }
    }

    private static final int LIMIT = limit();

    private static int limit() {
        // readAllLines: Files.readString/readAllBytes of this procfs file gave its first byte only (JDK 21, Parallels: "6" for 65536)
        try { return Integer.parseInt(Files.readAllLines(Path.of("/proc/sys/fs/inotify/max_user_watches")).get(0).trim()); }
        catch (IOException | RuntimeException unknown) { System.out.println("[apklayer-hub] media: inotify's limit unknown: " + unknown); return 8192; }
    }

    private static int budget() {
        String set = System.getenv("APKLAYER_MEDIA_WATCHES");
        if (set != null) try { return Math.max(1, Integer.parseInt(set.trim())); } catch (NumberFormatException ignored) { /* the default */ }
        return Math.max(64, LIMIT / 4);
    }
}
