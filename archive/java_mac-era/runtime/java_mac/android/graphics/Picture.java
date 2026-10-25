// Mac.apk -- android.graphics.Picture
//
// AOSP display-list recorder. On real Android, Picture records the
// drawing commands issued to the Canvas returned by beginRecording()
// into an SkPicture and replays them when draw(Canvas) is called.
//
// Two implementations, chosen by MACAPK_PICTURE_OPLIST (default ON):
//   OP LIST: the same recorder a RenderNode uses (MacApkDisplayList,
//     RecordingCanvas in op-list mode). draw() plays the ops onto the target
//     canvas under its CURRENT matrix and clip, so a picture drawn scaled
//     (drawPicture(picture, dst), an SVG PictureDrawable) rasterizes at the
//     target resolution instead of resampling a bitmap, and content outside
//     (0, 0, w, h) is not cut off -- SkPicture playback does not clip to the
//     cull rect. AOSP's SkRecordDraw wraps the playback in a save/restore
//     (SkAutoCanvasRestore), and so does draw().
//   BITMAP (=0, the pre-change path): rasterize into a backing Bitmap during
//     recording and blit that Bitmap on playback.
//
// Mirrors the public API surface of
// frameworks/base/graphics/java/android/graphics/Picture.java so
// callers' bytecode resolves. A missing beginRecording() throws
// NoSuchMethodError mid-frame, which is why the full surface lives
// here.
package android.graphics;

public class Picture {

    // The Bitmap that recording rasterizes into. Null until the first
    // beginRecording()/endRecording() cycle completes (or copied from
    // a source Picture by the copy constructor).
    private Bitmap recordedBitmap;

    // The Canvas handed out by beginRecording(), held so endRecording()
    // can detach its backing Bitmap. Null when not actively recording.
    private Canvas recordingCanvas;

    private int width;
    private int height;

    // OP-LIST mode: the committed list (immutable once committed, so a copy
    // can share it) and the recorder while a recording is open.
    private MacApkDisplayList recordedList;
    private RecordingCanvas opRecorder;

    public Picture() {
    }

    // Copy constructor. AOSP copies the native picture (its last FINISHED
    // recording); we share the recorded content -- the op list, or the raster --
    // plus dimensions, so a draw() of the copy reproduces the source. Neither is
    // ever mutated after it is committed.
    public Picture(Picture src) {
        if (src != null) {
            this.width = src.width;
            this.height = src.height;
            this.recordedBitmap = src.recordedBitmap;
            this.recordedList = src.recordedList;
        }
    }

    // Begins recording. Returns a Canvas whose draw calls land in a
    // backing Bitmap of the requested size. AOSP clamps nothing here,
    // but a zero/negative dimension would make Bitmap.createBitmap
    // throw, so we floor to 1 to keep the software path robust.
    public Canvas beginRecording(int width, int height) {
        this.width = width;
        this.height = height;
        if (MacApkDisplayList.PICTURE_OPLIST) {
            // No pixels: the ops are played at draw() time. The previous
            // recording stays drawable until endRecording replaces it.
            this.opRecorder = new RecordingCanvas(null, width, height, new MacApkDisplayList());
            this.recordingCanvas = this.opRecorder;
            return this.opRecorder;
        }
        this.recordedBitmap = Bitmap.createBitmap(
                Math.max(1, width), Math.max(1, height),
                Bitmap.Config.ARGB_8888);
        this.recordingCanvas = new Canvas(this.recordedBitmap);
        return this.recordingCanvas;
    }

    // Ends recording. After this the Picture is immutable and draw()
    // replays the captured raster. The recording Canvas is released so
    // late draws against it no longer mutate this Picture.
    public void endRecording() {
        if (opRecorder != null) {
            final MacApkDisplayList l = opRecorder.macapkFinishOpList();
            opRecorder = null;
            if (l != null) {
                recordedList = l;
                recordedBitmap = null;
            }
        }
        this.recordingCanvas = null;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    // Replays the recording onto the given Canvas. With the software
    // backing this is a single blit of the recorded Bitmap at the
    // origin (AOSP draws relative to the Canvas' current matrix, which
    // drawBitmap already honors).
    public void draw(Canvas canvas) {
        if (MacApkDisplayList.PICTURE_OPLIST) {
            // AOSP Picture.draw: "if (mRecordingCanvas != null) endRecording();"
            if (opRecorder != null) endRecording();
            final MacApkDisplayList l = recordedList;
            if (canvas != null && l != null) {
                final int sc = canvas.save();
                try { l.replay(canvas); }
                finally { canvas.restoreToCount(sc); }
                return;
            }
        }
        if (canvas != null && recordedBitmap != null) {
            canvas.drawBitmap(recordedBitmap, 0f, 0f, null);
        }
    }

    // We never use the hardware (GPU) Canvas path; recording is always
    // software-backed, so callers can safely replay onto any Canvas.
    public boolean requiresHardwareAcceleration() {
        return false;
    }

    // Deserialization stub. AOSP reconstructs the recorded op stream
    // from the stream's encoded form; we have no on-disk format, so we
    // return a fresh empty Picture. Returning null here would diverge
    // from the documented non-null contract.
    public static Picture createFromStream(java.io.InputStream stream) {
        return new Picture();
    }

    // Serialization stub. AOSP deprecated this and we have no encoded
    // form to emit, so it is a no-op. Kept so callers resolve.
    public void writeToStream(java.io.OutputStream stream) {
        // No-op: no serializable representation in the software path.
    }
}
