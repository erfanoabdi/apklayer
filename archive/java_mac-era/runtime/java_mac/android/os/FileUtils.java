// android.os.FileUtils -- utility helpers for files/streams. Several libraries
// call FileUtils.closeQuietly and FileUtils.copy. Implemented faithfully with
// real JDK I/O (no system dependency).
//
// AOSP ref: frameworks/base/core/java/android/os/FileUtils.java
package android.os;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class FileUtils {
    public static final int FLAG_MKDIRS = 1 << 0;

    private FileUtils() {}

    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {}
        }
    }

    public static void closeQuietly(FileDescriptor fd) {
        // AOSP delegates to IoUtils.closeQuietly(fd) -> Os.close(fd), i.e. it
        // really does close the descriptor; a no-op here leaked every fd a
        // caller handed us. Wrapping the fd in a FileInputStream and closing
        // that is the portable equivalent: FileInputStream.close() runs
        // FileDescriptor.closeAll(), which closes the underlying descriptor.
        // "Quietly" means every failure is swallowed, which is why the whole
        // thing sits in a catch-all.
        if (fd == null) return;
        try {
            new java.io.FileInputStream(fd).close();
        } catch (Throwable ignored) {}
    }

    /** AOSP core/java/android/os/FileUtils.java:154 -- "Listener that is
     *  called periodically as progress is made." */
    public interface ProgressListener {
        void onProgress(long progress);
    }

    // AOSP :134 -- report progress every 512 KiB.
    private static final long COPY_CHECKPOINT_BYTES = 524288;

    // Copy all bytes from in to out, returning the number of bytes copied.
    public static long copy(InputStream in, OutputStream out) throws IOException {
        long progress = 0;
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            progress += n;
        }
        return progress;
    }

    /** AOSP :398 -- the cancellable/observable stream copy. AOSP first tries a
     *  kernel-side splice/sendfile when both sides are real files and falls
     *  back to "copyInternalUserspace"; that optimisation is invisible to the
     *  caller (same bytes, same return, same callbacks), so this is the
     *  userspace path only, ported from AOSP :681 including the 512 KiB
     *  checkpoint cadence, the cancellation check AT each checkpoint, and the
     *  final unconditional progress callback. */
    public static long copy(InputStream in, OutputStream out, CancellationSignal signal,
                            java.util.concurrent.Executor executor, ProgressListener listener)
            throws IOException {
        long progress = 0;
        long checkpoint = 0;
        byte[] buffer = new byte[8192];
        int t;
        while ((t = in.read(buffer)) != -1) {
            out.write(buffer, 0, t);
            progress += t;
            checkpoint += t;
            if (checkpoint >= COPY_CHECKPOINT_BYTES) {
                if (signal != null) {
                    signal.throwIfCanceled();
                }
                if (executor != null && listener != null) {
                    final long snapshot = progress;
                    executor.execute(new Runnable() {
                        @Override public void run() { listener.onProgress(snapshot); }
                    });
                }
                checkpoint = 0;
            }
        }
        if (executor != null && listener != null) {
            final long snapshot = progress;
            executor.execute(new Runnable() {
                @Override public void run() { listener.onProgress(snapshot); }
            });
        }
        return progress;
    }

    /** AOSP :420 -- fd-to-fd copy. */
    public static long copy(FileDescriptor in, FileDescriptor out) throws IOException {
        return copy(in, out, null, null, null);
    }

    /** AOSP :436 -- fd-to-fd copy with cancellation + progress. Wrapping each
     *  fd in a stream is AOSP's own userspace fallback (:673). */
    public static long copy(FileDescriptor in, FileDescriptor out, CancellationSignal signal,
                            java.util.concurrent.Executor executor, ProgressListener listener)
            throws IOException {
        return copy(new java.io.FileInputStream(in), new java.io.FileOutputStream(out),
                    signal, executor, listener);
    }

    public static long copy(File from, File to) throws IOException {
        try (InputStream in = new java.io.FileInputStream(from);
             OutputStream out = new java.io.FileOutputStream(to)) {
            return copy(in, out);
        }
    }

    // Best-effort recursive delete used by several caching libs.
    public static boolean deleteContentsAndDir(File dir) {
        if (deleteContents(dir)) {
            return dir.delete();
        }
        return false;
    }

    public static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    success = false;
                }
            }
        }
        return success;
    }
}
