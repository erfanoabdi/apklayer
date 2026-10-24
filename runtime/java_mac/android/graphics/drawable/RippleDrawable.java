// android.graphics.drawable.RippleDrawable -- minimum stub so
// Material widgets that wrap a content drawable in a ripple
// (BottomNavigationItemView's item background, MaterialButton's
// background, etc.) can construct one.
//
// AOSP: `public class RippleDrawable extends LayerDrawable`. The
// superclass relationship is LOAD-BEARING: Material's MaterialButton/
// MaterialButtonHelper treats the ripple background as a LayerDrawable
// (it calls getNumberOfLayers()/getDrawable(int)/setDrawable(int,...)
// on it to reach the inner MaterialShapeDrawable). When we extended
// plain Drawable, MaterialButton.<init> threw AbstractMethodError
// ("RippleDrawable does not inherit getNumberOfLayers() of class
// LayerDrawable"), which aborted the Calculator's history-button
// inflation (the first MaterialButtons the calc creates) and left the
// fragment view empty -> blank screen. Extending LayerDrawable fixes it.
//
// Drawing: the content layers always paint (drawContent, i.e. every layer
// except the one tagged android.R.id.mask). Under MACAPK_RIPPLE_PRESS a
// PRESSED ripple additionally paints AOSP's background half -- a steady fill
// in the ripple colour, shaped by the mask's outline. The radial
// RippleForeground animation is still not implemented. v1.0.97; v1.0.450
// extends LayerDrawable.
package android.graphics.drawable;

public class RippleDrawable extends LayerDrawable {
    private android.content.res.ColorStateList color;

    public RippleDrawable(android.content.res.ColorStateList color,
                          Drawable content,
                          Drawable mask) {
        // Register content (+ mask) as real layers so the LayerDrawable
        // surface (getNumberOfLayers/getDrawable/setDrawable) operates on
        // them, matching what MaterialButtonHelper expects.
        this.color = color;
        if (content != null) addLayer(content);
        if (mask != null) {
            addLayer(mask);
            // AOSP tags the mask layer with android.R.id.mask
            // (RippleDrawable.java:262, android13-release:
            //   addLayer(mask, null, android.R.id.mask, 0, 0, 0, 0);)
            // and every drawing path then keys off that id rather than off
            // layer position -- drawContent() is literally "draw everything
            // except the mask". Without the tag our draw() had to assume
            // "layer 0 is content", which is wrong for the documented
            // RippleDrawable(color, null, mask) shape: there the mask lands at
            // index 0 and we PAINTED it.
            setId(getNumberOfLayers() - 1, ID_MASK);
        }
    }
    public RippleDrawable() { this(null, null, null); }

    /** AOSP RippleDrawable.setEffectColor(ColorStateList) (API 31) -- the color
     *  of the RIPPLE EFFECT (the sparkle/patterned overlay), which is a separate
     *  slot from the ripple's own color passed to the constructor. */
    public void setEffectColor(android.content.res.ColorStateList color) {
        mEffectColor = color;
    }

    /** AOSP RippleDrawable.getEffectColor(). AOSP's default is an opaque-white
     *  ColorStateList (RippleDrawable.DEFAULT_EFFECT_COLOR = 0xFFFFFFFF), NOT
     *  null -- the getter is @NonNull, so an app may dereference it. */
    public android.content.res.ColorStateList getEffectColor() {
        if (mEffectColor == null) {
            mEffectColor = android.content.res.ColorStateList.valueOf(0xFFFFFFFF);
        }
        return mEffectColor;
    }
    private android.content.res.ColorStateList mEffectColor;

    /** android.R.id.mask, read out of the API-30 platform resource table
     *  (resources.arsc of framework-res.apk, via debug/extract_fw_res.py).
     *  We have no android.R class, so the constant is inlined the same way
     *  ColorStateList.stateAttrId and TypedArray.frameworkAttrName inline
     *  theirs. */
    public static final int ID_MASK = 0x0102002e;

