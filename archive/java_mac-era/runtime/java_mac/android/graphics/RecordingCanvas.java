// android.graphics.RecordingCanvas -- the Canvas subclass returned by
// RenderNode.beginRecording(). In AOSP this is a hardware display-list
// recording canvas (DisplayListCanvas); draw ops issued to it are
// captured into the RenderNode's display list and replayed later.
//
// It MUST extend Canvas: Compose / androidx layer code does
// `RecordingCanvas c = renderNode.beginRecording(); drawBlock(c);` and
// passes it everywhere a Canvas is expected. isHardwareAccelerated()
// returns true to match AOSP -- this keeps the record/replay chain
// consistent (a child RenderNode drawn into a recording canvas takes
// the drawRenderNode path).
//
// TWO MODES, fixed at construction:
//
//   OP LIST (MACAPK_RENDERNODE_OPLIST, default ON; MacApkDisplayList). Every
//   public Canvas method is overridden here. A pixel method RECORDS one op and
//   draws nothing; a state method (save/restore/transform/clip) records one op
//   AND runs on Canvas's own state machinery, so every query the content makes
//   (getClipBounds, quickReject, getMatrix, getSaveCount, getWidth...) is
//   answered by the very code that answers it on a bitmap-backed canvas of the
//   node's size -- exactly what today's recording canvas answered, in node
//   space, as AOSP's does. The backing is MacApkDisplayList.stateBitmap: w x h
//   to every query, one row of pixels in memory. Queries are NOT overridden.
//
//   BITMAP (the MACAPK_RENDERNODE_OPLIST=0 path, and the per-node fallback):
//   every override is a plain super call, so this is the pre-change class: the
//   content rasterizes into the node's offscreen bitmap.
//
// debug/displaylist_gate.sh's API-shape row enumerates every public and
// protected Canvas method and fails if one is neither overridden here nor on
// its query/config allowlist, because an un-overridden PIXEL method in op-list
// mode would draw into the state backing and vanish. The tripwire in
// macapkFinishOpList catches the same thing at run time and logs it.
package android.graphics;

import android.graphics.MacApkDisplayList.Op;

public class RecordingCanvas extends Canvas {
    /** The RenderNode whose content is being recorded into this canvas
     *  (null for a Picture's recording).
     *
     *  In BITMAP mode our replay COPIES PIXELS while AOSP's stores a
     *  reference. When a child node is drawn into this canvas, its pixels are
     *  flattened into `owner`'s backing bitmap there and then, so `owner`'s
     *  recording goes stale the moment that child re-records. Knowing who owns
     *  the destination canvas is what lets drawRenderNode wire up the
     *  child->parent link that RenderNode.beginRecording uses to say so. */
    private RenderNode macapkOwner;

    /** Non-null in OP-LIST mode: the list being recorded into. Set AFTER the
     *  super constructor returns, so Canvas's constructor-time setBitmap takes
     *  the bitmap path and installs the state backing. */
    private MacApkDisplayList rec;
    /** > 0 while a state op runs on super: Canvas's own overloads re-enter
     *  virtual methods (restoreToCount -> restore, clipRect(Rect) ->
     *  clipRect(float...)), which must update state without recording twice. */
    private int suppress;
    /** Tripwire baselines: an inherited pixel method would move these. */
    private int tripDrawOps;
    private Bitmap stateBitmap;
    private int tripGen;

    public RecordingCanvas() { super(); }
    public RecordingCanvas(Bitmap b) { super(b); }
    RecordingCanvas(Bitmap b, RenderNode owner) { super(b); this.macapkOwner = owner; }

    /** OP-LIST mode: record into `list`; queries answer for a w x h canvas. */
    RecordingCanvas(RenderNode owner, int w, int h, MacApkDisplayList list) {
        this(MacApkDisplayList.stateBitmap(Math.max(1, w), Math.max(1, h)), owner, list);
    }
    private RecordingCanvas(Bitmap state, RenderNode owner, MacApkDisplayList list) {
        super(state);
        this.macapkOwner = owner;
        this.stateBitmap = state;
        this.tripDrawOps = macapkDrawOps;
        this.tripGen = state.getGenerationId();
        this.rec = list;
    }

    /** Package hook for Canvas.drawRenderNode. */
    RenderNode macapkOwner() { return macapkOwner; }
    /** True for an op-list recording, finished or not. */
    boolean macapkIsOpList() { return rec != null; }

