// android.os.FileObserver -- watches a path and delivers onEvent() callbacks
// when files change. Mac.apk has a real on-disk filesystem mirror
// (~/Documents/android/...), so we back this with java.nio.file.WatchService on
// a daemon thread rather than the inotify fd AOSP uses. We map the JDK watch
// kinds onto the Android event constants the app expects.
//
// Notes on fidelity:
//   - WatchService delivers ENTRY_CREATE/MODIFY/DELETE for the WATCHED
//     DIRECTORY's children. Android's MODIFY/CREATE/DELETE map cleanly. The
//     finer-grained events (ACCESS, OPEN, CLOSE_*, ATTRIB, MOVED_*) have no
//     JDK equivalent and never fire here; apps that only need create/modify/
//     delete (the common case: cache invalidation, media scanning) work.
//   - Watching a single file: the JDK watches directories, so we watch the
//     parent and filter to the target name.
//
// AOSP ref: frameworks/base/core/java/android/os/FileObserver.java
package android.os;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
public abstract class FileObserver {
    public static final int ACCESS = 0x00000001;
    public static final int MODIFY = 0x00000002;
    public static final int ATTRIB = 0x00000004;
    public static final int CLOSE_WRITE = 0x00000008;
    public static final int CLOSE_NOWRITE = 0x00000010;
    public static final int OPEN = 0x00000020;
    public static final int MOVED_FROM = 0x00000040;
    public static final int MOVED_TO = 0x00000080;
    public static final int CREATE = 0x00000100;
    public static final int DELETE = 0x00000200;
    public static final int DELETE_SELF = 0x00000400;
    public static final int MOVE_SELF = 0x00000800;
    public static final int ALL_EVENTS = 0x00000fff;

    private final List<File> mFiles;
    private final int mMask;

    private volatile WatchService mWatch;
    private volatile Thread mThread;
    private volatile boolean mRunning;

    public FileObserver(String path) { this(path, ALL_EVENTS); }

    public FileObserver(String path, int mask) {
        this(java.util.Collections.singletonList(new File(path)), mask);
    }

    public FileObserver(File file) { this(file, ALL_EVENTS); }

    public FileObserver(File file, int mask) {
        this(java.util.Collections.singletonList(file), mask);
    }

    // API 29+ multi-path constructors.
    public FileObserver(List<File> files) { this(files, ALL_EVENTS); }

    public FileObserver(List<File> files, int mask) {
        mFiles = (files == null) ? java.util.Collections.<File>emptyList() : files;
        mMask = (mask == 0) ? ALL_EVENTS : mask;
    }

    public abstract void onEvent(int event, String path);

    public synchronized void startWatching() {
        if (mRunning) return;
        try {
            mWatch = FileSystems.getDefault().newWatchService();
        } catch (Throwable t) {
            mWatch = null;
            return;
        }
        // Register the directory for each watched path. For a watched file, watch
        // its parent and remember the leaf name to filter against.
        final java.util.HashMap<WatchKey, String> keyFilter = new java.util.HashMap<>();
        for (File f : mFiles) {
            try {
                File dir = f.isDirectory() ? f : f.getParentFile();
                String leaf = f.isDirectory() ? null : f.getName();
                if (dir == null) continue;
                Path p = dir.toPath();
                WatchKey k = p.register(mWatch,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                keyFilter.put(k, leaf);
            } catch (Throwable ignored) {
                // A path that does not exist yet simply yields no events.
            }
        }
        mRunning = true;
        mThread = new Thread(() -> watchLoop(keyFilter), "FileObserver");
        mThread.setDaemon(true);
        mThread.start();
    }

    private void watchLoop(java.util.HashMap<WatchKey, String> keyFilter) {
        WatchService ws = mWatch;
        if (ws == null) return;
        while (mRunning) {
            WatchKey key;
            try {
                key = ws.take();
            } catch (Throwable t) {
                break; // closed or interrupted -> stop
            }
            String leafFilter = keyFilter.get(key);
            for (WatchEvent<?> ev : key.pollEvents()) {
                WatchEvent.Kind<?> kind = ev.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                Object ctx = ev.context();
                String name = (ctx == null) ? null : ctx.toString();
                if (leafFilter != null && !leafFilter.equals(name)) continue;

                int androidEvent;
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) androidEvent = CREATE;
                else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) androidEvent = MODIFY;
                else if (kind == StandardWatchEventKinds.ENTRY_DELETE) androidEvent = DELETE;
                else continue;

                if ((androidEvent & mMask) != 0) {
                    try { onEvent(androidEvent, name); } catch (Throwable ignored) {}
                }
            }
            boolean valid = key.reset();
            if (!valid) keyFilter.remove(key);
        }
    }

    public synchronized void stopWatching() {
        mRunning = false;
        WatchService ws = mWatch;
        mWatch = null;
        if (ws != null) {
            try { ws.close(); } catch (Throwable ignored) {}
        }
        Thread t = mThread;
        mThread = null;
        if (t != null) t.interrupt();
    }

    @Override protected void finalize() {
        try { stopWatching(); } catch (Throwable ignored) {}
    }
}