    /** MACAPK_RIPPLE_PRESS -- press feedback for <ripple> drawables.
     *
     *  Default OFF. With the gate off every method below early-returns to the
     *  behaviour that shipped before it, and Resources' <ripple> inflater keeps
     *  producing a plain LayerDrawable, so the default path is unchanged.
     *
     *  WHY THE GATE EXISTS AT ALL rather than this just being landed: View
     *  already runs a SEPARATE press ripple of its own (View.macapkStartRipple /
     *  macapkDrawRipple, default ON under MACAPK_REAL_LOOPER), so turning this
     *  on paints press feedback from TWO places at once for any clickable view
     *  whose background is a <ripple>. Which one survives is a decision for
     *  whoever measures the two together, not for this file. */
    // public (not package-private) so the resource inflater in
    // android.content.res can read the SAME gate rather than re-reading the
    // env var and risking the two halves disagreeing. Named MACAPK_* like
    // Looper.MACAPK_REAL_LOOPER so it cannot collide with platform API.
    public static final boolean MACAPK_RIPPLE_PRESS =
        "1".equals(System.getenv("MACAPK_RIPPLE_PRESS"));

    /** AOSP RippleDrawable.mRippleActive (:196) -- "whether we expect to draw a
     *  ripple when visible", set by onStateChange from `enabled && pressed`. */
    private boolean mRippleActive;

    /** AOSP RippleDrawable.onStateChange (android14-release:318-343):
     *
     *      protected boolean onStateChange(int[] stateSet) {
     *          final boolean changed = super.onStateChange(stateSet);
     *          ... for (int state : stateSet) { ... state_enabled/pressed/... }
     *          setRippleActive(enabled && pressed);
     *          setBackgroundActive(hovered, focused, pressed, windowFocused);
     *          return changed;
     *      }
     *
     *  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/graphics/java/android/graphics/drawable/RippleDrawable.java
     *
     *  Deliberate deviation on the RETURN value: AOSP returns super's answer and
     *  gets its repaint from the invalidateSelf(false) inside tryRippleEnter /
     *  startPatternedAnimation. Our View.drawableStateChanged repaints only when
     *  setState returns true (View.java: `if (changed) invalidate()`), and for a
     *  mask-only ripple -- which is exactly what ?attr/selectableItemBackground
     *  is -- no child is stateful, so super answers false and the press would
     *  never reach the screen. Reporting true when the ripple's own appearance
     *  changed is what the boolean actually means, so we do both: invalidateSelf
     *  (AOSP's mechanism) and an honest return.
     *
     *  state_enabled / state_pressed constants are inlined the same way ID_MASK
     *  above is: we have no android.R class. Values read from the API-30
     *  platform table: android.R.attr.state_pressed = 0x010100a7,
     *  state_enabled = 0x0101009e. */
    private static final int STATE_PRESSED = 0x010100a7;
    private static final int STATE_ENABLED = 0x0101009e;

    @Override public boolean onStateChange(int[] state) {
        final boolean changed = super.onStateChange(state);
        if (!MACAPK_RIPPLE_PRESS) return changed;
        boolean enabled = false, pressed = false;
        if (state != null) {
            for (int s : state) {
                if (s == STATE_ENABLED) enabled = true;
                else if (s == STATE_PRESSED) pressed = true;
            }
        }
        // AOSP setRippleActive(enabled && pressed) (:340). A view that never
        // declares state_enabled at all still presses on a real device because
        // its state set carries state_pressed with enabled absent only when it
        // is genuinely disabled -- but our state sets are assembled by
        // View.getDrawableState, which does emit state_enabled, so the AOSP
        // conjunction is used verbatim rather than loosened.
        final boolean active = enabled && pressed;
        if (mRippleActive == active) return changed;
        mRippleActive = active;
        invalidateSelf();       // AOSP: tryRippleEnter/Exit end in invalidateSelf(false)
        return true;
    }

    /** AOSP RippleDrawable.draw -> drawSolid -> drawContent()
     *  + drawBackgroundAndRipples() (android14-release:805-830):
     *
     *      private void drawSolid(Canvas canvas) {
     *          pruneRipples();
     *          final Rect bounds = getDirtyBounds();
     *          final int saveCount = canvas.save(Canvas.CLIP_SAVE_FLAG);
     *          if (isBounded()) canvas.clipRect(bounds);
     *          drawContent(canvas);
     *          drawBackgroundAndRipples(canvas);
     *          canvas.restoreToCount(saveCount);
     *      }
     *
     *  drawContent() (:1155-1164) is:
     *      for (i...) if (array[i].mId != R.id.mask) array[i].mDrawable.draw(canvas);
     *  i.e. EVERY layer except the mask, not just layer 0. */
    @Override public void draw(android.graphics.Canvas canvas) {
        final int n = getNumberOfLayers();
        for (int i = 0; i < n; i++) {
            if (getId(i) == ID_MASK) continue;
            Drawable d = getDrawable(i);
            if (d != null) d.draw(canvas);
        }
        if (MACAPK_RIPPLE_PRESS) drawBackgroundAndRipples(canvas);
    }

