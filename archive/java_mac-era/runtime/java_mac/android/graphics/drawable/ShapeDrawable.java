// android.graphics.drawable.ShapeDrawable -- AOSP Drawable that paints a
// shapes.Shape with a Paint. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/ShapeDrawable.java
// SwipeRefreshLayout's CircleImageView and many custom backgrounds build
// a ShapeDrawable(OvalShape/RectShape/...). Previously draw() was a no-op
// so these painted nothing; this version resizes the shape to the
// drawable bounds (honoring padding) and draws it with the configured
// paint + alpha + colorFilter + tint, then runs the optional
// ShaderFactory to install a size-dependent shader.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.shapes.Shape;

public class ShapeDrawable extends Drawable {
    private Shape mShape;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ShaderFactory mShaderFactory;
    private int mAlpha = 0xFF;
    /** AOSP ShapeState.mIntrinsicWidth/mIntrinsicHeight are plain int fields, so
     *  they default to ZERO -- ShapeDrawable is the one drawable in the package
     *  whose "no intrinsic size" answer is 0 rather than Drawable's -1
     *  (ShapeDrawable.java:547-548, DRW.sd.bare.iw = "0x0"). The difference is
     *  visible to View.getSuggestedMinimumWidth only via getMinimumWidth, which
     *  collapses both to 0, but it is visible to any caller that branches on
     *  `intrinsic < 0` to mean "unspecified". */
    private int mIntrinsicWidth;
    private int mIntrinsicHeight;
    private final Rect mPadding = new Rect();

    public ShapeDrawable() {}
    public ShapeDrawable(Shape shape) {
        mShape = shape;
        updateShape();
    }

    public Shape getShape() { return mShape; }
    public void setShape(Shape shape) { mShape = shape; updateShape(); invalidateSelf(); }
    public Paint getPaint() { return mPaint; }
    public ShaderFactory getShaderFactory() { return mShaderFactory; }
    public void setShaderFactory(ShaderFactory f) { mShaderFactory = f; }

    public void setIntrinsicWidth(int width)  { mIntrinsicWidth = width; invalidateSelf(); }
    public void setIntrinsicHeight(int height) { mIntrinsicHeight = height; invalidateSelf(); }
    @Override public int getIntrinsicWidth()  { return mIntrinsicWidth; }
    @Override public int getIntrinsicHeight() { return mIntrinsicHeight; }

    // v1.0.498 diagnostic accessors: a filled button whose ShapeDrawable fill
    // never paints shows here whether the paint color / shape / tint is set.
    public int macapkDumpPaintColor() { return mPaint.getColor(); }
    public String macapkDumpShape() { return mShape == null ? "null" : mShape.getClass().getSimpleName(); }
    public int macapkDumpAlpha() { return mAlpha; }
    public int macapkDumpTint() { return macapkEffectiveTint(); }

    /** AOSP models "no padding" as a NULL ShapeState.mPadding, and setPadding
     *  nulls it when all four sides are zero (ShapeDrawable.java, setPadding).
     *  mHasPadding stands in for that null so the scratch Rect can stay final.
     *  The old `!mPadding.isEmpty()` test was Rect.isEmpty (left >= right), which
     *  is a geometry question, not a "was a padding set" one: it answered false
     *  for a legitimate asymmetric padding like (5,4,3,6). */
    private boolean mHasPadding;
    public void setPadding(int left, int top, int right, int bottom) {
        if ((left | top | right | bottom) == 0) {
            mHasPadding = false;
            mPadding.set(0, 0, 0, 0);
        } else {
            mHasPadding = true;
            mPadding.set(left, top, right, bottom);
        }
        invalidateSelf();
    }
    public void setPadding(Rect padding) {
        if (padding == null) setPadding(0, 0, 0, 0);
        else setPadding(padding.left, padding.top, padding.right, padding.bottom);
    }
    @Override public boolean getPadding(Rect padding) {
        if (mHasPadding) {
            if (padding != null) padding.set(mPadding);
            return true;
        }
        return super.getPadding(padding);
    }

