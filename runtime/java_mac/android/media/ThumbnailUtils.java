// android.media.ThumbnailUtils -- creates scaled thumbnails for images/videos.
// AOSP frameworks/base media/java/android/media/ThumbnailUtils.java.
//
// extractThumbnail (resize an in-memory bitmap) is fully serviceable via
// Bitmap.createScaledBitmap. createVideoThumbnail requires a video decoder we
// lack, so it returns null (AOSP also returns null when a frame cannot be
// decoded) -- callers fall back to a placeholder. createImageThumbnail decodes
// the file then scales it.
package android.media;

import android.graphics.Bitmap;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class ThumbnailUtils {
    public static final int OPTIONS_RECYCLE_INPUT = 0x2;

    /** AOSP declares a public no-arg constructor on this all-static class
     *  (android16.txt: "ctor public ThumbnailUtils();"), so an app that writes
     *  `new ThumbnailUtils()` -- and some do, via reflection or generated code
     *  -- still links. */
    public ThumbnailUtils() {}

    public static Bitmap extractThumbnail(Bitmap source, int width, int height) {
        if (source == null) return null;
        return Bitmap.createScaledBitmap(source, width, height, true);
    }
    public static Bitmap extractThumbnail(Bitmap source, int width, int height, int options) {
        Bitmap out = extractThumbnail(source, width, height);
        if ((options & OPTIONS_RECYCLE_INPUT) != 0 && source != out && source != null) {
            try { source.recycle(); } catch (Throwable ignore) {}
        }
        return out;
    }

    // No video decoder backend -> no thumbnail. The two overloads report that
    // DIFFERENTLY, and the difference is AOSP's, not ours: the deprecated
    // String/kind form is @Nullable and documents null as "failure", while the
    // File/Size form is @NonNull and documents IOException as "failure".
    // Returning null from the @NonNull one -- which this file used to do -- hands
    // the app an NPE somewhere downstream instead of the exception it already
    // has a catch block for.
    public static Bitmap createVideoThumbnail(String filePath, int kind) { return null; }

    public static Bitmap createVideoThumbnail(java.io.File file, android.util.Size size,
                                              android.os.CancellationSignal signal)
            throws java.io.IOException {
        throw new java.io.IOException("Failed to create thumbnail for " + file);
    }

    /**
     * API 29. An audio file's thumbnail is its EMBEDDED ALBUM ART -- AOSP reads
     * it with MediaMetadataRetriever.getEmbeddedPicture() and only falls back to
     * a folder-level image afterwards. The same path is taken here, so this
     * starts working the moment MediaMetadataRetriever grows real art
     * extraction (it currently answers null; see that file), with no change
     * needed at this call site.
     *
     * @NonNull in AOSP, so failure is an IOException rather than null.
     */
    public static Bitmap createAudioThumbnail(java.io.File file, android.util.Size size,
                                              android.os.CancellationSignal signal)
            throws java.io.IOException {
        java.util.Objects.requireNonNull(file, "file must not be null");
        java.util.Objects.requireNonNull(size, "size must not be null");
        Bitmap art = embeddedArt(file.getPath());
        if (art == null) {
            throw new java.io.IOException("Failed to create thumbnail for " + file);
        }
        return Bitmap.createScaledBitmap(art, size.getWidth(), size.getHeight(), true);
    }

    /** @deprecated in AOSP; @Nullable, so failure is null rather than an
     *  exception. kind is MICRO_KIND (1) or MINI_KIND (3), the same square
     *  dimensions createImageThumbnail uses above. */
    @Deprecated
    public static Bitmap createAudioThumbnail(String filePath, int kind) {
        Bitmap art = embeddedArt(filePath);
        if (art == null) return null;
        int dim = kind == 1 ? 96 : 512;
        try {
            return Bitmap.createScaledBitmap(art, dim, dim, true);
        } catch (Throwable t) { return null; }
    }

    private static Bitmap embeddedArt(String path) {
        if (path == null) return null;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            byte[] picture = mmr.getEmbeddedPicture();
            if (picture == null) return null;
            return android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.length);
        } catch (Throwable t) {
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) { }
        }
    }

    public static Bitmap createImageThumbnail(String filePath, int kind) {
        try {
            Bitmap full = android.graphics.BitmapFactory.decodeFile(filePath);
            if (full == null) return null;
            int dim = kind == 1 ? 96 : 512;  // MICRO_KIND=1, MINI_KIND=3
            return Bitmap.createScaledBitmap(full, dim, dim, true);
        } catch (Throwable t) { return null; }
    }
    /** @NonNull in AOSP: an undecodable file is an IOException, not null. */
    public static Bitmap createImageThumbnail(java.io.File file, android.util.Size size,
                                              android.os.CancellationSignal signal)
            throws java.io.IOException {
        java.util.Objects.requireNonNull(file, "file must not be null");
        java.util.Objects.requireNonNull(size, "size must not be null");
        Bitmap full;
        try {
            full = android.graphics.BitmapFactory.decodeFile(file.getPath());
        } catch (Throwable t) {
            throw new java.io.IOException("Failed to create thumbnail for " + file, t);
        }
        if (full == null) {
            throw new java.io.IOException("Failed to create thumbnail for " + file);
        }
        return Bitmap.createScaledBitmap(full, size.getWidth(), size.getHeight(), true);
    }
}
