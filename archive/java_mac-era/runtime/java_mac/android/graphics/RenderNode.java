// android.graphics.RenderNode -- the display-list / hardware-layer
// primitive (public API since API 29).
//
// Why this is load-bearing for Mac.apk: modern Jetpack Compose builds
// (Reddit 2026.21 ships compose-ui with the GraphicsLayer / RenderNode
// layer path) construct `new RenderNode("Compose")` UNCONDITIONALLY on
// API >= 29 for EVERY composable that owns a layer (graphicsLayer,
// clip, alpha, shadow). Before this class existed as more than a
// no-arg stub, AndroidComposeView.createLayer threw
//   NoSuchMethodError: void android.graphics.RenderNode.<init>(String)
// during onLayout, which aborted the whole Compose measure/layout pass
// so the UI never drew (Reddit's login screen stayed blank).
//
// Our Canvas is software (AWT BufferedImage + Graphics2D). A RenderNode
// here is a transform/clip property bag plus its recorded content, which
// is an OP LIST replayed by reference (MacApkDisplayList, the hwui model;
// MACAPK_RENDERNODE_OPLIST, default ON) or, with that gate off and for a
// node whose recording hit the tripwire, an offscreen recording BITMAP
// (the pre-2026-09-26 model the notes below describe). Two draw paths both work:
//   (1) Software path: the layer's drawLayer() sees
//       canvas.isHardwareAccelerated()==false, re-invokes its drawBlock
//       directly onto the target canvas after canvas.concat(getMatrix()).
//       Here the RenderNode only supplies getMatrix()/left/top/alpha/
//       clip -- no recorded bitmap is used.
//   (2) Hardware path: the layer records into beginRecording()'s
//       RecordingCanvas, then the parent calls canvas.drawRenderNode(node)
//       which replays the recorded op list (or composites the recorded
//       bitmap) with this node's transform + alpha + clip applied.
// We implement BOTH so whichever path a given Compose version chooses
// produces pixels.
//
// Mirrors the AOSP public surface
// (frameworks/base/graphics/java/android/graphics/RenderNode.java);
// setters return boolean ("did the value change") per AOSP. The matrix
// composition order matches RenderProperties::updateMatrix:
//   M = T(translation) * T(pivot) * Rz * S * T(-pivot)
// (3D rotationX/Y + cameraDistance are stored but not applied -- our
// software canvas is 2D; Compose layers overwhelmingly use
// translation/scale/rotationZ/alpha/clip, which we render exactly).
package android.graphics;

public class RenderNode {
    private final String name;

    // Bounds / position (parent-space).
    private int left, top, right, bottom;

    // Transform properties.
    private float alpha = 1f;
    private float translationX, translationY, translationZ;
    private float scaleX = 1f, scaleY = 1f;
    private float rotationX, rotationY, rotationZ;
    private float pivotX, pivotY;
    private boolean pivotExplicit;
    private float elevation, cameraDistance;
    private int ambientShadowColor = 0xFF000000;
    private int spotShadowColor = 0xFF000000;

    // Clip.
    private boolean clipToBounds = true;
    private boolean clipToOutline = false;
    private Outline outline;
    private final Rect clipRect = new Rect();
    private boolean hasClipRect;

    private boolean hasOverlappingRendering = true;

    // Recorded content (hardware-path replay).
    private Bitmap recorded;
    /** v1.0.997: holes punched into THIS node's recording, in the node's own
     *  recording (device) space, so Canvas.drawRenderNode can re-apply them to
     *  the parent before it blits our pixels.
     *
     *  WHY THIS HAS TO EXIST AT ALL. AOSP's SurfaceView exposes the surface
     *  behind the window with canvas.punchHole(...), which HWUI implements as
     *  a DST_OUT draw (libs/hwui/SkiaCanvas.cpp:267-273, fetched 2026-08-18:
     *  paint.setBlendMode(SkBlendMode::kDstOut); mCanvas->drawRRect(rect, paint)).
     *  That reaches the window surface because HWUI replays a RenderNode's
     *  DISPLAY LIST into the same canvas -- no intermediate buffer exists unless
     *  a layer was explicitly requested, which is exactly why
     *  SurfaceView.hasOverlappingRendering() returns false.
     *
     *  Ours is a software canvas: beginRecording allocates an ARGB Bitmap and
     *  Canvas.drawRenderNode COPIES it onto the parent with SRC_OVER. A
     *  destination-clearing op recorded inside a node therefore clears only the
     *  node's own offscreen, and SRC_OVER of a transparent region leaves the
     *  parent untouched -- so the hole never reaches the frame. Measured on
     *  PlayTube 2026-08-18: SurfaceView.draw punched 227 times over 145 s and
     *  the presented canvas had 0 transparent pixels in the video rect
     *  (alpha histogram {255: 92112}), with the punch site reporting
     *  isRoot=false against ViewCanvasDriver's frame canvas.
     *
     *  Recording the rect and re-applying it one level up per replay
     *  reconstructs AOSP's observable result in our buffered model. Empty for
     *  virtually every node, so the replay cost is one null check. */
    private java.util.ArrayList<Punch> macapkPunches;

