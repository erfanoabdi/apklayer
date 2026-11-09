package apklayer.host;

import android.content.Intent;
import android.net.Uri;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * ModernMediaScanner.scanFile: a new or changed file is indexed, a gone one removed, a new directory scanned whole). A path is sent once it has
 * been quiet for QUIET_MS, so a file being copied is scanned when the copy is done. App-private Android/ is not watched (MediaProvider does not scan it).
 */
final class HubMediaWatch {
    private static final long QUIET_MS = 1500;
    private final HubDirectory directory;
    private final WatchService watcher;
    /** Watched directory (host path) -> the Android path of the volume root it is under, and that root's host path. */
    private final Map<WatchKey, Path> dirs = new HashMap<>();
    private final Map<Path, String> roots = new HashMap<>();
    private final Set<String> due = new LinkedHashSet<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "hub-media-scan"); t.setDaemon(true); return t; });
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

    /** Watches the volume mounted at {@code host}, which apps see at {@code android}. */
    synchronized void add(Path host, String android) {
        if (watcher == null || roots.containsKey(host)) return;
        roots.put(host, android);
        register(host);
    }

    /** Stops watching the volume apps saw at {@code android} (it was unmounted). */
    synchronized void remove(String android) {
        roots.entrySet().removeIf(root -> root.getValue().equals(android));
        dirs.entrySet().removeIf(dir -> {
            if (roots.keySet().stream().anyMatch(dir.getValue()::startsWith)) return false;
            dir.getKey().cancel();
            return true;
        });
    }

    private void register(Path top) {
        try {
            Files.walkFileTree(top, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    if (dir.getParent() != null && roots.containsKey(dir.getParent()) && dir.getFileName().toString().equals("Android")) return FileVisitResult.SKIP_SUBTREE;
                    try { dirs.put(dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE), dir); }
                    catch (IOException full) { System.out.println("[apklayer-hub] media: not watching " + dir + ": " + full); return FileVisitResult.TERMINATE; }  // inotify's watch limit
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException unreadable) { return FileVisitResult.CONTINUE; }
            });
        } catch (IOException unreadable) { System.out.println("[apklayer-hub] media: not watching " + top + ": " + unreadable); }
    }

    private void run() {
        try {
            while (true) {
                WatchKey key = watcher.take();
                synchronized (this) {
                    Path dir = dirs.get(key);
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (dir == null || event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                        Path changed = dir.resolve((Path) event.context());
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed) && !Files.isSymbolicLink(changed)) register(changed);
                        String android = android(changed);
                        if (android != null) due.add(android);
                    }
                    if (!key.reset()) dirs.remove(key);
                    if (flush != null) flush.cancel(false);
                    flush = timer.schedule(this::send, QUIET_MS, TimeUnit.MILLISECONDS);
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException ended) { /* the hub ends */ }
    }

    private String android(Path host) {
        for (Map.Entry<Path, String> root : roots.entrySet()) {
            if (!host.startsWith(root.getKey())) continue;
            Path relative = root.getKey().relativize(host);
            if (relative.getNameCount() > 0 && relative.getName(0).toString().equals("Android")) return null;
            return root.getValue() + "/" + relative;
        }
        return null;
    }

    private void send() {
        Map<String, java.util.List<String>> byParent = new java.util.LinkedHashMap<>();
        synchronized (this) {
            for (String path : due) byParent.computeIfAbsent(new java.io.File(path).getParent(), parent -> new java.util.ArrayList<>()).add(path);
            due.clear();
        }
        // several changes in one directory are one scan of it (MediaProvider's default folders on a new drive, a copied folder): each scan is a work
        // item of MediaProvider's, and its process may start for each
        for (Map.Entry<String, java.util.List<String>> dir : byParent.entrySet())
            for (String path : dir.getValue().size() > 1 ? java.util.List.of(dir.getKey()) : dir.getValue())  // the scanner's own trigger, as an app's MediaScannerConnection.scanFile reaches the same scanFile
                directory.sendSystem(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new java.io.File(path))).addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND), false);
    }
}
