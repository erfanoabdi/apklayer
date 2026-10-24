// android.widget.AbsSeekBar -- shared superclass of SeekBar / RatingBar.
// AOSP: frameworks/base core/java/android/widget/AbsSeekBar.java
//   public abstract class AbsSeekBar extends ProgressBar
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public abstract class AbsSeekBar extends ProgressBar {
    protected int keyProgressIncrement;

    public AbsSeekBar(Context context) { super(context); }
    public AbsSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        macapkApplyStyledThumb(context, attrs, macapkDefaultStyleAttr(), 0);
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AbsSeekBar(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        macapkApplyStyledThumb(context, attrs, defStyleAttr, defStyleRes);
    }
    public AbsSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        macapkApplyStyledThumb(context, attrs, defStyleAttr, 0);
    }

    /** AOSP AbsSeekBar(Context, AttributeSet) is this(context, attrs, 0): no
     *  default style of its own (SeekBar and RatingBar name theirs). */
    @Override int macapkDefaultStyleAttr() { return 0; }

    /** android.R.attr.thumb / thumbOffset (SDK-34 R$attr). */
    private static final int[] MACAPK_STYLED_THUMB_ATTRS = { 0x01010142, 0x01010143 };

    /** AOSP android14-release AbsSeekBar.java:122-159, in the constructor:
     *      final TypedArray a = context.obtainStyledAttributes(
     *              attrs, R.styleable.SeekBar, defStyleAttr, defStyleRes);
     *      final Drawable thumb = a.getDrawable(R.styleable.SeekBar_thumb);
     *      setThumb(thumb);
     *      ...
     *      final int thumbOffset = a.getDimensionPixelOffset(
     *              R.styleable.SeekBar_thumbOffset, getThumbOffset());
     *      setThumbOffset(thumbOffset);
     *  Ours never read it, so getThumb() was null on every XML SeekBar (NewPipe's
     *  VideoPlayerUi dereferences it; see ProgressBar.PROGRESS_STYLED_DRAWABLES,
     *  whose gate this shares). */
    private void macapkApplyStyledThumb(Context context, AttributeSet attrs,
                                        int defStyleAttr, int defStyleRes) {
        if (!PROGRESS_STYLED_DRAWABLES || context == null) return;
        android.content.res.TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, MACAPK_STYLED_THUMB_ATTRS,
                    defStyleAttr, defStyleRes);
            if (a.hasValue(0)) {
                Drawable thumb = a.getDrawable(0);
                if (thumb != null) setThumb(thumb);
            }
            if (a.hasValue(1)) setThumbOffset(a.getDimensionPixelOffset(1, getThumbOffset()));
        } catch (Throwable t) {
            // A thumb that cannot be built must never break construction.
        } finally {
            if (a != null) try { a.recycle(); } catch (Throwable ignored) {}
        }
    }

    public void setKeyProgressIncrement(int increment) { keyProgressIncrement = increment; }
    public int  getKeyProgressIncrement() { return keyProgressIncrement; }

    // AOSP AbsSeekBar.mThumb / setThumb / getThumb (~lines 57, 187-243): was
    // `{}` / hardcoded null with no backing field at all -- the thumb is the
    // ONE visual element that identifies a SeekBar as draggable, and
    // SeekBar.java (this package) already has real drag-to-progress touch
    // handling, so the interaction worked but drew no handle whatsoever, and
    // any app-configured `app:thumb` / thumbTint was silently discarded.
    // Found via the same half-implemented-accessor grep that caught
    // ImageView's image-tint pair and Switch's thumb/track-tint pair.
    private Drawable mThumb;
    private int mThumbOffset;
    private Drawable mTickMark;
    private boolean mSplitTrack;

    private android.content.res.ColorStateList mThumbTintList;
    private android.graphics.PorterDuff.Mode mThumbTintMode;
    private boolean mHasThumbTint;
    private boolean mHasThumbTintMode;
    // Tick-mark tint is stored for accessor correctness (see setTickMark
    // below) but not painted -- see the drawTickMarks omission note there.
    private android.content.res.ColorStateList mTickMarkTintList;
    private android.graphics.PorterDuff.Mode mTickMarkTintMode;

    /** AOSP AbsSeekBar.setThumb(Drawable) (~line 187). If the new thumb's
     *  intrinsic size differs from the old one, half its width becomes the
     *  new thumb offset (AOSP: "assuming the thumb drawable is symmetric, the
     *  thumb hangs halfway off either edge of the track") -- this OVERWRITES
     *  any earlier explicit setThumbOffset() call, which is a real AOSP
     *  quirk, mirrored faithfully rather than "fixed". */
    public void setThumb(Drawable thumb) {
        if (mThumb != null && thumb != mThumb) {
            mThumb.setCallback(null);
        }
        if (thumb != null) {
            thumb.setCallback(this);
            thumb.setLayoutDirection(getLayoutDirection());
            mThumbOffset = thumb.getIntrinsicWidth() / 2;
        }
        mThumb = thumb;
        applyThumbTint();
        if (thumb != null && thumb.isStateful()) {
            thumb.setState(getDrawableState());
        }
        invalidate();
    }
    public Drawable getThumb() { return mThumb; }

    /** AOSP AbsSeekBar.onMeasure (android14 :881-897): ProgressBar's clamp, at
     *  least as tall as the thumb. */
    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable d = getCurrentDrawable();
        int thumbHeight = mThumb == null ? 0 : mThumb.getIntrinsicHeight();
        int dw = 0, dh = 0;
        if (d != null) {
            dw = Math.max(getMinWidth(), Math.min(getMaxWidth(), d.getIntrinsicWidth()));
            dh = Math.max(getMinHeight(), Math.min(getMaxHeight(), d.getIntrinsicHeight()));
            dh = Math.max(thumbHeight, dh);
        }
        dw += getPaddingLeft() + getPaddingRight();
        dh += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSizeAndState(dw, widthMeasureSpec, 0),
                resolveSizeAndState(dh, heightMeasureSpec, 0));
    }

    public void setThumbOffset(int thumbOffset) { mThumbOffset = thumbOffset; invalidate(); }
    public int  getThumbOffset() { return mThumbOffset; }

    /** AOSP AbsSeekBar.setTickMark(Drawable) (~line 380). Accessor + callback/
     *  state wiring only -- drawTickMarks (dividing the track into
     *  max-min discrete ticks) is NOT painted here; that is a separate,
     *  lower-priority visual feature (most SeekBar usages are continuous, not
     *  segmented) and this fix is scoped to the accessor-correctness bug
     *  (a setter that discarded its argument and a getter hardcoded to
     *  null/0/false), matching the pattern this pass targets. */
    public void setTickMark(Drawable tickMark) {
        if (mTickMark != null) mTickMark.setCallback(null);
        mTickMark = tickMark;
        if (tickMark != null) {
            tickMark.setCallback(this);
            tickMark.setLayoutDirection(getLayoutDirection());
            if (tickMark.isStateful()) tickMark.setState(getDrawableState());
        }
        invalidate();
    }
    public Drawable getTickMark() { return mTickMark; }
    public void setTickMarkTintList(android.content.res.ColorStateList tint) { mTickMarkTintList = tint; }
    public android.content.res.ColorStateList getTickMarkTintList() { return mTickMarkTintList; }
    public void setTickMarkTintMode(android.graphics.PorterDuff.Mode mode) { mTickMarkTintMode = mode; }
    public android.graphics.PorterDuff.Mode getTickMarkTintMode() { return mTickMarkTintMode; }

    /** AOSP AbsSeekBar.setSplitTrack/getSplitTrack: whether the track is
     *  clipped around the thumb's optical bounds. Accessor-correctness only
     *  (see setTickMark above) -- the clip itself needs a drawTrack() hook
     *  our monolithic ProgressBar.onDraw does not expose, and is a purely
     *  cosmetic gap-around-the-thumb refinement, not a missing element. */
    public void setSplitTrack(boolean split) { mSplitTrack = split; invalidate(); }
    public boolean getSplitTrack() { return mSplitTrack; }

    /** AOSP AbsSeekBar.setThumbTintList/getThumbTintList/setThumbTintMode/
     *  getThumbTintMode (~lines 259-325), collapsed onto this tree's existing
     *  PorterDuff.Mode-based Drawable.setTintList/setTintMode surface (AOSP's
     *  BlendMode-based storage is a newer wrapper around the same observable
     *  API) -- same collapse CompoundButton.applyButtonTint and ImageView.
     *  applyImageTint already use in this package. */
    public void setThumbTintList(android.content.res.ColorStateList tint) {
        mThumbTintList = tint;
        mHasThumbTint = true;
        applyThumbTint();
    }
    public android.content.res.ColorStateList getThumbTintList() { return mThumbTintList; }
    public void setThumbTintMode(android.graphics.PorterDuff.Mode tintMode) {
        mThumbTintMode = tintMode;
        mHasThumbTintMode = true;
        applyThumbTint();
    }
    public android.graphics.PorterDuff.Mode getThumbTintMode() { return mThumbTintMode; }

    /** AOSP AbsSeekBar.applyThumbTint (~line 344): mutate-then-tint so a
     *  shared/cached Drawable instance's tint does not bleed into other
     *  views using the same instance. */
    private void applyThumbTint() {
        if (mThumb == null || !(mHasThumbTint || mHasThumbTintMode)) return;
        mThumb = mThumb.mutate();
        if (mHasThumbTint) mThumb.setTintList(mThumbTintList);
        if (mHasThumbTintMode) mThumb.setTintMode(mThumbTintMode);
        if (mThumb.isStateful()) mThumb.setState(getDrawableState());
    }

    /** AOSP AbsSeekBar.getScale (~line 703): current progress as a [0,1]
     *  fraction of [min,max]. ProgressBar.getMin/getMax/getProgress already
     *  exist and are real (this package). */
    private float getScale() {
        int range = getMax() - getMin();
        return range > 0 ? (getProgress() - getMin()) / (float) range : 0f;
    }

    /** AOSP AbsSeekBar.onDraw -> drawThumb (~lines 719-877): positions and
     *  paints mThumb after the track. AOSP caches the thumb's Rect and
     *  recomputes it only on progress/size/RTL-change events routed through
     *  onVisualProgressChanged/onSizeChanged/onRtlPropertiesChanged; our
     *  ProgressBar.onDraw has no such per-drawable-position hook to attach
     *  to (its track paint is monolithic, not factored into an overridable
     *  drawTrack()), so this recomputes the thumb bounds directly from the
     *  live progress fraction on every draw instead -- functionally
     *  equivalent (the position can never be stale) and matches how
     *  CompoundButton.onDraw in this same package already positions its
     *  button drawable fresh every call rather than caching bounds.
     *  Math (available/thumbPos/left/right) mirrors AOSP's setThumbPos
     *  verbatim; vertical centering is simplified to "center the thumb in
     *  the padded content height" rather than AOSP's compare-thumb-vs-track-
     *  height dance, since our onDraw does not expose the track drawable's
     *  own measured height separately. */
    @Override
    protected synchronized void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        final Drawable thumb = mThumb;
        if (thumb == null || canvas == null) return;
        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        final int w = getWidth();
        final int contentHeight = getHeight() - paddingTop - paddingBottom;
        int available = w - paddingLeft - paddingRight;
        final int thumbWidth = thumb.getIntrinsicWidth();
        final int thumbHeight = thumb.getIntrinsicHeight();
        available -= thumbWidth;
        // The extra space for the thumb to move on the track (AOSP setThumbPos).
        available += mThumbOffset * 2;
        if (available <= 0) return;
        final int thumbPos = (int) (getScale() * available + 0.5f);
        final int top = (contentHeight - thumbHeight) / 2;
        final int bottom = top + thumbHeight;
        final boolean rtl = isLayoutRtl();
        final int left = rtl ? available - thumbPos : thumbPos;
        final int right = left + thumbWidth;
        final int sv = canvas.save();
        // Translate the padding; for x, allow the thumb to draw in its extra
        // space (AOSP drawThumb: canvas.translate(paddingLeft - thumbOffset, paddingTop)).
        canvas.translate(paddingLeft - mThumbOffset, paddingTop);
        thumb.setBounds(left, top, right, bottom);
        thumb.draw(canvas);
        canvas.restoreToCount(sv);
    }

    /** AOSP AbsSeekBar.verifyDrawable (~line 597): View.verifyDrawable (this
     *  package) already checks the background, so chain into it. */
    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mThumb || who == mTickMark || super.verifyDrawable(who);
    }

    /** AOSP AbsSeekBar.jumpDrawablesToCurrentState (~line 602). */
    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mThumb != null) mThumb.jumpToCurrentState();
        if (mTickMark != null) mTickMark.jumpToCurrentState();
    }

    /** AOSP AbsSeekBar.drawableStateChanged (~line 614): forwards the current
     *  drawable state to the thumb/tick-mark so a stateful drawable (a
     *  <selector> keyed on state_pressed) picks the right entry. Omits
     *  AOSP's mDisabledAlpha dimming of the progress drawable (no such field
     *  in this tree's ProgressBar). */
    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable thumb = mThumb;
        if (thumb != null && thumb.isStateful() && thumb.setState(getDrawableState())) {
            invalidateDrawable(thumb);
        }
        final Drawable tickMark = mTickMark;
        if (tickMark != null && tickMark.isStateful() && tickMark.setState(getDrawableState())) {
            invalidateDrawable(tickMark);
        }
    }

    /** AOSP AbsSeekBar.drawableHotspotChanged (~line 636). */
    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mThumb != null) mThumb.setHotspot(x, y);
    }

    // ---- API-36 surface completion: the BlendMode half of the two tint pairs.
    // AOSP AbsSeekBar (android16-release) stores ONE BlendMode per tint and
    // derives the PorterDuff.Mode from it; this port already stores the
    // PorterDuff.Mode (which is what our Drawable.setTintMode takes), so the
    // BlendMode is kept alongside as the round-trip source of truth and the two
    // are converted through MacApkBlendModes -- AOSP's own
    // BlendMode.fromValue / blendModeToPorterDuffMode mapping.
    private android.graphics.BlendMode mThumbBlendMode;
    private android.graphics.BlendMode mTickMarkBlendMode;

    /** AOSP AbsSeekBar.setThumbTintBlendMode / getThumbTintBlendMode. */
    public void setThumbTintBlendMode(android.graphics.BlendMode blendMode) {
        mThumbBlendMode = blendMode;
        setThumbTintMode(MacApkBlendModes.toPorterDuff(blendMode));
    }
    public android.graphics.BlendMode getThumbTintBlendMode() { return mThumbBlendMode; }

    /** AOSP AbsSeekBar.setTickMarkTintBlendMode / getTickMarkTintBlendMode. */
    public void setTickMarkTintBlendMode(android.graphics.BlendMode blendMode) {
        mTickMarkBlendMode = blendMode;
        setTickMarkTintMode(MacApkBlendModes.toPorterDuff(blendMode));
    }
    public android.graphics.BlendMode getTickMarkTintBlendMode() { return mTickMarkBlendMode; }
}