    /** A punched hole: the shape in its recording's own device space, plus the
     *  alpha AOSP carries through punchHole (the SurfaceView's View alpha). */
    public static final class Punch {
        public final java.awt.Shape shape;
        public final float alpha;
        Punch(java.awt.Shape shape, float alpha) { this.shape = shape; this.alpha = alpha; }
    }

    /** Package hook: Canvas.punchHole records here while this node is recording. */
    void macapkAddPunch(java.awt.Shape deviceShape, float alpha) {
        if (deviceShape == null) return;
        if (macapkPunches == null) macapkPunches = new java.util.ArrayList<>(2);
        // DEDUPE, and it is not cosmetic. This node only clears the list when it
        // RE-RECORDS, but the replay path adds to it on every frame a child is
        // drawn -- so a node that records once and replays for a minute would
        // otherwise accumulate copies of one identical hole and pay a DST_OUT
        // fill for each of them, every frame. DST_OUT is idempotent, so the
        // duplicates were invisible and purely wasted.
        java.awt.Rectangle nb = deviceShape.getBounds();
        for (int i = 0; i < macapkPunches.size(); i++) {
            Punch p = macapkPunches.get(i);
            if (p.alpha == alpha && p.shape.getBounds().equals(nb)) return;
        }
        // Cap anyway: a genuinely moving surface (a video view animating across
        // the screen without the node re-recording) must not grow unbounded.
        if (macapkPunches.size() < 16) macapkPunches.add(new Punch(deviceShape, alpha));
    }

    /** Package hook: Canvas.drawRenderNode replays these onto the parent. */
    java.util.ArrayList<Punch> macapkPunches() { return macapkPunches; }
    private RecordingCanvas recordingCanvas;
    private boolean hasDisplayList;

    // Snapshot of the global per-type draw counters at beginRecording, so
    // endRecording can log exactly what the content block drew INTO this
    // recording (rect/text/bitmap/path/roundRect deltas). recordingCanvas.
    // macapkDrawOps only counts drawRenderNode replays, NOT leaf draws, so it
    // makes every recording look empty; the global deltas are the authoritative
    // signal for "did the content lambda emit any leaf ops during record".
    private long recRect, recText, recBitmap, recPath, recRound;

    private static final boolean macapkTrace =
        System.getenv("MACAPK_RENDERNODE_TRACE") != null;
    /** Shares Canvas's blit-chain gate on purpose: "which recording bitmap
     *  belongs to which node" is the missing half of that trace -- without it a
     *  48x48 destination is just a size, and a layer re-record is
     *  indistinguishable from an unrelated scratch buffer. One switch for one
     *  question, per the same reasoning as MACAPK_TRACE_VECTORDRAW's two halves. */
    private static final boolean macapkTraceBlit =
        "1".equals(System.getenv("MACAPK_TRACE_VECTORDRAW"));

    // ---- Aggregate record/replay statistics (MACAPK_RENDERNODE_STATS=1) ----
    // A SECOND, cheaper gate next to MACAPK_RENDERNODE_TRACE above. The trace
    // gate prints a 30-frame stack per beginRecording; a modern Compose screen
    // owns ~100 layers and re-records at 60 fps, so it drowns the log AND
    // changes the timing of the very pipeline being measured. The question
    // "did the recordings capture leaf draws, and did the replays put them on
    // the frame canvas" only needs COUNTS, so keep them as O(1) increments
    // behind a `static final` flag (the JIT folds the whole block away when
    // unset) and print one line on request.
    //
    // Reading the dump: recNonEmpty>0 with replayOk==0 means "Compose recorded
    // its content and the replay never happened" (a record->replay break);
    // recEmpty≈records means "the composition emitted no leaf draws at all"
    // (look upstream at the recomposer / frame clock, not here); recTiny>0
    // means the node's position was never sized, so the content recorded into
    // a 1x1 bitmap.
    static final boolean macapkStats =
        System.getenv("MACAPK_RENDERNODE_STATS") != null;
    static final java.util.concurrent.atomic.AtomicLong sStRecords    = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStRecEmpty   = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStRecTiny    = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStReplays    = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStReplayNoG  = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStReplayNoRec= new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStReplayClipped = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong sStReplayOk   = new java.util.concurrent.atomic.AtomicLong();
    static volatile int sStMaxRecW = 0, sStMaxRecH = 0;