    /** End an op-list recording: seal the list (a late draw on this canvas,
     *  which AOSP forbids after endRecording, can no longer reach it) and run
     *  the tripwire. Returns the list, or null in bitmap mode. */
    MacApkDisplayList macapkFinishOpList() {
        final MacApkDisplayList l = rec;
        if (l == null || l == MacApkDisplayList.SEALED) return null;
        rec = MacApkDisplayList.SEALED;
        l.seal();
        final boolean moved = macapkDrawOps != tripDrawOps
                || (stateBitmap != null && stateBitmap.getGenerationId() != tripGen);
        if (moved) {
            l.unrecorded = true;
            if (!sTripLogged) {
                sTripLogged = true;
                try {
                    com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
                        "[dl-unrecorded] a pixel op reached a display-list recording without an "
                        + "override (inkDelta=" + (macapkDrawOps - tripDrawOps) + "); the node "
                        + "falls back to a bitmap recording. RecordingCanvas is missing an override.");
                } catch (Throwable ignored) { }
            }
        }
        return l;
    }
    private static volatile boolean sTripLogged;

    // ---- Recording helpers -------------------------------------------------
    private boolean recording() { return rec != null && suppress == 0; }
    private void op(String method, Op o) { rec.add(method, o, false); }
    private void draw(String method, Op o) { rec.add(method, o, true); }
    /** A pixel op with a Paint: the snapshot, and the call to make with it. */
    private void draw(String method, Paint p, MacApkDisplayList.PDraw d) {
        final MacApkDisplayList.PaintSnap ps = new MacApkDisplayList.PaintSnap(p);
        rec.add(method, (c, x) -> ps.run(c, x, d), true);
    }

    // ---- Canvas management ---------------------------------------------------
    @Override public boolean isHardwareAccelerated() { return true; }

    /** AOSP RecordingCanvas.setBitmap throws UnsupportedOperationException: a
     *  display list has no pixels to retarget. Bitmap mode keeps the pre-change
     *  behaviour (a plain Canvas retarget). */
    @Override public void setBitmap(Bitmap bitmap) {
        if (rec != null) throw new UnsupportedOperationException();
        super.setBitmap(bitmap);
    }
    /** AOSP RecordingCanvas.setDensity: "drop silently". Ours was already a no-op. */
    @Override public void setDensity(int density) { super.setDensity(density); }
    @Override public void setScreenDensity(int density) { super.setScreenDensity(density); }
    /** Stored, never read by any draw path of ours, so nothing to record. */
    @Override public void setDrawFilter(DrawFilter filter) { super.setDrawFilter(filter); }
    /** No Z reordering or shadows on our canvas in either mode (see Canvas). */
    @Override public void enableZ() { super.enableZ(); }
    @Override public void disableZ() { super.disableZ(); }

    // ---- Save / restore ------------------------------------------------------
    @Override public int save() {
        if (!recording()) return super.save();
        op("save", (c, x) -> c.save());
        suppress++; try { return super.save(); } finally { suppress--; }
    }
    @Override public int save(int saveFlags) {
        if (!recording()) return super.save(saveFlags);
        op("save", (c, x) -> c.save(saveFlags));
        suppress++; try { return super.save(saveFlags); } finally { suppress--; }
    }
    @Override public void restore() {
        if (!recording()) { super.restore(); return; }
        // Canvas.restore() pops only when something is saved; an unbalanced
        // restore is a no-op here and must stay one at replay, where popping
        // would eat the replay's own node setup.
        if (getSaveCount() > 1) op("restore", (c, x) -> c.restore());
        suppress++; try { super.restore(); } finally { suppress--; }
    }
    @Override public void restoreToCount(int saveCount) {
        if (!recording()) { super.restoreToCount(saveCount); return; }
        // Canvas returns for a count < 1 (AOSP calls it illegal). A count is in
        // this recording's own space; the replay rebases it onto the target.
        if (saveCount >= 1) op("restoreToCount", (c, x) -> c.restoreToCount(x.rebase(saveCount)));
        suppress++; try { super.restoreToCount(saveCount); } finally { suppress--; }
    }

    // saveLayer: recorded as the same call; the STATE half is emulated without
    // the offscreen. Canvas.pushLayer, as far as any query can see, is a save
    // plus, when a real layer is made AND the bounds are non-empty, a clip to
    // the bounds (lg.clip(bounds)); the layer image itself only matters to
    // pixels, and no pixel is drawn here.
    //  - saveLayerAlpha: a real layer iff the clamped alpha is below 255.
    //  - saveLayer(paint): with non-empty bounds it is ALWAYS a real layer
    //    (forceLayer); with empty bounds it never clips.
    // So the clip is applied iff the bounds are non-empty and (saveLayer, or
    // saveLayerAlpha with alpha < 255).
    //
    // The op's KIND says whether our Canvas will make a REAL offscreen for it
    // (K_LAYER) or a plain save (K_STATE): pushLayer degenerates to save()
    // exactly when alpha is 255, the rule is SRC_OVER and the layer is not
    // forced. Non-empty bounds OR a color filter force saveLayer offscreen,
    // including a full-alpha, bounds-less filtered group. The hole pre-pass
    // needs the difference: a hole inside a real layer stays in it.
    private void layerOp(String method, MacApkDisplayList.Op o, boolean real) {
        rec.add(method, o, real ? MacApkDisplayList.K_LAYER : MacApkDisplayList.K_STATE);
    }
    private static boolean realAlphaLayer(int alpha) { return Math.max(0, Math.min(255, alpha)) < 255; }
    private static boolean realLayer(float l, float t, float r, float b, Paint p) {
        return (r > l && b > t) || (p != null && (p.getAlpha() < 255 || p.getColorFilter() != null));
    }
    private int layerState(float l, float t, float r, float b, boolean clips) {
        suppress++;
        try {
            final int sc = super.save();
            if (clips && r > l && b > t) super.clipRect(l, t, r, b);
            return sc;
        } finally { suppress--; }
    }
    @Override public int saveLayerAlpha(float l, float t, float r, float b, int alpha) {
        if (!recording()) return super.saveLayerAlpha(l, t, r, b, alpha);
        layerOp("saveLayerAlpha", (c, x) -> c.saveLayerAlpha(l, t, r, b, alpha), realAlphaLayer(alpha));
        return layerState(l, t, r, b, Math.max(0, Math.min(255, alpha)) < 255);
    }
    @Override public int saveLayerAlpha(RectF bounds, int alpha) {
        if (!recording()) return super.saveLayerAlpha(bounds, alpha);
        final RectF k = MacApkDisplayList.snap(bounds);
        layerOp("saveLayerAlpha", (c, x) -> c.saveLayerAlpha(k, alpha), realAlphaLayer(alpha));
        return k == null ? layerState(0, 0, 0, 0, false)
             : layerState(k.left, k.top, k.right, k.bottom, Math.max(0, Math.min(255, alpha)) < 255);
    }
    @Override public int saveLayerAlpha(float l, float t, float r, float b, int alpha, int saveFlags) {
        if (!recording()) return super.saveLayerAlpha(l, t, r, b, alpha, saveFlags);
        layerOp("saveLayerAlpha", (c, x) -> c.saveLayerAlpha(l, t, r, b, alpha, saveFlags), realAlphaLayer(alpha));
        return layerState(l, t, r, b, Math.max(0, Math.min(255, alpha)) < 255);
    }
    @Override public int saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
        if (!recording()) return super.saveLayerAlpha(bounds, alpha, saveFlags);
        final RectF k = MacApkDisplayList.snap(bounds);
        layerOp("saveLayerAlpha", (c, x) -> c.saveLayerAlpha(k, alpha, saveFlags), realAlphaLayer(alpha));
        return k == null ? layerState(0, 0, 0, 0, false)
             : layerState(k.left, k.top, k.right, k.bottom, Math.max(0, Math.min(255, alpha)) < 255);
    }
    @Override public int saveLayer(float l, float t, float r, float b, Paint paint) {
        if (!recording()) return super.saveLayer(l, t, r, b, paint);
        final Paint p = MacApkDisplayList.snap(paint);
        layerOp("saveLayer", (c, x) -> c.saveLayer(l, t, r, b, p), realLayer(l, t, r, b, p));
        return layerState(l, t, r, b, true);
    }
    @Override public int saveLayer(RectF bounds, Paint paint) {
        if (!recording()) return super.saveLayer(bounds, paint);
        final RectF k = MacApkDisplayList.snap(bounds);
        final Paint p = MacApkDisplayList.snap(paint);
        layerOp("saveLayer", (c, x) -> c.saveLayer(k, p),
                k == null ? realLayer(0, 0, 0, 0, p) : realLayer(k.left, k.top, k.right, k.bottom, p));
        return k == null ? layerState(0, 0, 0, 0, false) : layerState(k.left, k.top, k.right, k.bottom, true);
    }
    @Override public int saveLayer(float l, float t, float r, float b, Paint paint, int saveFlags) {
        if (!recording()) return super.saveLayer(l, t, r, b, paint, saveFlags);
        final Paint p = MacApkDisplayList.snap(paint);
        layerOp("saveLayer", (c, x) -> c.saveLayer(l, t, r, b, p, saveFlags), realLayer(l, t, r, b, p));
        return layerState(l, t, r, b, true);
    }
    @Override public int saveLayer(RectF bounds, Paint paint, int saveFlags) {
        if (!recording()) return super.saveLayer(bounds, paint, saveFlags);
        final RectF k = MacApkDisplayList.snap(bounds);
        final Paint p = MacApkDisplayList.snap(paint);
        layerOp("saveLayer", (c, x) -> c.saveLayer(k, p, saveFlags),
                k == null ? realLayer(0, 0, 0, 0, p) : realLayer(k.left, k.top, k.right, k.bottom, p));
        return k == null ? layerState(0, 0, 0, 0, false) : layerState(k.left, k.top, k.right, k.bottom, true);
    }

    // ---- Transforms ----------------------------------------------------------
    @Override public void translate(float dx, float dy) {
        if (!recording()) { super.translate(dx, dy); return; }
        op("translate", (c, x) -> c.translate(dx, dy));
        suppress++; try { super.translate(dx, dy); } finally { suppress--; }
    }
    @Override public void scale(float sx, float sy) {
        if (!recording()) { super.scale(sx, sy); return; }
        op("scale", (c, x) -> c.scale(sx, sy));
        suppress++; try { super.scale(sx, sy); } finally { suppress--; }
    }
    @Override public void scale(float sx, float sy, float px, float py) {
        if (!recording()) { super.scale(sx, sy, px, py); return; }
        op("scale", (c, x) -> c.scale(sx, sy, px, py));
        suppress++; try { super.scale(sx, sy, px, py); } finally { suppress--; }
    }
    @Override public void rotate(float degrees) {
        if (!recording()) { super.rotate(degrees); return; }
        op("rotate", (c, x) -> c.rotate(degrees));
        suppress++; try { super.rotate(degrees); } finally { suppress--; }
    }
    @Override public void rotate(float degrees, float px, float py) {
        if (!recording()) { super.rotate(degrees, px, py); return; }
        op("rotate", (c, x) -> c.rotate(degrees, px, py));
        suppress++; try { super.rotate(degrees, px, py); } finally { suppress--; }
    }
    @Override public void skew(float sx, float sy) {
        if (!recording()) { super.skew(sx, sy); return; }
        op("skew", (c, x) -> c.skew(sx, sy));
        suppress++; try { super.skew(sx, sy); } finally { suppress--; }
    }
    @Override public void concat(Matrix matrix) {
        if (!recording()) { super.concat(matrix); return; }
        final Matrix m = MacApkDisplayList.snap(matrix);
        op("concat", (c, x) -> c.concat(m));
        suppress++; try { super.concat(matrix); } finally { suppress--; }
    }
    /** Replayed RELATIVE to the matrix the replay STARTED with, as hwui's
     *  DisplayListData SetMatrix::draw does (`c->setMatrix(SkM44(original) *
     *  matrix)`, `original` being the target's total matrix before the list's
     *  first op). That is also what the pre-change path produced: the matrix was
     *  absolute in the node's OWN bitmap, whose origin is the node's position.
     *  noteSetMatrix makes replay() capture that start matrix up front; reading
     *  it when this op runs would include the list's own earlier translate /
     *  concat (the bug the gate's C3 control re-creates). A null matrix is a
     *  no-op on our Canvas, and stays one. */
    @Override public void setMatrix(Matrix matrix) {
        if (!recording()) { super.setMatrix(matrix); return; }
        if (matrix != null) {
            final Matrix m = MacApkDisplayList.snap(matrix);
            rec.noteSetMatrix();
            op("setMatrix", (c, x) -> {
                final Matrix abs = new Matrix();
                abs.setConcat(x.initialMatrix(), m);
                c.setMatrix(abs);
            });
        }
        suppress++; try { super.setMatrix(matrix); } finally { suppress--; }
    }

    // ---- Clips ---------------------------------------------------------------
    @Override public boolean clipRect(float l, float t, float r, float b) {
        if (!recording()) return super.clipRect(l, t, r, b);
        op("clipRect", (c, x) -> c.clipRect(l, t, r, b));
        suppress++; try { return super.clipRect(l, t, r, b); } finally { suppress--; }
    }
    @Override public boolean clipRect(Rect rect) {
        if (!recording()) return super.clipRect(rect);
        final Rect k = MacApkDisplayList.snap(rect);
        op("clipRect", (c, x) -> c.clipRect(k));
        suppress++; try { return super.clipRect(rect); } finally { suppress--; }
    }
    @Override public boolean clipRect(RectF rect) {
        if (!recording()) return super.clipRect(rect);
        final RectF k = MacApkDisplayList.snap(rect);
        op("clipRect", (c, x) -> c.clipRect(k));
        suppress++; try { return super.clipRect(rect); } finally { suppress--; }
    }
    @Override public boolean clipRect(int l, int t, int r, int b) {
        if (!recording()) return super.clipRect(l, t, r, b);
        op("clipRect", (c, x) -> c.clipRect(l, t, r, b));
        suppress++; try { return super.clipRect(l, t, r, b); } finally { suppress--; }
    }
    @Override public boolean clipRect(float l, float t, float r, float b, Region.Op rop) {
        if (!recording()) return super.clipRect(l, t, r, b, rop);
        op("clipRect", (c, x) -> c.clipRect(l, t, r, b, rop));
        suppress++; try { return super.clipRect(l, t, r, b, rop); } finally { suppress--; }
    }
    @Override public boolean clipRect(int l, int t, int r, int b, Region.Op rop) {
        if (!recording()) return super.clipRect(l, t, r, b, rop);
        op("clipRect", (c, x) -> c.clipRect(l, t, r, b, rop));
        suppress++; try { return super.clipRect(l, t, r, b, rop); } finally { suppress--; }
    }
    @Override public boolean clipRect(Rect rect, Region.Op rop) {
        if (!recording()) return super.clipRect(rect, rop);
        final Rect k = MacApkDisplayList.snap(rect);
        op("clipRect", (c, x) -> c.clipRect(k, rop));
        suppress++; try { return super.clipRect(rect, rop); } finally { suppress--; }
    }
    @Override public boolean clipRect(RectF rect, Region.Op rop) {
        if (!recording()) return super.clipRect(rect, rop);
        final RectF k = MacApkDisplayList.snap(rect);
        op("clipRect", (c, x) -> c.clipRect(k, rop));
        suppress++; try { return super.clipRect(rect, rop); } finally { suppress--; }
    }
    @Override public boolean clipPath(Path path, Region.Op rop) {
        if (!recording()) return super.clipPath(path, rop);
        final Path k = MacApkDisplayList.snap(path);
        op("clipPath", (c, x) -> c.clipPath(k, rop));
        suppress++; try { return super.clipPath(path, rop); } finally { suppress--; }
    }
    @Override public boolean clipPath(Path path) {
        if (!recording()) return super.clipPath(path);
        final Path k = MacApkDisplayList.snap(path);
        op("clipPath", (c, x) -> c.clipPath(k));
        suppress++; try { return super.clipPath(path); } finally { suppress--; }
    }
    @Override public boolean clipOutRect(float l, float t, float r, float b) {
        if (!recording()) return super.clipOutRect(l, t, r, b);
        op("clipOutRect", (c, x) -> c.clipOutRect(l, t, r, b));
        suppress++; try { return super.clipOutRect(l, t, r, b); } finally { suppress--; }
    }
    @Override public boolean clipOutRect(int l, int t, int r, int b) {
        if (!recording()) return super.clipOutRect(l, t, r, b);
        op("clipOutRect", (c, x) -> c.clipOutRect(l, t, r, b));
        suppress++; try { return super.clipOutRect(l, t, r, b); } finally { suppress--; }
    }
    @Override public boolean clipOutRect(Rect rect) {
        if (!recording()) return super.clipOutRect(rect);
        final Rect k = MacApkDisplayList.snap(rect);
        op("clipOutRect", (c, x) -> c.clipOutRect(k));
        suppress++; try { return super.clipOutRect(rect); } finally { suppress--; }
    }
    @Override public boolean clipOutRect(RectF rect) {
        if (!recording()) return super.clipOutRect(rect);
        final RectF k = MacApkDisplayList.snap(rect);
        op("clipOutRect", (c, x) -> c.clipOutRect(k));
        suppress++; try { return super.clipOutRect(rect); } finally { suppress--; }
    }
    @Override public boolean clipOutPath(Path path) {
        if (!recording()) return super.clipOutPath(path);
        final Path k = MacApkDisplayList.snap(path);
        op("clipOutPath", (c, x) -> c.clipOutPath(k));
        suppress++; try { return super.clipOutPath(path); } finally { suppress--; }
    }
    @Override public boolean clipRegion(Region region) {
        if (!recording()) return super.clipRegion(region);
        final Region k = MacApkDisplayList.snap(region);
        op("clipRegion", (c, x) -> c.clipRegion(k));
        suppress++; try { return super.clipRegion(region); } finally { suppress--; }
    }
    @Override public boolean clipRegion(Region region, Region.Op rop) {
        if (!recording()) return super.clipRegion(region, rop);
        final Region k = MacApkDisplayList.snap(region);
        op("clipRegion", (c, x) -> c.clipRegion(k, rop));
        suppress++; try { return super.clipRegion(region, rop); } finally { suppress--; }
    }
    @Override public void clipShader(Shader shader) {
        if (!recording()) { super.clipShader(shader); return; }
        suppress++; try { super.clipShader(shader); } finally { suppress--; }   // throws the NPE first
        op("clipShader", (c, x) -> c.clipShader(shader));
    }
    @Override public void clipOutShader(Shader shader) {
        if (!recording()) { super.clipOutShader(shader); return; }
        suppress++; try { super.clipOutShader(shader); } finally { suppress--; }
        op("clipOutShader", (c, x) -> c.clipOutShader(shader));
    }
    @Override public boolean macapkClipChildRect(int l, int t, int r, int b) {
        if (!recording()) return super.macapkClipChildRect(l, t, r, b);
        op("macapkClipChildRect", (c, x) -> c.macapkClipChildRect(l, t, r, b));
        suppress++; try { return super.macapkClipChildRect(l, t, r, b); } finally { suppress--; }
    }

    // ---- Fills ---------------------------------------------------------------
    // drawColor(int) and the overloads that reach it (drawARGB, drawRGB,
    // drawColor(long)) are replayed as drawColor(color, SRC_OVER). AOSP's
    // drawColor(int) IS SrcOver (BaseCanvas.drawColor -> nDrawColor(...,
    // BlendMode.SRC_OVER); Canvas.drawColor javadoc "using srcover porterduff
    // mode"). Our Canvas.drawColor(int) is deliberately SRC for the frame
    // canvas's own clear callers (see the note on it there); none of those ever
    // draws into a recording, which only ever holds app content. And SRC_OVER is
    // also what the pre-change path produced for this content: a drawColor into
    // the node's transparent offscreen, composited back SrcOver. Replaying SRC
    // instead would erase the FRAME under a drawColor(0) that a device, and the
    // old path, both treat as a no-op.
    @Override public void drawColor(int color) {
        if (rec == null) { super.drawColor(color); return; }
        draw("drawColor", (c, x) -> c.drawColor(color, PorterDuff.Mode.SRC_OVER));
    }
    @Override public void drawARGB(int a, int r, int gg, int b) {
        if (rec == null) { super.drawARGB(a, r, gg, b); return; }
        final int color = ((a & 0xff) << 24) | ((r & 0xff) << 16) | ((gg & 0xff) << 8) | (b & 0xff);
        draw("drawARGB", (c, x) -> c.drawColor(color, PorterDuff.Mode.SRC_OVER));
    }
    @Override public void drawRGB(int r, int gg, int b) {
        if (rec == null) { super.drawRGB(r, gg, b); return; }
        final int color = 0xFF000000 | ((r & 0xff) << 16) | ((gg & 0xff) << 8) | (b & 0xff);
        draw("drawRGB", (c, x) -> c.drawColor(color, PorterDuff.Mode.SRC_OVER));
    }
    @Override public void drawColor(long color) {
        if (rec == null) { super.drawColor(color); return; }
        // Canvas.drawColor(long) is drawColor(int) of the sRGB high word.
        final int argb = (int) (color >>> 32);
        draw("drawColor", (c, x) -> c.drawColor(argb, PorterDuff.Mode.SRC_OVER));
    }
    @Override public void drawColor(int color, PorterDuff.Mode mode) {
        if (rec == null) { super.drawColor(color, mode); return; }
        draw("drawColor", mode == null
            ? (Op) (c, x) -> c.drawColor(color, PorterDuff.Mode.SRC_OVER)   // null mode -> drawColor(int)
            : (Op) (c, x) -> c.drawColor(color, mode));
    }
    @Override public void drawColor(int color, BlendMode mode) {
        if (rec == null) { super.drawColor(color, mode); return; }
        draw("drawColor", mode == null
            ? (Op) (c, x) -> c.drawColor(color, PorterDuff.Mode.SRC_OVER)
            : (Op) (c, x) -> c.drawColor(color, mode));
    }
    @Override public void drawColor(long color, BlendMode mode) {
        if (rec == null) { super.drawColor(color, mode); return; }
        final int argb = (int) (color >>> 32);
        draw("drawColor", mode == null
            ? (Op) (c, x) -> c.drawColor(argb, PorterDuff.Mode.SRC_OVER)
            : (Op) (c, x) -> c.drawColor(color, mode));
    }
    @Override public void drawColor(long color, PorterDuff.Mode mode) {
        if (rec == null) { super.drawColor(color, mode); return; }
        final int argb = (int) (color >>> 32);
        draw("drawColor", mode == null
            ? (Op) (c, x) -> c.drawColor(argb, PorterDuff.Mode.SRC_OVER)
            : (Op) (c, x) -> c.drawColor(color, mode));
    }
    @Override public void drawPaint(Paint p) {
        if (rec == null) { super.drawPaint(p); return; }
        if (p == null) return;
        draw("drawPaint", p, (c, q) -> c.drawPaint(q));
    }

    // ---- Shapes --------------------------------------------------------------
    @Override public void drawRect(float l, float t, float r, float b, Paint p) {
        if (rec == null) { super.drawRect(l, t, r, b, p); return; }
        draw("drawRect", p, (c, q) -> c.drawRect(l, t, r, b, q));
    }
    @Override public void drawRect(Rect r, Paint p) {
        if (rec == null) { super.drawRect(r, p); return; }
        final Rect k = MacApkDisplayList.snap(r);
        draw("drawRect", p, (c, q) -> c.drawRect(k, q));
    }
    @Override public void drawRect(RectF r, Paint p) {
        if (rec == null) { super.drawRect(r, p); return; }
        final RectF k = MacApkDisplayList.snap(r);
        draw("drawRect", p, (c, q) -> c.drawRect(k, q));
    }
    @Override public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        if (rec == null) { super.drawLine(x0, y0, x1, y1, p); return; }
        draw("drawLine", p, (c, q) -> c.drawLine(x0, y0, x1, y1, q));
    }
    // The (array, offset, count) ops snapshot only the range the call reads,
    // as hwui copies only it, validated AT THE CALL with AOSP's own check
    // (BaseRecordingCanvas android14 and, for lines and points, its JNI
    // android_graphics_Canvas.cpp:285-321), so a bad range throws in the app's
    // draw as on a device and as the pre-change recording did, not in every
    // later frame's replay. Our immediate Canvas is more lenient on a few of
    // these (it clamps lines and points and truncates drawPosText's
    // positions); the recorder follows AOSP, whose software canvas throws too.
    @Override public void drawLines(float[] pts, int offset, int count, Paint p) {
        if (rec == null) { super.drawLines(pts, offset, count, p); return; }
        if (pts == null) throw new NullPointerException();    // NPE_CHECK_RETURN_VOID
        if ((offset | count) < 0 || offset + count > pts.length) throw new ArrayIndexOutOfBoundsException();
        final float[] k = MacApkDisplayList.range(pts, offset, offset + count);
        final int ko = (k == pts) ? offset : 0;
        draw("drawLines", p, (c, q) -> c.drawLines(k, ko, count, q));
    }
    @Override public void drawLines(float[] pts, Paint p) {
        if (rec == null) { super.drawLines(pts, p); return; }
        final float[] k = MacApkDisplayList.snap(pts);
        draw("drawLines", p, (c, q) -> c.drawLines(k, q));
    }
    @Override public void drawCircle(float cx, float cy, float radius, Paint p) {
        if (rec == null) { super.drawCircle(cx, cy, radius, p); return; }
        draw("drawCircle", p, (c, q) -> c.drawCircle(cx, cy, radius, q));
    }
    @Override public void drawOval(RectF oval, Paint p) {
        if (rec == null) { super.drawOval(oval, p); return; }
        final RectF k = MacApkDisplayList.snap(oval);
        draw("drawOval", p, (c, q) -> c.drawOval(k, q));
    }
    @Override public void drawOval(float l, float t, float r, float b, Paint p) {
        if (rec == null) { super.drawOval(l, t, r, b, p); return; }
        draw("drawOval", p, (c, q) -> c.drawOval(l, t, r, b, q));
    }
    @Override public void drawArc(RectF oval, float start, float sweep, boolean useCenter, Paint p) {
        if (rec == null) { super.drawArc(oval, start, sweep, useCenter, p); return; }
        final RectF k = MacApkDisplayList.snap(oval);
        draw("drawArc", p, (c, q) -> c.drawArc(k, start, sweep, useCenter, q));
    }
    @Override public void drawArc(float l, float t, float r, float b, float start, float sweep,
                                  boolean useCenter, Paint p) {
        if (rec == null) { super.drawArc(l, t, r, b, start, sweep, useCenter, p); return; }
        draw("drawArc", p, (c, q) -> c.drawArc(l, t, r, b, start, sweep, useCenter, q));
    }
    @Override public void drawRoundRect(RectF rect, float rx, float ry, Paint p) {
        if (rec == null) { super.drawRoundRect(rect, rx, ry, p); return; }
        final RectF k = MacApkDisplayList.snap(rect);
        draw("drawRoundRect", p, (c, q) -> c.drawRoundRect(k, rx, ry, q));
    }
    @Override public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        if (rec == null) { super.drawRoundRect(l, t, r, b, rx, ry, p); return; }
        draw("drawRoundRect", p, (c, q) -> c.drawRoundRect(l, t, r, b, rx, ry, q));
    }
    @Override public void drawDoubleRoundRect(RectF outer, float orx, float ory,
                                              RectF inner, float irx, float iry, Paint p) {
        if (rec == null) { super.drawDoubleRoundRect(outer, orx, ory, inner, irx, iry, p); return; }
        final RectF o = MacApkDisplayList.snap(outer), i = MacApkDisplayList.snap(inner);
        draw("drawDoubleRoundRect", p, (c, q) -> c.drawDoubleRoundRect(o, orx, ory, i, irx, iry, q));
    }
    @Override public void drawDoubleRoundRect(RectF outer, float[] outerRadii,
                                              RectF inner, float[] innerRadii, Paint p) {
        if (rec == null) { super.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, p); return; }
        final RectF o = MacApkDisplayList.snap(outer), i = MacApkDisplayList.snap(inner);
        final float[] orr = MacApkDisplayList.snap(outerRadii), irr = MacApkDisplayList.snap(innerRadii);
        draw("drawDoubleRoundRect", p, (c, q) -> c.drawDoubleRoundRect(o, orr, i, irr, q));
    }
    @Override public void drawPoint(float px, float py, Paint p) {
        if (rec == null) { super.drawPoint(px, py, p); return; }
        draw("drawPoint", p, (c, q) -> c.drawPoint(px, py, q));
    }
    @Override public void drawPoints(float[] pts, int offset, int count, Paint p) {
        if (rec == null) { super.drawPoints(pts, offset, count, p); return; }
        if (pts == null) throw new NullPointerException();
        if ((offset | count) < 0 || offset + count > pts.length) throw new ArrayIndexOutOfBoundsException();
        final float[] k = MacApkDisplayList.range(pts, offset, offset + count);
        final int ko = (k == pts) ? offset : 0;
        draw("drawPoints", p, (c, q) -> c.drawPoints(k, ko, count, q));
    }
    @Override public void drawPoints(float[] pts, Paint p) {
        if (rec == null) { super.drawPoints(pts, p); return; }
        final float[] k = MacApkDisplayList.snap(pts);
        draw("drawPoints", p, (c, q) -> c.drawPoints(k, q));
    }
    @Override public void drawPath(Path path, Paint p) {
        if (rec == null) { super.drawPath(path, p); return; }
        final Path k = MacApkDisplayList.snap(path);
        draw("drawPath", p, (c, q) -> c.drawPath(k, q));
    }
    @Override public void drawRegion(Region region, Paint paint) {
        if (rec == null) { super.drawRegion(region, paint); return; }
        // Canvas's @NonNull checks fire at the CALL, as on a device.
        if (region == null) throw new NullPointerException("region must not be null");
        if (paint == null) throw new NullPointerException("paint must not be null");
        final Region k = MacApkDisplayList.snap(region);
        draw("drawRegion", paint, (c, q) -> c.drawRegion(k, q));
    }
    @Override public void drawMesh(Mesh mesh, BlendMode blendMode, Paint paint) {
        if (rec == null) { super.drawMesh(mesh, blendMode, paint); return; }
        if (mesh == null) throw new NullPointerException("mesh must not be null");
        if (paint == null) throw new NullPointerException("paint must not be null");
        draw("drawMesh", paint, (c, q) -> c.drawMesh(mesh, blendMode, q));
    }
    @Override public void drawVertices(VertexMode mode, int vertexCount, float[] verts, int vertOffset,
                                       float[] texs, int texOffset, int[] colors, int colorOffset,
                                       short[] indices, int indexOffset, int indexCount, Paint p) {
        if (rec == null) {
            super.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset, colors,
                               colorOffset, indices, indexOffset, indexCount, p);
            return;
        }
        final float[] v = MacApkDisplayList.snap(verts), tx = MacApkDisplayList.snap(texs);
        final int[] co = MacApkDisplayList.snap(colors);
        final short[] ix = MacApkDisplayList.snap(indices);
        draw("drawVertices", p, (c, q) -> c.drawVertices(mode, vertexCount, v, vertOffset, tx, texOffset,
                co, colorOffset, ix, indexOffset, indexCount, q));
    }

    // ---- Text ----------------------------------------------------------------
    @Override public void drawText(String text, float x0, float y0, Paint p) {
        if (rec == null) { super.drawText(text, x0, y0, p); return; }
        draw("drawText", p, (c, q) -> c.drawText(text, x0, y0, q));
    }
    @Override public void drawText(String text, int start, int end, float x0, float y0, Paint p) {
        if (rec == null) { super.drawText(text, start, end, x0, y0, p); return; }
        draw("drawText", p, (c, q) -> c.drawText(text, start, end, x0, y0, q));
    }
    @Override public void drawText(CharSequence text, int start, int end, float x0, float y0, Paint p) {
        if (rec == null) { super.drawText(text, start, end, x0, y0, p); return; }
        if (text == null) return;   // Canvas returns for null text
        final CharSequence k = MacApkDisplayList.snapRange(text, start, end);
        final int s = (k instanceof String && k == text) ? start : 0;
        final int e = (k instanceof String && k == text) ? end : end - start;
        draw("drawText", p, (c, q) -> c.drawText(k, s, e, x0, y0, q));
    }
    @Override public void drawText(char[] text, int index, int count, float x0, float y0, Paint p) {
        if (rec == null) { super.drawText(text, index, count, x0, y0, p); return; }
        if ((index | count | (index + count) | (text.length - index - count)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        final char[] k = MacApkDisplayList.range(text, index, index + count);
        final int ki = (k == text) ? index : 0;
        draw("drawText", p, (c, q) -> c.drawText(k, ki, count, x0, y0, q));
    }
    @Override public void drawTextRun(char[] text, int index, int count, int contextIndex,
                                      int contextCount, float x0, float y0, boolean isRtl, Paint p) {
        if (rec == null) { super.drawTextRun(text, index, count, contextIndex, contextCount, x0, y0, isRtl, p); return; }
        if (text == null) throw new NullPointerException("text is null");
        if (p == null) throw new NullPointerException("paint is null");
        if ((index | count | contextIndex | contextCount | index - contextIndex
                | (contextIndex + contextCount) - (index + count)
                | text.length - (contextIndex + contextCount)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        // The CONTEXT range, which holds the run: it is what shaping reads.
        final char[] k = MacApkDisplayList.range(text, contextIndex, contextIndex + contextCount);
        final int off = (k == text) ? 0 : contextIndex;
        draw("drawTextRun", p, (c, q) -> c.drawTextRun(k, index - off, count, contextIndex - off,
                contextCount, x0, y0, isRtl, q));
    }
    @Override public void drawTextRun(CharSequence text, int start, int end, int contextStart,
                                      int contextEnd, float x0, float y0, boolean isRtl, Paint p) {
        if (rec == null) { super.drawTextRun(text, start, end, contextStart, contextEnd, x0, y0, isRtl, p); return; }
        if (text == null) return;
        // Snapshot the whole CONTEXT range, and keep the run's place in it.
        final int cs = Math.max(0, Math.min(contextStart, start));
        final int ce = Math.min(text.length(), Math.max(contextEnd, end));
        final CharSequence k = MacApkDisplayList.snapRange(text, cs, ce);
        final int off = (k instanceof String && k == text) ? 0 : cs;
        draw("drawTextRun", p, (c, q) -> c.drawTextRun(k, start - off, end - off,
                contextStart - off, contextEnd - off, x0, y0, isRtl, q));
    }
    @Override public void drawTextRun(android.graphics.text.MeasuredText text, int start, int end,
                                      int contextStart, int contextEnd, float x0, float y0,
                                      boolean isRtl, Paint p) {
        if (rec == null) { super.drawTextRun(text, start, end, contextStart, contextEnd, x0, y0, isRtl, p); return; }
        // MeasuredText is immutable once built: by reference.
        draw("drawTextRun", p, (c, q) -> c.drawTextRun(text, start, end, contextStart, contextEnd, x0, y0, isRtl, q));
    }
    @Override public void drawTextOnPath(String text, Path path, float hOffset, float vOffset, Paint p) {
        if (rec == null) { super.drawTextOnPath(text, path, hOffset, vOffset, p); return; }
        final Path k = MacApkDisplayList.snap(path);
        draw("drawTextOnPath", p, (c, q) -> c.drawTextOnPath(text, k, hOffset, vOffset, q));
    }
    @Override public void drawTextOnPath(char[] text, int index, int count, Path path,
                                         float hOffset, float vOffset, Paint p) {
        if (rec == null) { super.drawTextOnPath(text, index, count, path, hOffset, vOffset, p); return; }
        // AOSP's check, plus count < 0: its JNI would pass that to the shaper,
        // our Canvas throws on it, and a throw belongs at the call.
        if (index < 0 || count < 0 || index + count > text.length) throw new ArrayIndexOutOfBoundsException();
        final char[] k = MacApkDisplayList.range(text, index, index + count);
        final int ki = (k == text) ? index : 0;
        final Path pk = MacApkDisplayList.snap(path);
        draw("drawTextOnPath", p, (c, q) -> c.drawTextOnPath(k, ki, count, pk, hOffset, vOffset, q));
    }
    @Override public void drawPosText(char[] text, int index, int count, float[] pos, Paint p) {
        if (rec == null) { super.drawPosText(text, index, count, pos, p); return; }
        if (index < 0 || index + count > text.length || count * 2 > pos.length) {
            throw new IndexOutOfBoundsException();
        }
        // Glyph i sits at pos[2i], pos[2i + 1] from 0, not from the index.
        final char[] k = MacApkDisplayList.range(text, index, index + Math.max(0, count));
        final int ki = (k == text) ? index : 0;
        final float[] ps = MacApkDisplayList.range(pos, 0, Math.max(0, count) * 2);
        draw("drawPosText", p, (c, q) -> c.drawPosText(k, ki, count, ps, q));
    }
    @Override public void drawPosText(String text, float[] pos, Paint p) {
        if (rec == null) { super.drawPosText(text, pos, p); return; }
        final float[] ps = MacApkDisplayList.snap(pos);
        draw("drawPosText", p, (c, q) -> c.drawPosText(text, ps, q));
    }
    @Override public void drawGlyphs(int[] glyphIds, int glyphIdOffset, float[] positions,
                                     int positionOffset, int glyphCount,
                                     android.graphics.fonts.Font font, Paint paint) {
        if (rec == null) { super.drawGlyphs(glyphIds, glyphIdOffset, positions, positionOffset, glyphCount, font, paint); return; }
        java.util.Objects.requireNonNull(glyphIds, "glyphIds must not be null.");
        java.util.Objects.requireNonNull(positions, "positions must not be null.");
        java.util.Objects.requireNonNull(font, "font must not be null.");
        java.util.Objects.requireNonNull(paint, "paint must not be null.");
        if (glyphCount < 0) throw new IllegalArgumentException();     // checkArgumentNonnegative
        if (glyphIdOffset < 0 || glyphIdOffset + glyphCount > glyphIds.length) {
            throw new IndexOutOfBoundsException(
                    "glyphIds must have at least " + (glyphIdOffset + glyphCount) + " of elements");
        }
        if (positionOffset < 0 || positionOffset + glyphCount * 2 > positions.length) {
            throw new IndexOutOfBoundsException(
                    "positions must have at least " + (positionOffset + glyphCount * 2) + " of elements");
        }
        final int[] g = MacApkDisplayList.range(glyphIds, glyphIdOffset, glyphIdOffset + glyphCount);
        final float[] ps = MacApkDisplayList.range(positions, positionOffset, positionOffset + glyphCount * 2);
        final int go = (g == glyphIds) ? glyphIdOffset : 0, po = (ps == positions) ? positionOffset : 0;
        draw("drawGlyphs", paint, (c, q) -> c.drawGlyphs(g, go, ps, po, glyphCount, font, q));
    }

    // ---- Bitmaps (held BY REFERENCE, as hwui holds them) ---------------------
    // By reference, so a pixel write after the recording shows at the next
    // replay, PLUS a pixel ref (MacApkDisplayList.pixelRef), so a recycle()
    // before the replay does not take the pixels with it: hwui records
    // bitmap.makeImage(), an SkImage that refs the pixels itself.
    @Override public void drawBitmap(Bitmap b, float left, float top, Paint p) {
        if (rec == null) { super.drawBitmap(b, left, top, p); return; }
        final Bitmap pr = MacApkDisplayList.pixelRef(b);
        draw("drawBitmap", p, (c, q) -> c.drawBitmap(MacApkDisplayList.live(b, pr), left, top, q));
    }
    @Override public void drawBitmap(Bitmap b, Rect src, Rect dst, Paint p) {
        if (rec == null) { super.drawBitmap(b, src, dst, p); return; }
        final Rect s = MacApkDisplayList.snap(src), d = MacApkDisplayList.snap(dst);
        final Bitmap pr = MacApkDisplayList.pixelRef(b);
        draw("drawBitmap", p, (c, q) -> c.drawBitmap(MacApkDisplayList.live(b, pr), s, d, q));
    }
    @Override public void drawBitmap(Bitmap b, Rect src, RectF dst, Paint p) {
        if (rec == null) { super.drawBitmap(b, src, dst, p); return; }
        final Rect s = MacApkDisplayList.snap(src);
        final RectF d = MacApkDisplayList.snap(dst);
        final Bitmap pr = MacApkDisplayList.pixelRef(b);
        draw("drawBitmap", p, (c, q) -> c.drawBitmap(MacApkDisplayList.live(b, pr), s, d, q));
    }
    @Override public void drawBitmap(Bitmap b, Matrix matrix, Paint p) {
        if (rec == null) { super.drawBitmap(b, matrix, p); return; }
        final Matrix m = MacApkDisplayList.snap(matrix);
        final Bitmap pr = MacApkDisplayList.pixelRef(b);
        draw("drawBitmap", p, (c, q) -> c.drawBitmap(MacApkDisplayList.live(b, pr), m, q));
    }
    @Override public void drawBitmap(int[] colors, int offset, int stride, float left, float top,
                                     int width, int height, boolean hasAlpha, Paint p) {
        if (rec == null) { super.drawBitmap(colors, offset, stride, left, top, width, height, hasAlpha, p); return; }
        // hwui copies the colours into a Bitmap at record time; this keeps the
        // rows the call reads.
        final int[] k = colorRows(colors, offset, stride, width, height);
        if (k == null) return;                                   // AOSP: "nothing to draw"
        final int ko = (k == colors) ? offset : offset - Math.min(offset, offset + (height - 1) * stride);
        draw("drawBitmap", p, (c, q) -> c.drawBitmap(k, ko, stride, left, top, width, height, hasAlpha, q));
    }
    @Override public void drawBitmap(int[] colors, int offset, int stride, int left, int top,
                                     int width, int height, boolean hasAlpha, Paint p) {
        if (rec == null) { super.drawBitmap(colors, offset, stride, left, top, width, height, hasAlpha, p); return; }
        final int[] k = colorRows(colors, offset, stride, width, height);
        if (k == null) return;
        final int ko = (k == colors) ? offset : offset - Math.min(offset, offset + (height - 1) * stride);
        draw("drawBitmap", p, (c, q) -> c.drawBitmap(k, ko, stride, left, top, width, height, hasAlpha, q));
    }
    /** AOSP BaseRecordingCanvas.drawBitmap(int[] ...) android14 :143-167: its
     *  checks, verbatim, then the span of rows the call reads (the stride may be
     *  negative, so from the lower of the first and last scanline). Null when
     *  AOSP returns without drawing (a zero width or height). */
    private static int[] colorRows(int[] colors, int offset, int stride, int width, int height) {
        if (width < 0) throw new IllegalArgumentException("width must be >= 0");
        if (height < 0) throw new IllegalArgumentException("height must be >= 0");
        if (Math.abs(stride) < width) throw new IllegalArgumentException("abs(stride) must be >= width");
        final int lastScanline = offset + (height - 1) * stride;
        final int length = colors.length;
        if (offset < 0 || (offset + width > length) || lastScanline < 0
                || (lastScanline + width > length)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (width == 0 || height == 0) return null;
        final int lo = Math.min(offset, lastScanline), hi = Math.max(offset, lastScanline) + width;
        return MacApkDisplayList.range(colors, lo, hi);
    }
    @Override public void drawBitmapMesh(Bitmap b, int meshWidth, int meshHeight, float[] verts,
                                         int vertOffset, int[] colors, int colorOffset, Paint p) {
        if (rec == null) { super.drawBitmapMesh(b, meshWidth, meshHeight, verts, vertOffset, colors, colorOffset, p); return; }
        final float[] v = MacApkDisplayList.snap(verts);
        final int[] co = MacApkDisplayList.snap(colors);
        final Bitmap pr = MacApkDisplayList.pixelRef(b);
        draw("drawBitmapMesh", p, (c, q) -> c.drawBitmapMesh(MacApkDisplayList.live(b, pr),
                meshWidth, meshHeight, v, vertOffset, co, colorOffset, q));
    }
    // The NinePatch itself is held by reference (its chunk is immutable), and
    // its bitmap gets the same pixel ref. A NinePatch has no public way to be
    // rebuilt on other pixels (the chunk is private), so a recycled one is
    // drawn by pointing the patch at the kept storage for the one call.
    @Override public void drawPatch(NinePatch patch, Rect dst, Paint p) {
        if (rec == null) { super.drawPatch(patch, dst, p); return; }
        final Rect d = MacApkDisplayList.snap(dst);
        final Bitmap pb = (patch != null) ? patch.getBitmap() : null;
        final Bitmap pr = MacApkDisplayList.pixelRef(pb);
        draw("drawPatch", p, (c, q) -> patchCall(patch, pb, pr, () -> c.drawPatch(patch, d, q)));
    }
    @Override public void drawPatch(NinePatch patch, RectF dst, Paint p) {
        if (rec == null) { super.drawPatch(patch, dst, p); return; }
        final RectF d = MacApkDisplayList.snap(dst);
        final Bitmap pb = (patch != null) ? patch.getBitmap() : null;
        final Bitmap pr = MacApkDisplayList.pixelRef(pb);
        draw("drawPatch", p, (c, q) -> patchCall(patch, pb, pr, () -> c.drawPatch(patch, d, q)));
    }
    private static void patchCall(NinePatch patch, Bitmap pb, Bitmap pr, Runnable call) {
        if (pr == null || patch.getBitmap() != pb || !pb.isRecycled()) { call.run(); return; }
        patch.setBitmap(pr);
        try { call.run(); } finally { patch.setBitmap(pb); }
    }

    // ---- Composites ----------------------------------------------------------
    /** AOSP RecordingCanvas.drawRenderNode: nDrawRenderNode records a REFERENCE
     *  to the child, so replaying this recording draws whatever the child holds
     *  THEN -- a child that re-records needs no re-record here, and a property
     *  change on the child reaches the screen with no re-record at all. */
    @Override public void drawRenderNode(RenderNode node) {
        if (rec == null) { super.drawRenderNode(node); return; }
        if (node == null) return;   // Canvas returns for null; nothing to record
        rec.add("drawRenderNode", new MacApkDisplayList.NodeOp(node), MacApkDisplayList.K_NODE);
    }
    /** AOSP Canvas.drawPicture(Picture) ends the picture's recording at the call
     *  and plays the SkPicture as it is then; a later re-record of the same
     *  Picture is a new SkPicture that this recording never sees. Hence the
     *  snapshot (Picture(Picture) shares the finished content). */
    @Override public void drawPicture(Picture picture) {
        if (rec == null) { super.drawPicture(picture); return; }
        if (picture == null) return;
        if (Canvas.DRAWPICTURE_AOSP) picture.endRecording();
        final Picture k = new Picture(picture);
        draw("drawPicture", (c, x) -> c.drawPicture(k));
    }
    @Override public void drawPicture(Picture picture, RectF dst) {
        if (rec == null) { super.drawPicture(picture, dst); return; }
        if (picture == null) return;
        if (Canvas.DRAWPICTURE_AOSP) picture.endRecording();
        final Picture k = new Picture(picture);
        final RectF d = MacApkDisplayList.snap(dst);
        draw("drawPicture", (c, x) -> c.drawPicture(k, d));
    }
    @Override public void drawPicture(Picture picture, Rect dst) {
        if (rec == null) { super.drawPicture(picture, dst); return; }
        if (picture == null) return;
        if (Canvas.DRAWPICTURE_AOSP) picture.endRecording();
        final Picture k = new Picture(picture);
        final Rect d = MacApkDisplayList.snap(dst);
        draw("drawPicture", (c, x) -> c.drawPicture(k, d));
    }
    /** SurfaceView's hole. Replayed onto the target, the DST_OUT reaches the
     *  frame directly, as hwui's does (SkiaCanvas.cpp:260-266): no per-node
     *  punch list is needed on this path. Under a LAYER it reaches the frame
     *  through the hole pre-pass (MacApkDisplayList.replayPunches), which is
     *  why it is a PunchOp: hwui records the same hole twice for the same
     *  reason (SkiaRecordingCanvas.cpp:73-92, an annotation plus the DstOut). */
    @Override public void punchHole(float left, float top, float right, float bottom,
                                    float rx, float ry, float alpha) {
        if (rec == null) { super.punchHole(left, top, right, bottom, rx, ry, alpha); return; }
        rec.add("punchHole", new MacApkDisplayList.PunchOp(left, top, right, bottom, rx, ry, alpha),
                MacApkDisplayList.K_PUNCH);
    }

    /** AOSP RecordingCanvas exposes width/height it was begun with; the
     *  inherited Canvas getWidth()/getHeight() (bitmap-backed, and in op-list
     *  mode backed by the state bitmap of the same size) already return the
     *  recording dimensions, so no override is needed. */

}