    @Override public void draw(Canvas canvas) {
        if (mShape == null) return;
        // Resize lazily: our base Drawable.setBounds does not dispatch
        // onBoundsChange, so sync the shape size here. Shape.resize
        // early-returns when the size is unchanged, so this is cheap.
        updateShape();
        final Rect r = getBounds();
        final int count = canvas.save();
        canvas.translate(r.left + mPadding.left, r.top + mPadding.top);

        // Apply the per-frame alpha + colorFilter to a temporary paint state.
        final Paint paint = mPaint;
        final int prevAlpha = paint.getAlpha();
        final ColorFilter prevCf = paint.getColorFilter();
        // Combine the drawable alpha with the paint alpha (AOSP modulates).
        paint.setAlpha(modulateAlpha(prevAlpha, mAlpha));
        // Honor a set tint via a colorFilter if no explicit filter is present.
        int tint = macapkEffectiveTint();
        if (prevCf == null && tint != 0) {
            paint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                tint, mTintMode));
        }

        // The explicit colour filter on a FLAT colour (MACAPK_DRAWABLE_COLOR_FILTER),
        // for the same reason as ColorDrawable.draw: AOSP (android14-release
        // ShapeDrawable.java:229-265) draws the shape with the filter on the
        // paint, and Skia filters the modulated paint colour, so SRC_IN keeps
        // its alpha; our Canvas's solidColorForFilter answers SRC_IN with the
        // filter colour verbatim. Filtered here exactly, once, only where this
        // class knows the whole draw is that one paint colour: no shader, the
        // framework's own onDraw (a subclass may set colours of its own), and
        // one of the framework's own Shapes (none of them touches the paint).
        // Anything else keeps the filter on the paint, the pre-change path.
        final int prevColor = paint.getColor();
        boolean prefiltered = false;
        if (MACAPK_DRAWABLE_COLOR_FILTER && prevCf != null && paint.getShader() == null
                && !MACAPK_ONDRAW_OVERRIDDEN.get(getClass())
                && macapkIsFrameworkShape(mShape)) {
            final Integer f = macapkFilterColor(prevCf, paint.getColor());
            if (f != null) {
                paint.setColorFilter(null);
                paint.setColor(f);
                prefiltered = true;
            }
        }

        if (!prefiltered || (paint.getColor() >>> 24) != 0) {
            onDraw(mShape, canvas, paint);
        }

        if (prefiltered) paint.setColor(prevColor);
        paint.setAlpha(prevAlpha);
        paint.setColorFilter(prevCf);
        canvas.restoreToCount(count);
    }

    /** Does this runtime class override onDraw(Shape, Canvas, Paint) anywhere
     *  below ShapeDrawable? Computed once per class. */
    private static final ClassValue<Boolean> MACAPK_ONDRAW_OVERRIDDEN = new ClassValue<Boolean>() {
        @Override protected Boolean computeValue(Class<?> c) {
            for (Class<?> k = c; k != null && k != ShapeDrawable.class; k = k.getSuperclass()) {
                try {
                    k.getDeclaredMethod("onDraw", Shape.class, Canvas.class, Paint.class);
                    return Boolean.TRUE;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    return Boolean.TRUE;   // cannot tell: keep the paint path
                }
            }
            return Boolean.FALSE;
        }
    };

    private static boolean macapkIsFrameworkShape(Shape s) {
        if (s == null) return false;
        final Class<?> c = s.getClass();
        return c == android.graphics.drawable.shapes.RectShape.class
            || c == android.graphics.drawable.shapes.OvalShape.class
            || c == android.graphics.drawable.shapes.RoundRectShape.class
            || c == android.graphics.drawable.shapes.ArcShape.class
            || c == android.graphics.drawable.shapes.PathShape.class;
    }

    /** AOSP ShapeDrawable.onDraw(Shape, Canvas, Paint) -- "Called from the
     *  drawable's draw() method after the canvas has been set to draw the shape
     *  at (0,0). Subclasses can override for special effects such as multiple
     *  layers, stroking, etc." PROTECTED, and the ONLY reason draw() is not
     *  final: subclasses (PaintDrawable's callers, custom shape drawables)
     *  override this to decorate. Ours must therefore be the call draw() makes,
     *  or an override would never run. */
    protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
        if (shape != null) shape.draw(canvas, paint);
    }

    /** AOSP ShapeDrawable.inflateTag(name, r, parser, attrs) -- "Subclasses
     *  override this to handle custom child tags inside <shape>. Returns true if
     *  the tag was handled". The BASE handles nothing and returns false; the
     *  hook exists so a subclass can be called. Being absent meant an app's
     *  override was never dispatched AND `super.inflateTag(...)` did not link. */
    protected boolean inflateTag(String name, android.content.res.Resources r,
            org.xmlpull.v1.XmlPullParser parser, android.util.AttributeSet attrs) {
        return false;
    }

    @Override public void onBoundsChange(Rect bounds) {
        updateShape();
    }

    private void updateShape() {
        if (mShape == null) return;
        final Rect r = getBounds();
        int w = r.width() - mPadding.left - mPadding.right;
        int h = r.height() - mPadding.top - mPadding.bottom;
        mShape.resize(w, h);
        if (mShaderFactory != null) {
            Shader s = mShaderFactory.resize(Math.max(0, w), Math.max(0, h));
            if (s != null) mPaint.setShader(s);
        }
    }

    @Override public void setAlpha(int alpha) { mAlpha = alpha; invalidateSelf(); }
    @Override public int getAlpha() { return mAlpha; }
    @Override public void setColorFilter(ColorFilter cf) { mPaint.setColorFilter(cf); invalidateSelf(); }
    @Override public ColorFilter getColorFilter() { return mPaint.getColorFilter(); }
    /** AOSP ShapeDrawable.java:323-339, and it is the reverse of the intuitive
     *  reading: the PAINT is consulted only when there is NO shape, and the
     *  instant a Shape is attached the answer is unconditionally TRANSLUCENT
     *  ("not sure, so be safe" -- an arbitrary Shape may not cover its bounds).
     *  So a bare ShapeDrawable is OPAQUE (paint alpha 255) while an OvalShape one
     *  is TRANSLUCENT. Reading the paint in all cases inverted both answers
     *  (DRW.sd.bare.opacity, DRW.sd.bareAlpha128.opacity). */
    @Override public int getOpacity() {
        if (mShape == null) {
            final int alpha = mPaint.getAlpha();
            if (alpha == 0) return android.graphics.PixelFormat.TRANSPARENT;
            if (alpha == 255) return android.graphics.PixelFormat.OPAQUE;
        }
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

    /** AOSP :503-509: delegate to the Shape, then stamp this drawable's alpha.
     *  With NO shape the outline is left untouched (empty), not set to the
     *  bounds -- so ShapeDrawable deliberately does not fall back to
     *  Drawable.getOutline. */
    @Override public void getOutline(android.graphics.Outline outline) {
        if (mShape != null) {
            mShape.getOutline(outline);
            outline.setAlpha(getAlpha() / 255.0f);
        }
    }

    private static int modulateAlpha(int paintAlpha, int alpha) {
        int scale = alpha + (alpha >>> 7);
        return paintAlpha * scale >>> 8;
    }

    public static abstract class ShaderFactory {
        public abstract Shader resize(int width, int height);
    }
}