    /** One-line aggregate dump of the record/replay tallies. Called from the
     *  canvas driver's existing checkpoint frames; no-op unless
     *  MACAPK_RENDERNODE_STATS is set. */
    public static void macapkStatsDump(String where) {
        if (!macapkStats) return;
        com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
            "STATS " + where
            + " records=" + sStRecords.get()
            + " recEmpty=" + sStRecEmpty.get()
            + " recTiny=" + sStRecTiny.get()
            + " maxRec=" + sStMaxRecW + "x" + sStMaxRecH
            + " | replays=" + sStReplays.get()
            + " noG=" + sStReplayNoG.get()
            + " noRec=" + sStReplayNoRec.get()
            + " clippedOut=" + sStReplayClipped.get()
            + " ok=" + sStReplayOk.get());
    }

    public RenderNode(String name) { this.name = name; }
    public RenderNode() { this.name = null; }
    /** API-23 factory name; kept for older androidx layer impls. */
    public static RenderNode create(String name, Object animationHost) {
        return new RenderNode(name);
    }
    public String getName() { return name; }

    // ---- Stale-ancestor tracking (see beginRecording) -------------------
    //
    // AOSP's display list stores a child node BY REFERENCE: the parent records
    // a DrawRenderNodeOp, and replaying the parent reads whatever the child
    // currently holds. So re-recording a leaf is enough, and hwui needs no
    // upward signal.
    //
    // Ours is a software canvas with no op list: Canvas.drawRenderNode COPIES
    // the child's recorded pixels into the parent's backing bitmap. That makes
    // every ancestor's bitmap a SNAPSHOT of its descendants taken at the moment
    // the parent recorded -- correct when the whole tree records together (the
    // first frame), and silently stale forever after if only a leaf re-records.
    //
    // MEASURED, and it is the reason this exists (Aurora Store, 2026-08-12):
    // when Coil delivered six onboarding icons, their leaf layers re-recorded
    // 24 times and NOT ONE of those recordings was ever replayed -- the only
    // replay after the load was the Compose root, from its unchanged snapshot.
    // Compose asks `hasDisplayList()` before re-recording (RenderNodeLayer
    // .updateDisplayList re-records when it is dirty OR has no display list),
    // so answering false is exactly the hook needed to make the chain re-record
    // the way hwui's by-reference replay would have.
    //
    // MACAPK_RENDERNODE_CHILD_INVALIDATE=0 restores the snapshot behaviour for
    // a one-run A/B. Default ON.
    private static final boolean CHILD_INVALIDATE =
        !"0".equals(System.getenv("MACAPK_RENDERNODE_CHILD_INVALIDATE"));
    /** The node whose recording canvas this node was last replayed into. Set by
     *  Canvas.drawRenderNode; re-set on every replay because a node can be
     *  re-parented. */
    private volatile RenderNode macapkReplayParent;
    /** A descendant re-recorded since this node last recorded, so this node's
     *  bitmap no longer contains that descendant's current pixels. */
    private volatile boolean macapkChildRecorded;

    /** Package hook: Canvas.drawRenderNode records who flattened us. */
    void macapkSetReplayParent(RenderNode parent) {
        if (parent != this) macapkReplayParent = parent;
    }
    /** Mark this node and every ancestor as holding a stale snapshot. Depth-
     *  capped rather than cycle-tracked: the chain is a UI layer tree a few
     *  dozen deep, and a cap cannot itself introduce a hang if a future change
     *  ever produces a loop. */
    private void macapkInvalidateAncestors() {
        RenderNode p = macapkReplayParent;
        for (int depth = 0; p != null && depth < 64; depth++) {
            if (p.macapkChildRecorded) break;   // already marked: so is its chain
            p.macapkChildRecorded = true;
            p = p.macapkReplayParent;
        }
    }

    /** A PROPERTY change is the second way an ancestor's flattened copy goes
     *  stale, and it was missing until 2026-08-13.
     *
     *  Canvas.drawRenderNode bakes this node's position, transform, clip, alpha
     *  and RenderEffect into the parent's bitmap AT REPLAY TIME. hwui does not:
     *  it keeps the child by reference and re-composites from the properties
     *  every frame, so `renderNode.setAlpha(0.4f)` alone is enough to change the
     *  screen and NOTHING re-records. That is exactly what an animating Compose
     *  `graphicsLayer` does -- RenderNodeLayer.updateLayerProperties writes the
     *  properties and deliberately does NOT re-record the display list, because
     *  on a device it does not have to.
     *
     *  MEASURED (Aurora Store Settings, 2026-08-13): the screen composed and
     *  recorded in full -- TopAppBar 808x128, seven ListItem rows 808x112 at
     *  y=0..672, LazyColumn 808x1524, `nonTransparent=27376/27376` -- and was
     *  replayed ONCE at the navigation transition's frame-0 values,
     *  `pos=808,0 alpha=0.0`. The container that flattened it therefore recorded
     *  `nonTransparent=0/27376`, the transition then advanced by property writes
     *  only, no ancestor was ever told, and the screen stayed blank forever.
     *  Every Compose enter/exit transition, AnimatedVisibility, and
     *  animate*AsState driving a graphicsLayer has the same shape.
     *
     *  Only call this for properties Canvas.drawRenderNode actually READS --
     *  rotationX/Y, cameraDistance, elevation and the shadow colours are stored
     *  but never applied by our 2D replay, so invalidating on those would cost
     *  a full-tree re-record and change no pixel. Gated on "the value really
     *  changed" for the same reason: Compose rewrites every layer property on
     *  every placement pass, and re-recording on an unchanged write would turn
     *  a still screen into a 60 fps rasterizer. Shares CHILD_INVALIDATE so the
     *  one A/B switch still covers both halves of the mechanism. */
    private void macapkPropertyChanged(boolean changed) {
        if (changed && CHILD_INVALIDATE) macapkInvalidateAncestors();
    }

    // ---- Op-list content (Stage A, MACAPK_RENDERNODE_OPLIST) --------------
    //
    // hwui's model: the node holds a DISPLAY LIST, and a parent's list holds this
    // node BY REFERENCE (MacApkDisplayList's header has the measurements that
    // motivated it). So a leaf that re-records reaches the screen through every
    // ancestor's existing list, and a property change reaches it with no
    // re-record at all -- the two stale-snapshot mechanisms above are
    // unnecessary by construction on this path. They stay wired for the one case
    // that still flattens pixels: a BITMAP recording (the gate off, or the
    // per-node fallback below) that draws an op-list child. In a pure op-list
    // tree nothing ever registers a replay parent, so they cost a null check.
    /** The committed display list (null: none, or bitmap mode). Replaced whole
     *  at endRecording, never mutated after, so a replay reads a stable list. */
    private volatile MacApkDisplayList macapkList;
    /** Set when a recording hit the tripwire (a pixel op no override recorded):
     *  this node records into a bitmap from then on, which is always complete. */
    private boolean macapkBitmapFallback;

    /** Package hook: the list Canvas.drawRenderNode replays, or null. */
    MacApkDisplayList macapkDisplayList() { return macapkList; }

    // ---- Recording -----------------------------------------------------
    public RecordingCanvas beginRecording(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        // Tell every ancestor its flattened copy of us is now out of date.
        // Safe DURING an ancestor's own recording pass (the common nested case:
        // parent.beginRecording -> drawBlock -> child.beginRecording): the
        // ancestor's endRecording clears the flag afterwards, so a node that
        // recorded AFTER its child is not left falsely stale.
        // On the op-list path this only ever reaches a BITMAP ancestor that
        // flattened us (see the op-list block above); otherwise it is a no-op.
        if (CHILD_INVALIDATE) macapkInvalidateAncestors();
        if (MacApkDisplayList.OPLIST && !macapkBitmapFallback) {
            // AOSP RenderNode.beginRecording: a fresh recording canvas; the
            // committed display list stays valid (hasDisplayList unchanged)
            // until endRecording replaces it. No pixels are allocated.
            recordingCanvas = new RecordingCanvas(this, w, h, new MacApkDisplayList());
            if (macapkPunches != null) macapkPunches = null;   // bitmap-path state
            if (macapkTraceBlit) {
                try {
                    com.macapk.loader.NativeLog.i("VectorDrawable",
                        "beginRecording node=@" + Integer.toHexString(System.identityHashCode(this))
                        + " name=" + name + " " + w + "x" + h + " oplist");
                } catch (Throwable ignored) {}
            }
            if (macapkStats) {
                sStRecords.incrementAndGet();
                if (w * h <= 1) sStRecTiny.incrementAndGet();
                if (w > sStMaxRecW) sStMaxRecW = w;
                if (h > sStMaxRecH) sStMaxRecH = h;
            }
            if (macapkTrace) {
                com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
                    "beginRecording " + w + "x" + h + " name=" + name + " oplist");
            }
            return recordingCanvas;
        }
        // Fresh ARGB bitmap each pass (TYPE_INT_ARGB is transparent-zero
        // initialized) so stale pixels never bleed through.
        recorded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        // Holes belong to ONE recording: the fresh bitmap above discards the old
        // pixels, so keeping the old rects would punch the parent for content
        // this pass never drew.
        if (macapkPunches != null) macapkPunches = null;
        recordingCanvas = new RecordingCanvas(recorded, this);
        hasDisplayList = false;
        macapkList = null;   // a bitmap recording supersedes any op list
        if (macapkTraceBlit) {
            try {
                com.macapk.loader.NativeLog.i("VectorDrawable",
                    "beginRecording node=@" + Integer.toHexString(System.identityHashCode(this))
                    + " name=" + name + " " + w + "x" + h
                    + " bmp=@" + Integer.toHexString(System.identityHashCode(recorded))
                    + " parent=@" + (macapkReplayParent == null ? "none"
                        : Integer.toHexString(System.identityHashCode(macapkReplayParent))));
            } catch (Throwable ignored) {}
        }
        // Snapshot global counters so endRecording can report this recording's
        // leaf-draw delta (see field comment).
        recRect = Canvas.sGlobalRect; recText = Canvas.sGlobalText;
        recBitmap = Canvas.sGlobalBitmap; recPath = Canvas.sGlobalPath;
        recRound = Canvas.sGlobalRoundRect;
        if (macapkStats) {
            sStRecords.incrementAndGet();
            if (w * h <= 1) sStRecTiny.incrementAndGet();
            if (w > sStMaxRecW) sStMaxRecW = w;
            if (h > sStMaxRecH) sStMaxRecH = h;
        }
        if (macapkTrace) {
            com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
                "beginRecording " + w + "x" + h + " name=" + name);
            StringBuilder sb = new StringBuilder("beginRecording stack:\n");
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(st.length, 30); i++) sb.append("  ").append(st[i]).append('\n');
            com.macapk.loader.NativeLog.i("Mac.apk.RenderNode", sb.toString());
        }
        return recordingCanvas;
    }
    public RecordingCanvas beginRecording() {
        return beginRecording(getWidth(), getHeight());
    }
    public void endRecording() {
        final RecordingCanvas rc = recordingCanvas;
        if (rc != null && rc.macapkIsOpList()) {
            final MacApkDisplayList list = rc.macapkFinishOpList();
            if (list == null) return;   // already ended: AOSP throws, we ignore
            // AOSP: "Calling this method marks the display list valid and
            // hasDisplayList() will return true."
            macapkList = list;
            recorded = null;
            hasDisplayList = true;
            macapkChildRecorded = false;
            if (list.unrecorded) {
                // Incomplete list: record this node into a bitmap from now on and
                // answer hasDisplayList()==false once, so Compose re-records it
                // on the next frame instead of showing the gap.
                macapkBitmapFallback = true;
                hasDisplayList = false;
            }
            if (macapkStats && list.drawOps == 0) sStRecEmpty.incrementAndGet();
            if (macapkTrace) {
                com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
                    "recDelta " + getWidth() + "x" + getHeight() + " name=" + name
                    + " oplist ops=" + list.size() + " draws=" + list.drawOps
                    + (list.unrecorded ? " UNRECORDED" : ""));
            }
            return;
        }
        hasDisplayList = (recorded != null);
        // This node now contains its descendants' CURRENT pixels again.
        macapkChildRecorded = false;
        if (macapkStats) {
            long leaves = (Canvas.sGlobalRect - recRect) + (Canvas.sGlobalText - recText)
                        + (Canvas.sGlobalBitmap - recBitmap) + (Canvas.sGlobalPath - recPath)
                        + (Canvas.sGlobalRoundRect - recRound);
            if (leaves == 0) sStRecEmpty.incrementAndGet();
        }
        if (macapkTrace) {
            // Authoritative "what did the content block draw" signal: the
            // per-type global-counter deltas captured across this record pass.
            // All-zero => the content lambda emitted no leaf draws (composed but
            // never drew); non-zero => content drew (then a replay/visibility
            // bug is downstream).
            com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
                "recDelta " + getWidth() + "x" + getHeight() + " name=" + name
                + " rect=" + (Canvas.sGlobalRect - recRect)
                + " text=" + (Canvas.sGlobalText - recText)
                + " bitmap=" + (Canvas.sGlobalBitmap - recBitmap)
                + " path=" + (Canvas.sGlobalPath - recPath)
                + " roundRect=" + (Canvas.sGlobalRoundRect - recRound));
        }
        if (macapkTrace && recorded != null && getWidth() > 400) {
            try {
                java.awt.image.BufferedImage bi = recorded.macapkAwtImage();
                if (bi != null) {
                    // count non-transparent pixels
                    int nonzero = 0, total = 0;
                    int w = bi.getWidth(), h = bi.getHeight();
                    for (int y = 0; y < h; y += 7) for (int x = 0; x < w; x += 7) {
                        total++; if ((bi.getRGB(x, y) >>> 24) != 0) nonzero++;
                    }
                    com.macapk.loader.NativeLog.i("Mac.apk.RenderNode",
                        "endRecording id=" + Integer.toHexString(System.identityHashCode(this))
                        + " " + w + "x" + h + " nonTransparent=" + nonzero + "/" + total
                        + " drawOps=" + (recordingCanvas != null ? recordingCanvas.macapkDrawOps : -1));
                    javax.imageio.ImageIO.write(bi, "png",
                        new java.io.File("/tmp/reddit_layer_" + w + "x" + h + ".png"));
                }
            } catch (Throwable t) {
                com.macapk.loader.NativeLog.i("Mac.apk.RenderNode", "endRecording dump failed: " + t);
            }
        }
    }
    /** API-23 names. */
    public RecordingCanvas start(int width, int height) { return beginRecording(width, height); }
    public void end(RecordingCanvas canvas) { endRecording(); }

    /** AOSP: "Returns whether the RenderNode has a display list." Compose's
     *  RenderNodeLayer.updateDisplayList() re-records when this is false, which
     *  is what makes macapkChildRecorded actionable -- see the block above
     *  beginRecording for why a stale descendant has to answer false here. */
    public boolean hasDisplayList() {
        return hasDisplayList && (recorded != null || macapkList != null) && !macapkChildRecorded;
    }
    public void discardDisplayList() {
        hasDisplayList = false; recorded = null; recordingCanvas = null; macapkList = null;
    }
    public void destroy() { discardDisplayList(); }
    public boolean isValid() {
        return hasDisplayList && (recorded != null || macapkList != null) && !macapkChildRecorded;
    }
    /** Package hook used by Canvas.drawRenderNode for the replay path. */
    Bitmap macapkRecordedBitmap() { return recorded; }

    // ---- Position ------------------------------------------------------
    public boolean setPosition(int l, int t, int r, int b) {
        boolean changed = (l != left || t != top || r != right || b != bottom);
        left = l; top = t; right = r; bottom = b;
        macapkPropertyChanged(changed);   // replay translates by left/top and clips to w/h
        return changed;
    }
    public boolean setPosition(Rect position) {
        return setPosition(position.left, position.top, position.right, position.bottom);
    }
    public boolean setLeftTopRightBottom(int l, int t, int r, int b) {
        return setPosition(l, t, r, b);
    }
    public boolean offsetLeftAndRight(int offset) { left += offset; right += offset; macapkPropertyChanged(offset != 0); return offset != 0; }
    public boolean offsetTopAndBottom(int offset) { top += offset; bottom += offset; macapkPropertyChanged(offset != 0); return offset != 0; }
    public int getLeft()   { return left; }
    public int getTop()    { return top; }
    public int getRight()  { return right; }
    public int getBottom() { return bottom; }
    public int getWidth()  { return right - left; }
    public int getHeight() { return bottom - top; }

    // ---- Transform setters/getters -------------------------------------
    public boolean setAlpha(float a) { boolean c = a != alpha; alpha = a; macapkPropertyChanged(c); return c; }
    public float getAlpha() { return alpha; }
    public boolean setTranslationX(float v) { boolean c = v != translationX; translationX = v; macapkPropertyChanged(c); return c; }
    public float getTranslationX() { return translationX; }
    public boolean setTranslationY(float v) { boolean c = v != translationY; translationY = v; macapkPropertyChanged(c); return c; }
    public float getTranslationY() { return translationY; }
    public boolean setTranslationZ(float v) { boolean c = v != translationZ; translationZ = v; return c; }
    public float getTranslationZ() { return translationZ; }
    public boolean setScaleX(float v) { boolean c = v != scaleX; scaleX = v; macapkPropertyChanged(c); return c; }
    public float getScaleX() { return scaleX; }
    public boolean setScaleY(float v) { boolean c = v != scaleY; scaleY = v; macapkPropertyChanged(c); return c; }
    public float getScaleY() { return scaleY; }
    public boolean setRotationZ(float v) { boolean c = v != rotationZ; rotationZ = v; macapkPropertyChanged(c); return c; }
    public float getRotationZ() { return rotationZ; }
    /** Legacy alias (pre-API-29 RenderNode named the Z rotation setRotation). */
    public boolean setRotation(float v) { return setRotationZ(v); }
    public float getRotation() { return rotationZ; }
    public boolean setRotationX(float v) { boolean c = v != rotationX; rotationX = v; return c; }
    public float getRotationX() { return rotationX; }
    public boolean setRotationY(float v) { boolean c = v != rotationY; rotationY = v; return c; }
    public float getRotationY() { return rotationY; }
    public boolean setPivotX(float v) { boolean c = v != pivotX; pivotX = v; pivotExplicit = true; macapkPropertyChanged(c); return c; }
    public float getPivotX() { return pivotExplicit ? pivotX : getWidth() / 2f; }
    public boolean setPivotY(float v) { boolean c = v != pivotY; pivotY = v; pivotExplicit = true; macapkPropertyChanged(c); return c; }
    public float getPivotY() { return pivotExplicit ? pivotY : getHeight() / 2f; }
    public boolean resetPivot() { boolean c = pivotExplicit; pivotExplicit = false; pivotX = 0; pivotY = 0; macapkPropertyChanged(c); return c; }
    public boolean isPivotExplicitlySet() { return pivotExplicit; }
    public boolean setCameraDistance(float v) { boolean c = v != cameraDistance; cameraDistance = v; return c; }
    public float getCameraDistance() { return cameraDistance; }
    public boolean setElevation(float v) { boolean c = v != elevation; elevation = v; return c; }
    public float getElevation() { return elevation; }
    public boolean setAmbientShadowColor(int color) { boolean c = color != ambientShadowColor; ambientShadowColor = color; return c; }
    public int getAmbientShadowColor() { return ambientShadowColor; }
    public boolean setSpotShadowColor(int color) { boolean c = color != spotShadowColor; spotShadowColor = color; return c; }
    public int getSpotShadowColor() { return spotShadowColor; }

    // ---- Clip / outline / effects --------------------------------------
    public boolean setClipToBounds(boolean v) { boolean c = v != clipToBounds; clipToBounds = v; macapkPropertyChanged(c); return c; }
    public boolean getClipToBounds() { return clipToBounds; }
    public boolean setClipRect(Rect rect) {
        // Change-detected rather than the unconditional `true` AOSP returns:
        // the return value is only advisory, but macapkPropertyChanged uses it
        // to decide whether to re-record the whole ancestor chain, and Compose
        // re-sets an unchanged clip on every placement pass.
        if (rect == null) {
            boolean c = hasClipRect; hasClipRect = false; macapkPropertyChanged(c); return true;
        }
        boolean c = !hasClipRect || clipRect.left != rect.left || clipRect.top != rect.top
                 || clipRect.right != rect.right || clipRect.bottom != rect.bottom;
        hasClipRect = true; clipRect.set(rect.left, rect.top, rect.right, rect.bottom);
        macapkPropertyChanged(c);
        return true;
    }
    public boolean setClipToOutline(boolean v) { boolean c = v != clipToOutline; clipToOutline = v; macapkPropertyChanged(c); return c; }
    public boolean getClipToOutline() { return clipToOutline; }
    public boolean setOutline(Outline o) {
        // Only clipToOutline makes the outline reach the replay (macapkApplyClip),
        // so an outline set on a non-clipping node changes no pixel and must not
        // cost an ancestor re-record. Identity compare only: Compose hands the
        // SAME Outline back after mutating it in place, so a deep compare would
        // report "unchanged" for a real shape change.
        boolean c = (o != this.outline) && clipToOutline;
        this.outline = o; macapkPropertyChanged(c); return true;
    }
    // v1.0.433: setRenderEffect's param is now the AOSP type android.graphics.
    // RenderEffect (was Object). Compose's RenderNodeApi29 is compiled against
    // setRenderEffect(RenderEffect) -- the Object descriptor mismatched, so any
    // modern Compose build setting a blur/Material3 scrim RenderEffect threw
    // NoSuchMethodError at layer creation and blanked the whole composition (the
    // exact failure this class exists to prevent). The blur itself is still not
    // sampled on the software canvas; stored for round-trip + future ConvolveOp.
    private RenderEffect mRenderEffect;
    private boolean useCompositingLayer;
    private Paint layerPaint;
    private boolean forceDarkAllowed = true;
    private static final java.util.concurrent.atomic.AtomicLong sNextUniqueId =
        new java.util.concurrent.atomic.AtomicLong(1);
    private final long uniqueId = sNextUniqueId.getAndIncrement();
    public boolean setRenderEffect(RenderEffect renderEffect) {
        boolean c = renderEffect != mRenderEffect; mRenderEffect = renderEffect;
        macapkPropertyChanged(c); return c;
    }
    public boolean setUseCompositingLayer(boolean forceToLayer, Paint paint) {
        boolean c = forceToLayer != useCompositingLayer;
        useCompositingLayer = forceToLayer; layerPaint = paint; return c;
    }
    public boolean getUseCompositingLayer() { return useCompositingLayer; }
    public boolean setProjectBackwards(boolean shouldProject) { return true; }
    public boolean setProjectionReceiver(boolean shouldReceive) { return true; }
    public boolean setForceDarkAllowed(boolean allow) {
        boolean c = allow != forceDarkAllowed; forceDarkAllowed = allow; return c;
    }
    public boolean isForceDarkAllowed() { return forceDarkAllowed; }
    /** Stable per-node id Compose layer bookkeeping reads. */
    public long getUniqueId() { return uniqueId; }
    /** Public memory heuristic (androidx/graphics + profilers). */
    public long computeApproximateMemoryUsage() {
        return (long) Math.max(0, getWidth()) * Math.max(0, getHeight()) * 4L;
    }
    public boolean hasShadow() { return (elevation > 0f || translationZ > 0f) && alpha > 0f; }

    public boolean setHasOverlappingRendering(boolean v) {
        boolean c = v != hasOverlappingRendering; hasOverlappingRendering = v; return c;
    }
    public boolean hasOverlappingRendering() { return hasOverlappingRendering; }
    public boolean hasOffsetOrScale() {
        return translationX != 0 || translationY != 0 || scaleX != 1f || scaleY != 1f;
    }

    // ---- Matrix --------------------------------------------------------
    public boolean hasIdentityMatrix() {
        return translationX == 0 && translationY == 0 && scaleX == 1f && scaleY == 1f
            && rotationZ == 0 && rotationX == 0 && rotationY == 0;
    }
    public void getMatrix(Matrix outMatrix) {
        if (outMatrix == null) return;
        java.awt.geom.AffineTransform at = macapkAffine();
        outMatrix.setValues(new float[] {
            (float) at.getScaleX(), (float) at.getShearX(), (float) at.getTranslateX(),
            (float) at.getShearY(), (float) at.getScaleY(), (float) at.getTranslateY(),
            0f, 0f, 1f
        });
    }
    public void getInverseMatrix(Matrix outMatrix) {
        if (outMatrix == null) return;
        Matrix m = new Matrix();
        getMatrix(m);
        m.invert(outMatrix);
    }
    /** Transform WITHOUT the left/top position translate (the parent
     *  applies position separately, matching AOSP getMatrix semantics). */
    private java.awt.geom.AffineTransform macapkAffine() {
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        // Pre-concatenating (right-multiply) in this order yields the
        // point-transform M = T(t)*T(p)*Rz*S*T(-p).
        at.translate(translationX, translationY);
        float px = getPivotX(), py = getPivotY();
        if (rotationZ != 0f || scaleX != 1f || scaleY != 1f) {
            at.translate(px, py);
            if (rotationZ != 0f) at.rotate(Math.toRadians(rotationZ));
            if (scaleX != 1f || scaleY != 1f) at.scale(scaleX, scaleY);
            at.translate(-px, -py);
        }
        return at;
    }

    // ---- Replay helpers (used by Canvas.drawRenderNode) ----------------
    /** Apply this node's transform (NOT position) to a target canvas. */
    void macapkApplyTransform(Canvas c) {
        c.translate(translationX, translationY);
        if (rotationZ == 0f && scaleX == 1f && scaleY == 1f) return;
        float px = getPivotX(), py = getPivotY();
        c.translate(px, py);
        if (rotationZ != 0f) c.rotate(rotationZ);
        if (scaleX != 1f || scaleY != 1f) c.scale(scaleX, scaleY);
        c.translate(-px, -py);
    }
    /** Clip the target canvas to this node's clip-bounds / outline. */
    void macapkApplyClip(Canvas c) {
        if (clipToBounds) c.clipRect(0, 0, getWidth(), getHeight());
        if (hasClipRect) c.clipRect(clipRect.left, clipRect.top, clipRect.right, clipRect.bottom);
        macapkApplyOutlineClip(c);
    }
    /** The outline half of macapkApplyClip: what is left to clip once an alpha
     *  layer's bounds have taken the bounds / clip-rect flags (hwui
     *  setViewProperties "all clipping done by savelayer"). */
    void macapkApplyOutlineClip(Canvas c) {
        if (clipToOutline && outline != null && !outline.isEmpty()) {
            if (outline.mMode == Outline.MODE_PATH && outline.mPath != null) {
                c.clipPath(outline.mPath);
            } else if (outline.mMode == Outline.MODE_ROUND_RECT) {
                if (outline.mRadius > 0f) {
                    Path p = new Path();
                    p.addRoundRect(outline.mRect.left, outline.mRect.top,
                                   outline.mRect.right, outline.mRect.bottom,
                                   outline.mRadius, outline.mRadius, Path.Direction.CW);
                    c.clipPath(p);
                } else {
                    c.clipRect(outline.mRect.left, outline.mRect.top,
                               outline.mRect.right, outline.mRect.bottom);
                }
            }
        }
    }
    float macapkAlpha() { return alpha; }
    /** setUseCompositingLayer's paint (its alpha scales the layer composite). */
    Paint macapkLayerPaint() { return layerPaint; }
    /** hwui RenderProperties::getClippingRectForFlags for this node's flags, or
     *  (0, 0, w, h) when it has none: the bounds of the alpha layer
     *  (RenderNodeDrawable.cpp:397-406). {l, t, r, b}; empty when the clip
     *  rect misses the bounds. */
    float[] macapkLayerBounds() {
        float l = 0, t = 0, r = getWidth(), b = getHeight();
        if (clipToBounds) {
            if (hasClipRect) {
                l = Math.max(l, clipRect.left);  t = Math.max(t, clipRect.top);
                r = Math.min(r, clipRect.right); b = Math.min(b, clipRect.bottom);
            }
        } else if (hasClipRect) {
            l = clipRect.left; t = clipRect.top; r = clipRect.right; b = clipRect.bottom;
        }
        return new float[] { l, t, r, b };
    }
    /** The clip of a node that IS a layer, in hwui's setViewProperties
     *  (RenderNodeDrawable.cpp:392 android14): with alpha < 1,
     *  `clipFlags &= ~CLIP_TO_BOUNDS;  // bounds clipping done by layer`, so
     *  only the clip rect and the outline clip. At alpha 1 it is the full clip.
     *  What the hole pre-pass of a layer runs under. */
    void macapkApplyLayerClip(Canvas c) {
        if (alpha < 1f) {
            if (hasClipRect) c.clipRect(clipRect.left, clipRect.top, clipRect.right, clipRect.bottom);
            macapkApplyOutlineClip(c);
        } else {
            macapkApplyClip(c);
        }
    }

    // ---- How the op-list replay draws this node (hwui's layer types) --------
    /** Straight onto the target canvas. */
    static final int KIND_PLAIN = 0;
    /** An hwui RenderLayer: setUseCompositingLayer, an image filter (a blur, the
     *  only RenderEffect our replay applies), or an alpha node promoted to one.
     *  RenderProperties.h:553-558 android14:
     *      bool promotedToLayer() const {
     *          return mLayerProperties.mType == LayerType::None && fitsOnLayer() &&
     *                 (mComputedFields.mNeedLayerForFunctors || mLayerProperties.mImageFilter != nullptr ||
     *                  mLayerProperties.getStretchEffect().requiresLayer() ||
     *                  (!MathUtils::isZero(mPrimitiveFields.mAlpha) && mPrimitiveFields.mAlpha < 1 &&
     *                   mPrimitiveFields.mHasOverlappingRendering));
     *  So the everyday faded Compose layer is a RenderLayer on a device, not the
     *  saveLayerAlpha fallback of setViewProperties. */
    static final int KIND_LAYER = 1;
    /** alpha < 1 with hasOverlappingRendering false: hwui draws the list through
     *  an AlphaFilterCanvas (ModulateAlpha), no layer. Our replay approximates it
     *  with the saveLayerAlpha it always used. */
    static final int KIND_MODULATE = 2;
    /** alpha < 1, overlapping, too big for a layer: setViewProperties' real
     *  saveLayerAlpha (RenderNodeDrawable.cpp:397-406), which a hole cannot
     *  leave. */
    static final int KIND_SWALLOW = 3;
    /** hwui fitsOnLayer's bound is DeviceInfo::maxTextureSize, the same number
     *  AOSP's RecordingCanvas answers getMaximumBitmapWidth/Height with
     *  (nGetMaximumTextureWidth); ours answers 32766 (Canvas), so this is it. */
    static final int MAX_LAYER_SIZE = 32766;
    int macapkLayerKind() {
        if (useCompositingLayer || (mRenderEffect != null && mRenderEffect.macapkIsBlur())) return KIND_LAYER;
        if (alpha < 1f) {
            if (!hasOverlappingRendering) return KIND_MODULATE;
            return (getWidth() <= MAX_LAYER_SIZE && getHeight() <= MAX_LAYER_SIZE) ? KIND_LAYER : KIND_SWALLOW;
        }
        return KIND_PLAIN;
    }

    /** hwui RenderNode::nothingToDraw (RenderNode.h, android14): an empty list,
     *  alpha <= 0, a clip-to-outline with an EMPTY outline, or a zero scale.
     *  The op-list replay skips such a node entirely. */
    boolean macapkNothingToDraw() {
        return alpha <= 0f || scaleX == 0f || scaleY == 0f
            || (clipToOutline && outline != null && outline.isEmpty());
    }
    /** Package hook: the attached RenderEffect (blur) for Canvas.drawRenderNode. */
    RenderEffect macapkRenderEffect() { return mRenderEffect; }

    /** Convenience replay used by some androidx layer impls
     *  (renderNode.drawInto(canvas)); delegates to the canvas so all the
     *  Graphics2D-level compositing lives in one place. */
    public void drawInto(Canvas target) { if (target != null) target.drawRenderNode(this); }
}
