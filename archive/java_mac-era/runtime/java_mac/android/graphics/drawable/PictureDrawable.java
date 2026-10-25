// android.graphics.drawable.PictureDrawable -- AOSP Drawable that plays
// back a recorded Picture, scaled to fit its bounds. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/PictureDrawable.java
// AndroidSVG and similar libraries render into a Picture, then wrap it in
// a PictureDrawable for display. Our Picture replays its recorded ops via
// Picture.draw(Canvas); here we scale to the bounds then forward.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Picture;
import android.graphics.Rect;

public class PictureDrawable extends Drawable {
    private Picture mPicture;

    public PictureDrawable(Picture picture) { mPicture = picture; }

    public Picture getPicture() { return mPicture; }
    public void setPicture(Picture picture) { mPicture = picture; invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        if (mPicture == null) return;
        final Rect bounds = getBounds();
        final int saved = canvas.save();
        canvas.clipRect(bounds);
        // Scale the recorded picture to fill the bounds.
        int pw = mPicture.getWidth();
        int ph = mPicture.getHeight();
        if (pw > 0 && ph > 0) {
            canvas.translate(bounds.left, bounds.top);
            canvas.scale((float) bounds.width() / pw, (float) bounds.height() / ph);
        }
        mPicture.draw(canvas);
        canvas.restoreToCount(saved);
    }

    @Override public int getIntrinsicWidth() {
        return mPicture != null ? mPicture.getWidth() : -1;
    }
    @Override public int getIntrinsicHeight() {
        return mPicture != null ? mPicture.getHeight() : -1;
    }
    @Override public void setAlpha(int alpha) { /* Picture has no alpha state */ }
    @Override public void setColorFilter(ColorFilter cf) { /* not supported by Picture */ }
    @Override public int getOpacity() { return -3; /* TRANSLUCENT */ }
}