    /** The layer AOSP calls mMask -- the child tagged android.R.id.mask. AOSP
     *  caches it in updateLocalState via findDrawableByLayerId(R.id.mask)
     *  (:1320); we have few layers, so a scan is cheaper than a cache to keep
     *  coherent across setDrawableByLayerId. */
    private Drawable macapkMaskLayer() {
        final int n = getNumberOfLayers();
        for (int i = 0; i < n; i++) {
            if (getId(i) == ID_MASK) return getDrawable(i);
        }
        return null;
    }

    /** AOSP drawBackgroundAndRipples (android14-release:1166-1198). Ours is the
     *  BACKGROUND half only: a steady pressed fill, no radial animation.
     *
     *  What is faithful here, and why that is the useful subset:
     *
     *  - THE EARLY RETURN. AOSP opens with
     *        if (active == null && count <= 0 && (background == null
     *                || !background.isVisible())) return;
     *    i.e. an un-pressed, un-hovered, un-focused ripple paints nothing. Ours
     *    keys the same early return off mRippleActive, so a resting
     *    ?attr/selectableItemBackground stays transparent, exactly as before.
     *    (The old comment in this file used that early return to argue the whole
     *    method could be omitted. That reasoning was circular: it holds only
     *    because we never drove a ripple, and a PRESS is precisely the frame
     *    where AOSP's condition is false.)
     *
     *  - THE COLOUR AND ITS ALPHA. AOSP updateRipplePaint (:1234-1236) is
     *        final int color = mState.mColor.getColorForState(getState(), Color.BLACK);
     *        ... p.setColor(color);
     *    The pressed alpha is not a separate constant -- it is carried by the
     *    ripple colour itself. <ripple android:color="?attr/colorControlHighlight">
     *    resolves through ResourceIndex to @android:color/ripple_material_light
     *    = 0x33000000 (20% black) or ripple_material_dark = 0x33FFFFFF. So
     *    filling at the colour's own alpha reproduces AOSP's settled pressed
     *    appearance without inventing an opacity.
     *
     *  - THE SHAPE. AOSP masks the ripple with a BitmapShader built from the
     *    mask layer (updateMaskShaderIfNeeded / getMaskType, :1125-1150). We
     *    did not, because our GradientDrawable.setColorFilter was a no-op, so
     *    tinting the mask in place would have painted the mask's OWN colour
     *    (white, for item_background_material) over the widget. (Since
     *    v1.0.1650 it is real, MACAPK_DRAWABLE_COLOR_FILTER; this path was not
     *    revisited and still does not tint the mask.) Instead we take the mask's
     *    Outline -- which every drawable in this package implements, and which
     *    GradientDrawable fills from its real corner radii -- and fill that.
     *    Rounded and oval masks therefore keep their shape; an arbitrary path
     *    mask is filled as its path. This is an approximation of the shader, not
     *    of the ripple's extent, and it is exact for the two shapes that
     *    actually occur (a plain rect mask and a round-rect mask).
     *
     *  NOT implemented, deliberately: RippleForeground's expanding radius and
     *  its exit fade. Those need a per-frame animator; the acknowledgement the
     *  user needs is the fill, and an animator is a separate decision. */
    private void drawBackgroundAndRipples(android.graphics.Canvas canvas) {
        if (!mRippleActive || canvas == null) return;
        final android.content.res.ColorStateList csl = color;
        if (csl == null) return;
        final int c;
        try {
            // AOSP's default here is Color.BLACK, i.e. OPAQUE black. That is the
            // right AOSP answer for a ColorStateList that cannot answer at all,
            // and a <ripple> without a resolvable colour is an XmlPullParserException
            // on a device (verifyRequiredAttributes, :659-665), so the case does
            // not arise from well-formed resources.
            c = csl.getColorForState(getState(), 0xFF000000);
        } catch (Throwable t) { return; }
        if ((c >>> 24) == 0) return;    // fully transparent: nothing to paint

        final android.graphics.Rect b = getBounds();
        if (b == null || b.width() <= 0 || b.height() <= 0) return;

        android.graphics.Outline o = null;
        final Drawable mask = macapkMaskLayer();
        if (mask != null) {
            o = new android.graphics.Outline();
            try { mask.getOutline(o); } catch (Throwable t) { o = null; }
            if (o != null && o.isEmpty()) o = null;
        }

        final android.graphics.Paint p = new android.graphics.Paint();
        p.setAntiAlias(true);
        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setColor(c);

        if (o != null && o.mMode == android.graphics.Outline.MODE_PATH && o.mPath != null) {
            canvas.drawPath(o.mPath, p);
            return;
        }
        final android.graphics.Rect r =
            (o != null && o.mMode == android.graphics.Outline.MODE_ROUND_RECT
                 && !o.mRect.isEmpty()) ? o.mRect : b;
        final float rad = (o != null) ? o.mRadius : 0f;
        if (rad > 0.5f) {
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, rad, rad, p);
        } else {
            canvas.drawRect(r.left, r.top, r.right, r.bottom, p);
        }
    }

    /** AOSP RippleDrawable.getOutline (android14-release:790-801) -- "populates
     *  outline with the first available layer outline, EXCLUDING the mask
     *  layer". LayerDrawable's inherited version stops at the first non-empty
     *  child, mask included, which under the gate would hand a widget the
     *  mask's shape as its own shadow/clip outline. Gate-guarded so the default
     *  path keeps the inherited answer byte for byte. */
    @Override public void getOutline(android.graphics.Outline outline) {
        if (!MACAPK_RIPPLE_PRESS) { super.getOutline(outline); return; }
        final int n = getNumberOfLayers();
        for (int i = 0; i < n; i++) {
            if (getId(i) == ID_MASK) continue;
            Drawable d = getDrawable(i);
            if (d == null) continue;
            d.getOutline(outline);
            if (!outline.isEmpty()) return;
        }
    }
    @Override public void setAlpha(int alpha) {
        Drawable content = getNumberOfLayers() > 0 ? getDrawable(0) : null;
        if (content != null) content.setAlpha(alpha);
    }
    @Override public void setColorFilter(android.graphics.ColorFilter cf) {
        Drawable content = getNumberOfLayers() > 0 ? getDrawable(0) : null;
        if (content != null) content.setColorFilter(cf);
    }
    @Override public int getOpacity() { return -3; /* TRANSLUCENT */ }

    /** AOSP RippleDrawable.isStateful() is an unconditional `return true` --
     *  the ripple itself reacts to enabled/pressed/focused/hovered, so the
     *  drawable is stateful regardless of what its layers are. This is
     *  load-bearing for Mac.apk: View.drawableStateChanged only calls
     *  setState when isStateful() reports true, and the inherited
     *  LayerDrawable default reports false whenever the content layer happens
     *  not to be stateful, which silently stranded the whole state change.
     *  With this, a Material button's per-state-tinted content layer under the
     *  ripple actually receives pressed/checked. */
    @Override public boolean isStateful() { return true; }

    public void setColor(android.content.res.ColorStateList color) {
        this.color = color;
        invalidateSelf();   // AOSP: setColor ends in invalidateSelf(false)
    }
    public android.content.res.ColorStateList getColor() { return color; }
    // AOSP RippleDrawable.setRadius/getRadius (API 23+). Material buttons set the
    // ripple radius on press; was MISSING -> NoSuchMethodError on the activity
    // thread the moment a Material button/Compose ripple was touched, aborting the
    // press handler so the tap did nothing. We store it (the ripple is drawn via
    // the content drawable; the radius is accepted so the press handler completes).
    private int radius = RADIUS_AUTO;
    public static final int RADIUS_AUTO = -1;
    public void setRadius(int radius) {
        this.radius = radius;
        invalidateSelf();   // AOSP: setRadius ends in invalidateSelf(false)
    }
    public int getRadius() { return radius; }
}
