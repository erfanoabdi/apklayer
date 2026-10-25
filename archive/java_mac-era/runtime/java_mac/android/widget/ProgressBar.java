// android.widget.ProgressBar -- horizontal/circular progress indicator, and
// the shared superclass of SeekBar/RatingBar (via AbsSeekBar). AOSP:
//   frameworks/base/core/java/android/widget/ProgressBar.java
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class ProgressBar extends View {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private int max = 100;
    private int min = 0;
    private int progress = 0;
    private int secondaryProgress = 0;
    private boolean indeterminate;

    /** Full sweep of the synthetic spinning indeterminate arc, in
     *  milliseconds. AOSP's real indeterminate timing comes from the
     *  themed indeterminateDrawable's own animation (indeterminateDuration
     *  defaults vary by style); we have no themed-drawable pipeline, so we
     *  pick one fixed, reasonable rate rather than trying to model it. */
    private static final long INDETERMINATE_PERIOD_MS = 1200;
    private final android.graphics.Paint mPaint = new android.graphics.Paint();
    private Handler mAnimHandler;
    private Runnable mAnimTick;
    private boolean mAnimRunning;

    public ProgressBar(Context context) { super(context); }
    public ProgressBar(Context context, AttributeSet attrs) {
        // Was this(context, attrs, 0): the same two calls, plus the styled
        // drawable read under the class's DEFAULT style (AOSP chains this
        // constructor to this(context, attrs, R.attr.progressBarStyle)).
        super(context, attrs, 0);
        macapkApplyAttrs(attrs);
        macapkApplyStyledDrawables(context, attrs, macapkDefaultStyleAttr(), 0);
        macapkApplyStyledSize(context, attrs, macapkDefaultStyleAttr(), 0);
    }
    public ProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        macapkApplyAttrs(attrs);
        macapkApplyStyledDrawables(context, attrs, defStyleAttr, 0);
        macapkApplyStyledSize(context, attrs, defStyleAttr, 0);
    }
    public ProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        macapkApplyAttrs(attrs);
        macapkApplyStyledDrawables(context, attrs, defStyleAttr, defStyleRes);
        macapkApplyStyledSize(context, attrs, defStyleAttr, defStyleRes);
    }

    /** android.R.attr maxWidth / maxHeight / indeterminate / indeterminateOnly /
     *  minWidth / minHeight (SDK-36 public ids, ascending). */
    private static final int[] MACAPK_STYLED_SIZE_ATTRS =
        { 0x0101011f, 0x01010120, 0x01010139, 0x0101013a, 0x0101013f, 0x01010140 };

    /** AOSP ProgressBar constructor (android14 :298-301, :330-336): the min/max
     *  size clamp and indeterminate[Only] come from the STYLE as much as from the
     *  XML. Never read, so a bar built as `new ProgressBar(ctx, null,
     *  progressBarStyleLarge)` (leanback's ProgressBarManager, SmartTube's player
     *  spinner) had neither its 76dp clamp nor its indeterminate mode. */
    private void macapkApplyStyledSize(Context context, AttributeSet attrs,
                                       int defStyleAttr, int defStyleRes) {
        if (context == null) return;
        android.content.res.TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, MACAPK_STYLED_SIZE_ATTRS, defStyleAttr, defStyleRes);
            mMaxWidth = a.getDimensionPixelSize(0, mMaxWidth);
            mMaxHeight = a.getDimensionPixelSize(1, mMaxHeight);
            mMinWidth = a.getDimensionPixelSize(4, mMinWidth);
            mMinHeight = a.getDimensionPixelSize(5, mMinHeight);
            boolean only = a.getBoolean(3, false);
            boolean ind = only || a.getBoolean(2, indeterminate);
            if (ind != indeterminate) setIndeterminate(ind);
        } catch (Throwable t) {
            // An unreadable style must never break construction.
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) {}
        }
    }

    /** AOSP ProgressBar.onMeasure (android14 :2187-2205): the current drawable's
     *  intrinsic size clamped into [min, max], plus padding, resolved against the
     *  spec. There was no override, so a wrap_content bar took View's default
     *  (the whole AT_MOST size): SmartTube's player spinner filled the window as
     *  a stretched ellipse. */
    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int dw = 0, dh = 0;
        final android.graphics.drawable.Drawable d = mCurrentDrawable;
        if (d != null) {
            dw = Math.max(mMinWidth, Math.min(mMaxWidth, d.getIntrinsicWidth()));
            dh = Math.max(mMinHeight, Math.min(mMaxHeight, d.getIntrinsicHeight()));
        }
        dw += getPaddingLeft() + getPaddingRight();
        dh += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSizeAndState(dw, widthMeasureSpec, 0),
                resolveSizeAndState(dh, heightMeasureSpec, 0));
    }

    // ---- the styled drawables (MACAPK_PROGRESS_STYLED_DRAWABLES) -------------
    //
    // AOSP android14-release ProgressBar.java:277-326, in the constructor:
    //     final TypedArray a = context.obtainStyledAttributes(
    //             attrs, R.styleable.ProgressBar, defStyleAttr, defStyleRes);
    //     final Drawable progressDrawable = a.getDrawable(R.styleable.ProgressBar_progressDrawable);
    //     if (progressDrawable != null) { if (needsTileify(progressDrawable))
    //         setProgressDrawableTiled(progressDrawable); else setProgressDrawable(progressDrawable); }
    //     ...
    //     final Drawable indeterminateDrawable = a.getDrawable(
    //             R.styleable.ProgressBar_indeterminateDrawable);
    //     if (indeterminateDrawable != null) { ... setIndeterminateDrawable[Tiled](...) }
    // and :264 `this(context, attrs, com.android.internal.R.attr.progressBarStyle)`
    // for the (Context, AttributeSet) form. Ours never read either, so
    // getProgressDrawable()/getIndeterminateDrawable() answered null for every
    // XML ProgressBar and SeekBar. MEASURED on NewPipe (lanes19 media-playback,
    // 2026-09-29): VideoPlayerUi.<init> does
    //     playbackSeekBar.getThumb().setColorFilter(RED, SRC_IN)
    //     playbackSeekBar.getProgressDrawable().setColorFilter(RED, MULTIPLY)
    //     progressBarLoadingPanel.getIndeterminateDrawable().setColorFilter(WHITE, MULTIPLY)
    // and threw NullPointerException out of PlayerService.onStartCommand, so no
    // video ever played. AbsSeekBar reads `thumb` the same way.
    // =0: no styled read at all, exactly as before.
    static final boolean PROGRESS_STYLED_DRAWABLES =
        !"0".equals(System.getenv("MACAPK_PROGRESS_STYLED_DRAWABLES"));
    /** android.R.attr.progressDrawable / indeterminateDrawable (SDK-34 R$attr). */
    private static final int[] MACAPK_STYLED_PROGRESS_ATTRS = { 0x0101013c, 0x0101013b };
    /** True while mIndeterminateDrawable is the one the STYLE supplied (not an
     *  app's setIndeterminateDrawable); see onDraw's AnimatedVectorDrawable note. */
    private boolean mIndeterminateFromStyle;

    /** The default style attr AOSP's (Context, AttributeSet) constructor chains
     *  to: progressBarStyle here; SeekBar and RatingBar name theirs, and
     *  AbsSeekBar's own two-argument form has none (this(c, a, 0)). */
    int macapkDefaultStyleAttr() { return 0x01010077; /* android.R.attr.progressBarStyle */ }

    private void macapkApplyStyledDrawables(Context context, AttributeSet attrs,
                                            int defStyleAttr, int defStyleRes) {
        if (!PROGRESS_STYLED_DRAWABLES || context == null) return;
        android.content.res.TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, MACAPK_STYLED_PROGRESS_ATTRS,
                    defStyleAttr, defStyleRes);
            android.graphics.drawable.Drawable progress = a.hasValue(0) ? a.getDrawable(0) : null;
            android.graphics.drawable.Drawable indet = a.hasValue(1) ? a.getDrawable(1) : null;
            if (progress != null) {
                if (macapkNeedsTileify(progress)) setProgressDrawableTiled(progress);
                else setProgressDrawable(progress);
            }
            if (indet != null) {
                if (macapkNeedsTileify(indet)) setIndeterminateDrawableTiled(indet);
                else setIndeterminateDrawable(indet);
                mIndeterminateFromStyle = true;
            }
        } catch (Throwable t) {
            // A drawable that cannot be built must never break construction.
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) {}
        }
    }

    /** AOSP :492-527 needsTileify: true when a BitmapDrawable (the thing
     *  tileify rewrites) sits anywhere in a Layer/StateList tree. */
    private static boolean macapkNeedsTileify(android.graphics.drawable.Drawable dr) {
        if (dr instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable orig = (android.graphics.drawable.LayerDrawable) dr;
            for (int i = 0, n = orig.getNumberOfLayers(); i < n; i++) {
                if (macapkNeedsTileify(orig.getDrawable(i))) return true;
            }
            return false;
        }
        if (dr instanceof android.graphics.drawable.StateListDrawable) {
            android.graphics.drawable.StateListDrawable in = (android.graphics.drawable.StateListDrawable) dr;
            for (int i = 0, n = in.getStateCount(); i < n; i++) {
                if (macapkNeedsTileify(in.getStateDrawable(i))) return true;
            }
            return false;
        }
        return dr instanceof android.graphics.drawable.BitmapDrawable;
    }

    /** AOSP doRefreshProgress (:1090-1128) -> setVisualProgress (:1650-1672), one id
     *  at a time exactly as AOSP calls it: the level of the layer named by `id`
     *  (R.id.progress or R.id.secondaryProgress; the WHOLE drawable when a
     *  LayerDrawable has no such layer, or the drawable is not a LayerDrawable)
     *  is scale * MAX_LEVEL. AOSP refreshes the PRIMARY id from setProgress /
     *  setMax / setMin, the SECONDARY id from setSecondaryProgress, and BOTH (in
     *  that order) only when the drawable itself changes (setProgressDrawable).
     *  Refreshing both on every change would let the secondary value overwrite
     *  the primary level of a drawable with no layers. */
    private void macapkRefreshLevel(int id) {
        // Under MACAPK_PROGRESS_STYLED_DRAWABLES=0 no level is ever set, so the
        // =0 arm is the pre-change widget for an app-installed drawable too.
        if (!PROGRESS_STYLED_DRAWABLES) return;
        final android.graphics.drawable.Drawable cur = mCurrentDrawable;
        if (cur == null || indeterminate) return;
        final int range = max - min;
        final int value = id == 0x0102000f /* R.id.secondaryProgress */ ? secondaryProgress : progress;
        macapkSetVisualLevel(cur, id, range > 0 ? (value - min) / (float) range : 0f);
    }
    private void macapkRefreshPrimary()   { macapkRefreshLevel(0x0102000d /* R.id.progress */); }
    private void macapkRefreshSecondary() { macapkRefreshLevel(0x0102000f /* R.id.secondaryProgress */); }
    private static void macapkSetVisualLevel(android.graphics.drawable.Drawable cur, int id, float scale) {
        android.graphics.drawable.Drawable d = cur;
        if (d instanceof android.graphics.drawable.LayerDrawable) {
            d = ((android.graphics.drawable.LayerDrawable) d).findDrawableByLayerId(id);
            if (d == null) d = cur;
        }
        try { d.setLevel((int) (Math.max(0f, Math.min(1f, scale)) * 10000)); } catch (Throwable ignored) {}
    }

    /** AOSP ProgressBar(Context, AttributeSet, int, int) reads
     *  android:min/max/progress/secondaryProgress/indeterminate from the
     *  styled attributes at construction time (a.getInt(R.styleable.
     *  ProgressBar_min/_max/_progress/_secondaryProgress, ...) and
     *  a.getBoolean(R.styleable.ProgressBar_indeterminate, ...)). Our
     *  obtainStyledAttributes/TypedArray pipeline only resolves framework
     *  attrs it has an id->name mapping for (see ImageView's android:src),
     *  and teaching it every ProgressBar attr id belongs to TypedArray.java,
     *  not here -- so, matching the established pattern already used by
     *  android.view.ViewStub (which reads android:layout/inflatedId the
     *  same way), we read these directly off the AttributeSet by NAME
     *  instead. Was entirely absent: both AttributeSet constructors called
     *  super(context), discarding attrs outright, so every XML-declared
     *  <ProgressBar android:indeterminate="true"/> (the standard "loading
     *  spinner" markup) silently reported isIndeterminate()==false and any
     *  android:max/progress/secondaryProgress from XML was ignored.
     *  SeekBar/RatingBar inherit this fix for free -- their AttributeSet
     *  constructors chain through AbsSeekBar into this one. */
    private void macapkApplyAttrs(AttributeSet attrs) {
        if (attrs == null) return;
        try {
            min = attrs.getAttributeIntValue(ANDROID_NS, "min", min);
            max = attrs.getAttributeIntValue(ANDROID_NS, "max", max);
            progress = attrs.getAttributeIntValue(ANDROID_NS, "progress", progress);
            secondaryProgress = attrs.getAttributeIntValue(
                ANDROID_NS, "secondaryProgress", secondaryProgress);
            indeterminate = attrs.getAttributeBooleanValue(
                ANDROID_NS, "indeterminate", indeterminate);
        } catch (Throwable ignored) {}
    }

    public synchronized int getMax()                 { return max; }
    public synchronized void setMax(int max)         { this.max = max; macapkRefreshPrimary(); invalidate(); }
    public synchronized int getMin()                 { return min; }
    public synchronized void setMin(int min)         { this.min = min; macapkRefreshPrimary(); invalidate(); }
    public synchronized int getProgress()            { return progress; }
    public synchronized void setProgress(int p)      { this.progress = p; macapkRefreshPrimary(); invalidate(); }
    public synchronized void setProgress(int p, boolean animate) { this.progress = p; macapkRefreshPrimary(); invalidate(); }
    public synchronized int getSecondaryProgress()   { return secondaryProgress; }
    public synchronized void setSecondaryProgress(int p) { this.secondaryProgress = p; macapkRefreshSecondary(); invalidate(); }
    public synchronized boolean isIndeterminate()    { return indeterminate; }
    public synchronized void setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        // AOSP swaps which drawable is current here, so a caller that set BOTH
        // drawables and then flips the mode sees the right one become active.
        // Without this the getters stay correct but mCurrentDrawable does not,
        // and the widget would paint the wrong drawable after a mode change.
        macapkSwapCurrentDrawable(indeterminate ? mIndeterminateDrawable : mProgressDrawable);
        // AFTER the swap (a refresh before it set the levels on the OUTGOING
        // indeterminate drawable and left the progress drawable stale). AOSP
        // setIndeterminate (:674-690) only swaps drawables and starts/stops the
        // animation, because AOSP's setProgress is a no-op while indeterminate
        // (setProgressInternal: `if (mIndeterminate) return false;`) so its
        // drawable levels can never go stale; ours stores the value, so the
        // returning progress drawable is brought up to it here: the primary level
        // always, the secondary one only for a layered drawable (on a plain one it
        // would overwrite the primary level, see macapkRefreshLevel).
        macapkRefreshPrimary();
        if (mCurrentDrawable instanceof android.graphics.drawable.LayerDrawable) macapkRefreshSecondary();
        macapkUpdateAnim();
        invalidate();
    }
    public synchronized void incrementProgressBy(int diff)          { this.progress += diff; macapkRefreshPrimary(); invalidate(); }
    public synchronized void incrementSecondaryProgressBy(int diff) { this.secondaryProgress += diff; macapkRefreshSecondary(); invalidate(); }
    // ---- progress / indeterminate drawables ---------------------------------
    // These were `return null` / no-op stubs, justified by "no caller ever
    // populates them". That premise was FALSE, and the cost was large: Material
    // Components' BaseProgressIndicator keeps NO backing field of its own and
    // uses android.widget.ProgressBar as its only store, in BOTH directions.
    // Measured 2026-07-30 with javap -c on the guest's own
    // com/google/android/material/progressindicator/BaseProgressIndicator.class:
    //
    //   setIndeterminateDrawable: invokespecial ProgressBar.setIndeterminateDrawable  <- discarded
    //   getIndeterminateDrawable: invokespecial ProgressBar.getIndeterminateDrawable  <- always null
    //   getCurrentDrawingDelegate(): reads getIndeterminateDrawable()                 <- so, null
    //   onMeasure: `ifnonnull` on that delegate, else `monitorexit; return`
    //                                        ^ setMeasuredDimension() NEVER CALLED
    //
    // A View that returns from onMeasure without setting a measured dimension
    // gets View.java:2260's fallback -- the FULL spec size. So the indicator
    // measured 1684px instead of ~4dp, and on Organic Maps that one number
    // cascaded through RelativeLayout/layout_above until the icon, title, body
    // text and Download button all collapsed to zero height.
    //
    // Blast radius measured across the corpus: 12 of 62 APKs bundle Material
    // progressindicator (Signal, VLC, Wikipedia, AnkiDroid, AntennaPod, F-Droid...).
    //
    // Mirrors AOSP android13-release ProgressBar. Omitted deliberately, with
    // reasons: applyProgressTints/applyIndeterminateTint (no tint pipeline here),
    // doRefreshProgress (no level-based LayerDrawable), setLayoutDirection (not
    // modelled), and the mMaxHeight bump (our onMeasure does not consult it).
    // What IS mirrored is everything an app can OBSERVE: the field round-trip,
    // callback ownership, and which drawable is current for a given mode.
    private android.graphics.drawable.Drawable mProgressDrawable;
    private android.graphics.drawable.Drawable mIndeterminateDrawable;
    private android.graphics.drawable.Drawable mCurrentDrawable;

    public android.graphics.drawable.Drawable getProgressDrawable() { return mProgressDrawable; }

    public void setProgressDrawable(android.graphics.drawable.Drawable d) {
        if (mProgressDrawable == d) return;
        if (mProgressDrawable != null) {
            mProgressDrawable.setCallback(null);
            try { unscheduleDrawable(mProgressDrawable); } catch (Throwable ignored) {}
        }
        mProgressDrawable = d;
        if (d != null) {
            d.setCallback(this);
            if (d.isStateful()) d.setState(getDrawableState());
        }
        // AOSP applies pending tints here too, not only in the tint setters: XML
        // inflation and AppCompat both commonly set the tint BEFORE the drawable, and
        // without this the tint would be stored and silently never land.
        applyPrimaryProgressTint();
        applyProgressBackgroundTint();
        if (!indeterminate) {
            // Swap the FIELD, not the local d: getTintTarget mutate()s and reassigns
            // mProgressDrawable, so d can now be the stale pre-mutate instance and we
            // would display an untinted copy while holding the tinted one.
            macapkSwapCurrentDrawable(mProgressDrawable);
            // AOSP setProgressDrawable: doRefreshProgress(progress) then (secondaryProgress).
            macapkRefreshPrimary();
            macapkRefreshSecondary();
            postInvalidate();
        }
    }

    public android.graphics.drawable.Drawable getIndeterminateDrawable() { return mIndeterminateDrawable; }

    public void setIndeterminateDrawable(android.graphics.drawable.Drawable d) {
        mIndeterminateFromStyle = false;   // the style's installer re-sets it after this call
        if (mIndeterminateDrawable == d) return;
        if (mIndeterminateDrawable != null) {
            mIndeterminateDrawable.setCallback(null);
            try { unscheduleDrawable(mIndeterminateDrawable); } catch (Throwable ignored) {}
        }
        mIndeterminateDrawable = d;
        if (d != null) {
            d.setCallback(this);
            if (d.isStateful()) d.setState(getDrawableState());
        }
        applyIndeterminateTint();   // same reason as setProgressDrawable
        if (indeterminate) {
            // Field, not d -- applyIndeterminateTint mutate()s and reassigns it.
            macapkSwapCurrentDrawable(mIndeterminateDrawable);
            postInvalidate();
        }
    }

    /** AOSP ProgressBar.swapCurrentDrawable. The visibility handover matters:
     *  an AnimationDrawable only advances while visible, so leaving the old one
     *  visible leaks a running animation. */
    private void macapkSwapCurrentDrawable(android.graphics.drawable.Drawable newDrawable) {
        final android.graphics.drawable.Drawable old = mCurrentDrawable;
        mCurrentDrawable = newDrawable;
        if (old != mCurrentDrawable) {
            if (old != null) old.setVisible(false, false);
            if (mCurrentDrawable != null) mCurrentDrawable.setVisible(isShown(), false);
        }
    }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        macapkUpdateAnim();
    }
    @Override public void onDetachedFromWindow() {
        try { super.onDetachedFromWindow(); } catch (Throwable ignored) {}
        mAnimRunning = false;
        if (mAnimHandler != null && mAnimTick != null) mAnimHandler.removeCallbacks(mAnimTick);
    }

    /** Start/stop the indeterminate redraw tick to match the current
     *  (attached && indeterminate) state. Mirrors android.widget.
     *  Chronometer's own Handler.postDelayed self-repeating tick -- the
     *  established pattern in this package for a continuously-animating
     *  widget -- so an indeterminate spinner keeps turning while attached
     *  and stops cleanly (no leaked callbacks) once detached. */
    private synchronized void macapkUpdateAnim() {
        boolean want = indeterminate && isAttachedToWindow();
        if (want == mAnimRunning) return;
        mAnimRunning = want;
        if (want) {
            if (mAnimHandler == null) mAnimHandler = new Handler(Looper.getMainLooper());
            if (mAnimTick == null) {
                mAnimTick = new Runnable() {
                    @Override public void run() {
                        // v1.0.1643: also stop the moment the view is no longer
                        // attached. onDetachedFromWindow is the normal stop, but
                        // an app subclass's override can throw before reaching
                        // super (our dispatch swallows it) -- measured: a
                        // Material LinearProgressIndicator in a destroyed
                        // Wikipedia screen kept this tick alive, and the queued
                        // message pinned the whole activity.
                        if (mAnimRunning && isAttachedToWindow()) {
                            invalidate();
                            mAnimHandler.postDelayed(mAnimTick, 50);
                        } else {
                            mAnimRunning = false;
                        }
                    }
                };
            }
            mAnimHandler.postDelayed(mAnimTick, 50);
        } else if (mAnimHandler != null && mAnimTick != null) {
            mAnimHandler.removeCallbacks(mAnimTick);
        }
    }

    /** AOSP ProgressBar.onDraw -> drawTrack paints mCurrentDrawable (the
     *  progressDrawable or indeterminateDrawable, a themed LayerDrawable)
     *  at the current level, driving the indeterminate case's Animation
     *  via postInvalidateOnAnimation. We have no themed-drawable pipeline
     *  (getProgressDrawable()/getIndeterminateDrawable() above are stubs
     *  no caller ever populates), so we paint the observable equivalent
     *  chrome directly: for determinate mode, the same three-layer stack
     *  AOSP's progressDrawable uses (background track, secondaryProgress,
     *  primary progress) as plain fills; for indeterminate, a spinning
     *  arc. Was ABSENT entirely -- View's own onDraw is a bare no-op, so
     *  no ProgressBar/SeekBar/RatingBar ever painted a single pixel
     *  regardless of state. */
    @Override
    protected synchronized void onDraw(android.graphics.Canvas canvas) {
        if (canvas == null) return;
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        int sv = canvas.save();
        // AOSP drawTrack: canvas is already translated to (left, top) by the
        // parent's dispatchDraw, so paint in LOCAL coords starting at the
        // padding offset (mirrors View.draw's own local-coord convention).
        canvas.translate(getPaddingLeft(), getPaddingTop());
        // If a caller supplied a real drawable, AOSP draws THAT and nothing
        // else -- drawTrack's whole body is `final Drawable d = mCurrentDrawable;
        // if (d != null) { ...; d.draw(canvas); }`. Now that the setters are no
        // longer no-ops this branch is reachable, and honouring it matters: a
        // Material indicator supplies its own DrawingDelegate-backed drawable,
        // so painting our synthesized chrome underneath it would double-draw.
        // The synthesized fills below remain the fallback for the (common) case
        // of a plain ProgressBar with no drawable set.
        // A STYLE-supplied indeterminate drawable is RETURNED by
        // getIndeterminateDrawable() (what apps read and tint) but not drawn:
        // AOSP animates the platform spinner (Material's progress_medium_material
        // is an animated-vector) through Animatable.start(), and here that
        // cannot happen yet -- AnimatedVectorDrawable.start() only fires its
        // callbacks, and the platform animated-vector currently decodes to a
        // ColorDrawable (debug/progressdrawable_gate.sh D3 prints the class).
        // Drawing it would freeze every themed spinner, or paint it as a solid
        // block, so the synthesized spinning arc below keeps drawing, exactly as
        // before. An app's own setIndeterminateDrawable is drawn as before.
        final boolean styledStaticSpinner = indeterminate && mIndeterminateFromStyle;
        if (mCurrentDrawable != null && !styledStaticSpinner) {
            mCurrentDrawable.setBounds(0, 0, w, h);
            mCurrentDrawable.draw(canvas);
            canvas.restoreToCount(sv);
            return;
        }
        mPaint.setAntiAlias(true);
        if (indeterminate) {
            int d = Math.min(w, h);
            float left = (w - d) / 2f, top = (h - d) / 2f;
            mPaint.setStyle(android.graphics.Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(2f, d * 0.1f));
            mPaint.setColor(android.content.res.Resources.macapkThemeColor(
                "colorPrimary", 0xFF1976D2));
            long phase = SystemClock.uptimeMillis() % INDETERMINATE_PERIOD_MS;
            float startAngle = phase * 360f / INDETERMINATE_PERIOD_MS;
            canvas.drawArc(left, top, left + d, top + d, startAngle, 100f, false, mPaint);
        } else {
            int range = Math.max(1, max - min);
            float progFrac = macapkClamp01((progress - min) / (float) range);
            float secFrac  = macapkClamp01((secondaryProgress - min) / (float) range);
            mPaint.setStyle(android.graphics.Paint.Style.FILL);
            mPaint.setColor(0x33808080);
            canvas.drawRect(0, 0, w, h, mPaint);
            if (secFrac > 0f) {
                mPaint.setColor(0x663DDC84);
                canvas.drawRect(0, 0, w * secFrac, h, mPaint);
            }
            if (progFrac > 0f) {
                mPaint.setColor(android.content.res.Resources.macapkThemeColor(
                    "colorPrimary", 0xFF1976D2));
                canvas.drawRect(0, 0, w * progFrac, h, mPaint);
            }
        }
        canvas.restoreToCount(sv);
    }

    private static float macapkClamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    // Tints. These used to STORE the ColorStateList and never apply it -- an API that
    // exists, returns the value you set, and changes nothing on screen. That is the
    // failure mode this project keeps rediscovering, so the setters below actually push
    // the tint into the drawable, mirroring frameworks/base android13-release.
    //
    // Layer targeting: AOSP resolves the primary/background layers out of a LayerDrawable
    // by the PUBLIC framework resource ids below (stable across releases, which is why
    // hardcoding them is correct rather than a per-APK constant). Our <layer-list> parser
    // does record item ids (Resources.macapkInflateLayerList reads the android:id
    // attribute), so the lookup can hit. When it does not, AOSP's own fallback applies:
    // getTintTarget(..., shouldFallback=true) tints the whole drawable for the PRIMARY
    // progress, and the background tint is simply skipped -- identical to real Android on
    // a progress drawable that is not a layer-list.
    private static final int MACAPK_ID_PROGRESS   = 0x0102000d;  // @android:id/progress
    private static final int MACAPK_ID_BACKGROUND = 0x01020000;  // @android:id/background

    private android.content.res.ColorStateList mProgressTintList;
    private android.content.res.ColorStateList mProgressBackgroundTintList;
    private android.content.res.ColorStateList mIndeterminateTintList;

    public void setProgressTintList(android.content.res.ColorStateList tint) {
        mProgressTintList = tint;
        if (mProgressDrawable != null) applyPrimaryProgressTint();
    }
    public android.content.res.ColorStateList getProgressTintList() { return mProgressTintList; }

    public void setProgressBackgroundTintList(android.content.res.ColorStateList tint) {
        mProgressBackgroundTintList = tint;
        if (mProgressDrawable != null) applyProgressBackgroundTint();
    }
    public android.content.res.ColorStateList getProgressBackgroundTintList() { return mProgressBackgroundTintList; }

    /** AOSP setIndeterminateTintList. Note it does NOT go through the layer lookup at all:
     *  the indeterminate drawable is tinted directly, so this works regardless of whether
     *  the drawable is a layer-list. */
    public void setIndeterminateTintList(android.content.res.ColorStateList tint) {
        mIndeterminateTintList = tint;
        applyIndeterminateTint();
    }
    public android.content.res.ColorStateList getIndeterminateTintList() { return mIndeterminateTintList; }

    private void applyPrimaryProgressTint() {
        if (mProgressTintList == null) return;
        android.graphics.drawable.Drawable target = getTintTarget(MACAPK_ID_PROGRESS, true);
        if (target == null) return;
        target.setTintList(mProgressTintList);
        // AOSP re-pushes state after tinting, because a stateful drawable needs to
        // re-resolve which ColorStateList entry applies now that the list changed.
        if (target.isStateful()) target.setState(getDrawableState());
    }

    private void applyProgressBackgroundTint() {
        if (mProgressBackgroundTintList == null) return;
        android.graphics.drawable.Drawable target = getTintTarget(MACAPK_ID_BACKGROUND, false);
        if (target == null) return;
        target.setTintList(mProgressBackgroundTintList);
        if (target.isStateful()) target.setState(getDrawableState());
    }

    private void applyIndeterminateTint() {
        if (mIndeterminateDrawable == null || mIndeterminateTintList == null) return;
        // mutate() first: AOSP does, so tinting one ProgressBar cannot bleed into every
        // other view sharing the same constant-state drawable instance.
        mIndeterminateDrawable = mIndeterminateDrawable.mutate();
        mIndeterminateDrawable.setTintList(mIndeterminateTintList);
        if (mIndeterminateDrawable.isStateful()) {
            mIndeterminateDrawable.setState(getDrawableState());
        }
    }

    /** AOSP getTintTarget. Mutates the progress drawable, then finds the requested layer
     *  inside a LayerDrawable; shouldFallback means "if there is no such layer, tint the
     *  whole drawable instead". */
    private android.graphics.drawable.Drawable getTintTarget(int layerId, boolean shouldFallback) {
        android.graphics.drawable.Drawable layer = null;
        android.graphics.drawable.Drawable d = mProgressDrawable;
        if (d != null) {
            mProgressDrawable = d.mutate();
            d = mProgressDrawable;
            if (d instanceof android.graphics.drawable.LayerDrawable) {
                layer = ((android.graphics.drawable.LayerDrawable) d).findDrawableByLayerId(layerId);
            }
            if (shouldFallback && layer == null) layer = d;
        }
        return layer;
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.ProgressBar had 33
    // public members missing -- the whole tint-mode half of the tint API, the
    // min/max size accessors, the indeterminate interpolator, and the two
    // "Tiled" drawable setters). Bodies mirror
    // frameworks/base/core/java/android/widget/ProgressBar.java
    // (android16-release); cited line numbers are from that file.
    // ==================================================================

    // @android:id/secondaryProgress -- the third layer id AOSP's tint code
    // looks up, alongside MACAPK_ID_PROGRESS/MACAPK_ID_BACKGROUND above.
    // Value from frameworks/base/core/res/res/values/public.xml
    //   <public type="id" name="secondaryProgress" id="0x0102000f" />
    private static final int MACAPK_ID_SECONDARY = 0x0102000f;

    private android.content.res.ColorStateList mSecondaryProgressTintList;

    // AOSP stores ONE BlendMode per tint and derives the PorterDuff.Mode from
    // it (ProgressBar.ProgressTintInfo.mProgressBlendMode etc., :2416). We do
    // the same: the BlendMode is the storage, and getXTintMode() converts.
    private android.graphics.BlendMode mProgressBlendMode;
    private android.graphics.BlendMode mProgressBackgroundBlendMode;
    private android.graphics.BlendMode mIndeterminateBlendMode;
    private android.graphics.BlendMode mSecondaryProgressBlendMode;

    // AOSP :657 -- the raw defaults set before the styled attributes are read.
    private int mMinWidth = 24, mMaxWidth = 48, mMinHeight = 24, mMaxHeight = 48;
    private android.view.animation.Interpolator mInterpolator;
    private int mSampleWidth = 0;

    /** AOSP :1478 -- whichever of the progress/indeterminate drawables is
     *  currently being painted. */
    public android.graphics.drawable.Drawable getCurrentDrawable() { return mCurrentDrawable; }

    // ---- secondary progress tint (list half was missing too) ----------
    public void setSecondaryProgressTintList(android.content.res.ColorStateList tint) {
        mSecondaryProgressTintList = tint;
        if (mProgressDrawable != null) applySecondaryProgressTint();
    }
    public android.content.res.ColorStateList getSecondaryProgressTintList() {
        return mSecondaryProgressTintList;
    }
    private void applySecondaryProgressTint() {
        if (mSecondaryProgressTintList == null) return;
        // AOSP applySecondaryProgressTint passes shouldFallback=false: unlike the
        // primary progress there is no whole-drawable fallback, because tinting
        // the whole bar with the secondary colour would be wrong.
        android.graphics.drawable.Drawable target = getTintTarget(MACAPK_ID_SECONDARY, false);
        if (target == null) return;
        target.setTintList(mSecondaryProgressTintList);
        if (target.isStateful()) target.setState(getDrawableState());
    }

    // ---- tint MODES ---------------------------------------------------
    // Every pair below is AOSP's exact shape: the PorterDuff.Mode overload
    // delegates to the BlendMode overload through BlendMode.fromValue(), and
    // the PorterDuff getter runs BlendMode.blendModeToPorterDuffMode() on the
    // stored BlendMode (ProgressBar.java :1100-1400 for the four families).
    // Our Drawable tint plumbing takes a PorterDuff.Mode, so the applier
    // converts back on the way down -- lossless for every mode that has a
    // pair, and null (i.e. AOSP's own answer) for the Skia-only blends.
    public void setProgressTintMode(android.graphics.PorterDuff.Mode tintMode) {
        setProgressTintBlendMode(MacApkBlendModes.fromPorterDuff(tintMode));
    }
    public void setProgressTintBlendMode(android.graphics.BlendMode blendMode) {
        mProgressBlendMode = blendMode;
        if (mProgressDrawable != null) applyProgressTintMode();
    }
    public android.graphics.PorterDuff.Mode getProgressTintMode() {
        return MacApkBlendModes.toPorterDuff(mProgressBlendMode);
    }
    public android.graphics.BlendMode getProgressTintBlendMode() { return mProgressBlendMode; }
    private void applyProgressTintMode() {
        android.graphics.drawable.Drawable t = getTintTarget(MACAPK_ID_PROGRESS, true);
        if (t != null) t.setTintMode(MacApkBlendModes.toPorterDuff(mProgressBlendMode));
    }

    public void setProgressBackgroundTintMode(android.graphics.PorterDuff.Mode tintMode) {
        setProgressBackgroundTintBlendMode(MacApkBlendModes.fromPorterDuff(tintMode));
    }
    public void setProgressBackgroundTintBlendMode(android.graphics.BlendMode blendMode) {
        mProgressBackgroundBlendMode = blendMode;
        if (mProgressDrawable != null) {
            android.graphics.drawable.Drawable t = getTintTarget(MACAPK_ID_BACKGROUND, false);
            if (t != null) t.setTintMode(MacApkBlendModes.toPorterDuff(blendMode));
        }
    }
    public android.graphics.PorterDuff.Mode getProgressBackgroundTintMode() {
        return MacApkBlendModes.toPorterDuff(mProgressBackgroundBlendMode);
    }
    public android.graphics.BlendMode getProgressBackgroundTintBlendMode() {
        return mProgressBackgroundBlendMode;
    }

    public void setSecondaryProgressTintMode(android.graphics.PorterDuff.Mode tintMode) {
        setSecondaryProgressTintBlendMode(MacApkBlendModes.fromPorterDuff(tintMode));
    }
    public void setSecondaryProgressTintBlendMode(android.graphics.BlendMode blendMode) {
        mSecondaryProgressBlendMode = blendMode;
        if (mProgressDrawable != null) {
            android.graphics.drawable.Drawable t = getTintTarget(MACAPK_ID_SECONDARY, false);
            if (t != null) t.setTintMode(MacApkBlendModes.toPorterDuff(blendMode));
        }
    }
    public android.graphics.PorterDuff.Mode getSecondaryProgressTintMode() {
        return MacApkBlendModes.toPorterDuff(mSecondaryProgressBlendMode);
    }
    public android.graphics.BlendMode getSecondaryProgressTintBlendMode() {
        return mSecondaryProgressBlendMode;
    }

    public void setIndeterminateTintMode(android.graphics.PorterDuff.Mode tintMode) {
        setIndeterminateTintBlendMode(MacApkBlendModes.fromPorterDuff(tintMode));
    }
    public void setIndeterminateTintBlendMode(android.graphics.BlendMode blendMode) {
        mIndeterminateBlendMode = blendMode;
        if (mIndeterminateDrawable != null) {
            // AOSP mutates before tinting for the same reason applyIndeterminateTint does.
            mIndeterminateDrawable = mIndeterminateDrawable.mutate();
            mIndeterminateDrawable.setTintMode(MacApkBlendModes.toPorterDuff(blendMode));
        }
    }
    public android.graphics.PorterDuff.Mode getIndeterminateTintMode() {
        return MacApkBlendModes.toPorterDuff(mIndeterminateBlendMode);
    }
    public android.graphics.BlendMode getIndeterminateTintBlendMode() {
        return mIndeterminateBlendMode;
    }

    // ---- min/max size -------------------------------------------------
    // AOSP :439-497. These feed onMeasure, which clamps the drawable's
    // intrinsic size into [min,max] (and AbsSeekBar's, which adds the thumb).
    public void setMinWidth(int minWidth)   { mMinWidth = minWidth;   requestLayout(); }
    public int  getMinWidth()               { return mMinWidth; }
    public void setMaxWidth(int maxWidth)   { mMaxWidth = maxWidth;   requestLayout(); }
    public int  getMaxWidth()               { return mMaxWidth; }
    public void setMinHeight(int minHeight) { mMinHeight = minHeight; requestLayout(); }
    public int  getMinHeight()              { return mMinHeight; }
    public void setMaxHeight(int maxHeight) { mMaxHeight = maxHeight; requestLayout(); }
    public int  getMaxHeight()              { return mMaxHeight; }

    // ---- indeterminate interpolator -----------------------------------
    /** AOSP :2043 / :2023 / :2056. */
    public void setInterpolator(android.view.animation.Interpolator interpolator) {
        mInterpolator = interpolator;
    }
    public void setInterpolator(Context context, int resID) {
        setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(context, resID));
    }
    public android.view.animation.Interpolator getInterpolator() { return mInterpolator; }

    /** AOSP :2412 -- verbatim. */
    public boolean isAnimating() {
        return isIndeterminate() && getWindowVisibility() == VISIBLE && isShown();
    }

    // ---- tiled drawables ----------------------------------------------
    /** AOSP :1462 -- setProgressDrawable(tileify(d, false)). */
    public void setProgressDrawableTiled(android.graphics.drawable.Drawable d) {
        if (d != null) d = tileify(d, false);
        setProgressDrawable(d);
    }
    /** AOSP :914 -- setIndeterminateDrawable(tileifyIndeterminate(d)). */
    public void setIndeterminateDrawableTiled(android.graphics.drawable.Drawable d) {
        if (d != null) d = tileifyIndeterminate(d);
        setIndeterminateDrawable(d);
    }

    /** AOSP :546 tileify(Drawable, boolean) -- rewrite the leaf BitmapDrawables
     *  of a progress drawable so they REPEAT horizontally instead of stretching,
     *  wrapping the progress/secondaryProgress layers in a ClipDrawable so the
     *  level clips them. Structure-preserving for LayerDrawable and
     *  StateListDrawable, identity for anything else, exactly as AOSP.
     *  One deliberate difference: AOSP clones the BitmapDrawable via
     *  getConstantState().newDrawable(res); our Drawable.getConstantState()
     *  returns a SELF-referencing state (Drawable.java:154), so that call would
     *  hand back the caller's own instance and mutating it would change the
     *  caller's drawable. We build a fresh BitmapDrawable over the same Bitmap
     *  instead, which is what AOSP's clone is FOR. */
    private android.graphics.drawable.Drawable tileify(
            android.graphics.drawable.Drawable drawable, boolean clip) {
        if (drawable instanceof android.graphics.drawable.LayerDrawable) {
            final android.graphics.drawable.LayerDrawable orig =
                    (android.graphics.drawable.LayerDrawable) drawable;
            final int n = orig.getNumberOfLayers();
            final android.graphics.drawable.Drawable[] out =
                    new android.graphics.drawable.Drawable[n];
            for (int i = 0; i < n; i++) {
                final int id = orig.getId(i);
                out[i] = tileify(orig.getDrawable(i),
                        (id == MACAPK_ID_PROGRESS || id == MACAPK_ID_SECONDARY));
            }
            final android.graphics.drawable.LayerDrawable clone =
                    new android.graphics.drawable.LayerDrawable(out);
            for (int i = 0; i < n; i++) {
                clone.setId(i, orig.getId(i));
                clone.setLayerGravity(i, orig.getLayerGravity(i));
                clone.setLayerWidth(i, orig.getLayerWidth(i));
                clone.setLayerHeight(i, orig.getLayerHeight(i));
                clone.setLayerInsetLeft(i, orig.getLayerInsetLeft(i));
                clone.setLayerInsetRight(i, orig.getLayerInsetRight(i));
                clone.setLayerInsetTop(i, orig.getLayerInsetTop(i));
                clone.setLayerInsetBottom(i, orig.getLayerInsetBottom(i));
                clone.setLayerInsetStart(i, orig.getLayerInsetStart(i));
                clone.setLayerInsetEnd(i, orig.getLayerInsetEnd(i));
            }
            return clone;
        }
        if (drawable instanceof android.graphics.drawable.StateListDrawable) {
            final android.graphics.drawable.StateListDrawable in =
                    (android.graphics.drawable.StateListDrawable) drawable;
            final android.graphics.drawable.StateListDrawable out =
                    new android.graphics.drawable.StateListDrawable();
            final int n = in.getStateCount();
            for (int i = 0; i < n; i++) {
                out.addState(in.getStateSet(i), tileify(in.getStateDrawable(i), clip));
            }
            return out;
        }
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            final android.graphics.drawable.BitmapDrawable src =
                    (android.graphics.drawable.BitmapDrawable) drawable;
            final android.graphics.drawable.BitmapDrawable clone =
                    new android.graphics.drawable.BitmapDrawable(getResources(), src.getBitmap());
            clone.setTileModeXY(android.graphics.Shader.TileMode.REPEAT,
                                android.graphics.Shader.TileMode.CLAMP);
            if (mSampleWidth <= 0) mSampleWidth = clone.getIntrinsicWidth();
            if (clip) {
                return new android.graphics.drawable.ClipDrawable(clone,
                        android.view.Gravity.LEFT,
                        android.graphics.drawable.ClipDrawable.HORIZONTAL);
            }
            return clone;
        }
        return drawable;
    }

    /** AOSP :615 tileifyIndeterminate -- each AnimationDrawable frame becomes a
     *  tiled, fully-clipped (level 10000) frame, so a barber-pole indeterminate
     *  drawable repeats instead of stretching. Identity for anything that is
     *  not an AnimationDrawable, exactly as AOSP. */
    private android.graphics.drawable.Drawable tileifyIndeterminate(
            android.graphics.drawable.Drawable drawable) {
        if (drawable instanceof android.graphics.drawable.AnimationDrawable) {
            final android.graphics.drawable.AnimationDrawable background =
                    (android.graphics.drawable.AnimationDrawable) drawable;
            final int n = background.getNumberOfFrames();
            final android.graphics.drawable.AnimationDrawable newBg =
                    new android.graphics.drawable.AnimationDrawable();
            newBg.setOneShot(background.isOneShot());
            for (int i = 0; i < n; i++) {
                android.graphics.drawable.Drawable frame = tileify(background.getFrame(i), true);
                frame.setLevel(10000);
                newBg.addFrame(frame, background.getDuration(i));
            }
            newBg.setLevel(10000);
            drawable = newBg;
        }
        return drawable;
    }
}
