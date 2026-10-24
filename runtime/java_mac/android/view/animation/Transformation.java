// android.view.animation.Transformation -- the per-frame output of an Animation:
// an alpha value plus a 3x3 Matrix (and optionally a clip rect / insets). Was an
// empty stub. Animation.getTransformation() fills one of these each frame and the
// view draw path consumes mAlpha + mMatrix; without real fields a running
// animation produced no visible change.
// Mirrors AOSP frameworks/base/core/java/android/view/animation/Transformation.java.
package android.view.animation;

import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Rect;

public class Transformation {
    public static final int TYPE_IDENTITY = 0x0;
    public static final int TYPE_ALPHA = 0x1;
    public static final int TYPE_MATRIX = 0x2;
    public static final int TYPE_BOTH = 0x3;

    protected Matrix mMatrix;
    protected float mAlpha;
    protected int mTransformationType;

    private boolean mHasClipRect;
    private Rect mClipRect = new Rect();
    private Insets mInsets = Insets.NONE;

    public Transformation() {
        clear();
    }

    public void clear() {
        if (mMatrix == null) mMatrix = new Matrix();
        else mMatrix.reset();
        mClipRect.setEmpty();
        mHasClipRect = false;
        mInsets = Insets.NONE;
        mAlpha = 1.0f;
        mTransformationType = TYPE_BOTH;
    }

    public int getTransformationType() { return mTransformationType; }
    public void setTransformationType(int transformationType) {
        mTransformationType = transformationType;
    }

    public void set(Transformation t) {
        mAlpha = t.getAlpha();
        mMatrix.set(t.getMatrix());
        if (t.mHasClipRect) {
            setClipRect(t.getClipRect());
        } else {
            mHasClipRect = false;
            mClipRect.setEmpty();
        }
        mInsets = t.getInsets();
        mTransformationType = t.getTransformationType();
    }

    // AOSP: pre-concat matrices, multiply alphas, union clip rects.
    public void compose(Transformation t) {
        mAlpha *= t.getAlpha();
        mMatrix.preConcat(t.getMatrix());
        if (t.mHasClipRect) {
            Rect tClip = t.getClipRect();
            if (mHasClipRect) {
                setClipRect(mClipRect.left + tClip.left, mClipRect.top + tClip.top,
                        mClipRect.right + tClip.right, mClipRect.bottom + tClip.bottom);
            } else {
                setClipRect(tClip);
            }
        }
    }

    // postCompose: t is applied AFTER this (AOSP TransformationSet ordering).
    public void postCompose(Transformation t) {
        mAlpha *= t.getAlpha();
        mMatrix.postConcat(t.getMatrix());
        if (t.mHasClipRect) {
            Rect tClip = t.getClipRect();
            if (mHasClipRect) {
                setClipRect(mClipRect.left + tClip.left, mClipRect.top + tClip.top,
                        mClipRect.right + tClip.right, mClipRect.bottom + tClip.bottom);
            } else {
                setClipRect(tClip);
            }
        }
    }

    public Matrix getMatrix() { return mMatrix; }

    public void setAlpha(float alpha) { mAlpha = alpha; }
    public float getAlpha() { return mAlpha; }

    public void setClipRect(Rect r) { setClipRect(r.left, r.top, r.right, r.bottom); }
    public void setClipRect(int l, int t, int r, int b) {
        mClipRect.set(l, t, r, b);
        mHasClipRect = true;
    }
    public Rect getClipRect() { return mClipRect; }
    public boolean hasClipRect() { return mHasClipRect; }

    public void setInsets(Insets insets) { mInsets = insets; }
    public void setInsets(int l, int t, int r, int b) { mInsets = Insets.of(l, t, r, b); }
    public Insets getInsets() { return mInsets; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("Transformation");
        toShortString(sb);
        return sb.toString();
    }

    public String toShortString() {
        StringBuilder sb = new StringBuilder(64);
        toShortString(sb);
        return sb.toString();
    }

    private void toShortString(StringBuilder sb) {
        sb.append("{alpha=").append(mAlpha).append(" matrix=").append(mMatrix).append('}');
    }
}
