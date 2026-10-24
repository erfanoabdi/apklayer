// android.app.SharedElementCallback -- the app-supplied hook into the Activity /
// Fragment shared-element ("hero image") transition, installed via
// Activity.setEnterSharedElementCallback / setExitSharedElementCallback. AOSP:
//   frameworks/base/core/java/android/app/SharedElementCallback.java
//   public abstract class SharedElementCallback
//
// v1.0.92 shipped this as an empty non-abstract class with none of the seven
// callbacks and no nested OnSharedElementsReadyListener. That shape breaks two
// separate things: an app that subclasses it and overrides, say,
// onMapSharedElements gets no override at all (the method does not exist to
// override, so an @Override-annotated APK class fails to resolve its supertype
// method), and any app class that merely REFERENCES
// SharedElementCallback$OnSharedElementsReadyListener fails to link.
//
// The bodies below are AOSP's own defaults: the four notification hooks are
// no-ops (apps override selectively), onSharedElementsArrived immediately
// signals readiness (AOSP: "The default behavior is to trigger the transfer
// immediately"), and the snapshot pair really rasterizes / rehydrates, because
// apps commonly override one of them and delegate the other case back to
// super.
package android.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import java.util.List;
import java.util.Map;

public abstract class SharedElementCallback {
    private Matrix mTempMatrix;

    // Same key strings AOSP uses, so a snapshot Bundle produced by one callback
    // is readable by another callback's onCreateSnapshotView (apps override one
    // side and call super on the other).
    private static final String BUNDLE_SNAPSHOT_BITMAP = "sharedElement:snapshot:bitmap";
    private static final String BUNDLE_SNAPSHOT_IMAGE_SCALETYPE =
            "sharedElement:snapshot:imageScaleType";
    private static final String BUNDLE_SNAPSHOT_IMAGE_MATRIX =
            "sharedElement:snapshot:imageMatrix";

    // AOSP TransitionUtils.MAX_IMAGE_SIZE: snapshots are scaled down uniformly
    // so a full-screen shared element cannot allocate an unbounded bitmap.
    private static final int MAX_IMAGE_SIZE = 1024 * 1024;

    /**
     * Called immediately before capturing the start of the shared element state
     * on enter/reenter transitions, and immediately before capturing the end of
     * the shared element state for exit/return transitions.
     *
     * @param sharedElementNames The names of the shared elements that were
     *                           accepted into the View hierarchy.
     * @param sharedElements The shared elements that are part of the View hierarchy.
     * @param sharedElementSnapshots The Views containing snapshots of the shared
     *                               element from the launching Window. Null for
     *                               Fragment transitions.
     */
    public void onSharedElementStart(List<String> sharedElementNames,
            List<View> sharedElements, List<View> sharedElementSnapshots) {}

    /**
     * Called immediately before capturing the end of the shared element state on
     * enter/reenter transitions, and immediately before capturing the start of
     * the shared element state for exit/return transitions.
     */
    public void onSharedElementEnd(List<String> sharedElementNames,
            List<View> sharedElements, List<View> sharedElementSnapshots) {}

    /**
     * Called after onMapSharedElements when transferring shared elements in. Any
     * shared elements with no mapping arrive here and will be transitioned out
     * of the Scene unless removed from the list.
     */
    public void onRejectSharedElements(List<View> rejectedSharedElements) {}

    /**
     * Lets the callback adjust the mapping of shared element names to Views. The
     * best guess (by transitionName) is already filled into sharedElements.
     */
    public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {}

    /**
     * Creates a snapshot of a shared element, to be reconstituted by
     * {@link #onCreateSnapshotView(Context, Parcelable)}. A null return means the
     * receiving side sees a null snapshot View, which AOSP documents as legal.
     *
     * <p>Mirrors AOSP: an ImageView whose drawable is not obscured by an opaque
     * background is snapshotted as a Bundle carrying the drawable's bitmap plus
     * the scale type (and image matrix when the scale type is MATRIX), so the
     * receiver can reproduce the exact scaling; anything else is snapshotted as
     * a plain Bitmap of the view rendered through viewToGlobalMatrix.</p>
     */
    public Parcelable onCaptureSharedElementSnapshot(View sharedElement,
            Matrix viewToGlobalMatrix, RectF screenBounds) {
        if (sharedElement instanceof ImageView) {
            ImageView imageView = ((ImageView) sharedElement);
            Drawable d = imageView.getDrawable();
            Drawable bg = imageView.getBackground();
            if (d != null && (bg == null || bg.getAlpha() == 0)) {
                Bitmap bitmap = createDrawableBitmap(d);
                if (bitmap != null) {
                    Bundle bundle = new Bundle();
                    // AOSP branches to a HardwareBuffer here when the bitmap
                    // config is HARDWARE. Our Bitmaps are always CPU-side
                    // (BufferedImage-backed), so that branch cannot be taken.
                    bundle.putParcelable(BUNDLE_SNAPSHOT_BITMAP, bitmap);
                    bundle.putString(BUNDLE_SNAPSHOT_IMAGE_SCALETYPE,
                            imageView.getScaleType().toString());
                    if (imageView.getScaleType() == ScaleType.MATRIX) {
                        Matrix matrix = imageView.getImageMatrix();
                        float[] values = new float[9];
                        if (matrix != null) matrix.getValues(values);
                        bundle.putFloatArray(BUNDLE_SNAPSHOT_IMAGE_MATRIX, values);
                    }
                    return bundle;
                }
            }
        }
        if (mTempMatrix == null) {
            mTempMatrix = new Matrix(viewToGlobalMatrix);
        } else {
            mTempMatrix.set(viewToGlobalMatrix);
        }
        return createViewBitmap(sharedElement, mTempMatrix, screenBounds);
    }

    /**
     * Reconstitutes a snapshot View from the Parcelable returned by
     * {@link #onCaptureSharedElementSnapshot(View, Matrix, RectF)}. The returned
     * View is sized and positioned by the caller afterwards.
     */
    public View onCreateSnapshotView(Context context, Parcelable snapshot) {
        View view = null;
        if (snapshot instanceof Bundle) {
            Bundle bundle = (Bundle) snapshot;
            Bitmap bitmap = bundle.getParcelable(BUNDLE_SNAPSHOT_BITMAP, Bitmap.class);
            if (bitmap == null) {
                return null;
            }
            ImageView imageView = new ImageView(context);
            view = imageView;
            imageView.setImageBitmap(bitmap);
            String scaleType = bundle.getString(BUNDLE_SNAPSHOT_IMAGE_SCALETYPE);
            if (scaleType != null) {
                imageView.setScaleType(ScaleType.valueOf(scaleType));
            }
            if (imageView.getScaleType() == ScaleType.MATRIX) {
                float[] values = bundle.getFloatArray(BUNDLE_SNAPSHOT_IMAGE_MATRIX);
                if (values != null) {
                    Matrix matrix = new Matrix();
                    matrix.setValues(values);
                    imageView.setImageMatrix(matrix);
                }
            }
        } else if (snapshot instanceof Bitmap) {
            Bitmap bitmap = (Bitmap) snapshot;
            view = new View(context);
            Resources resources = context.getResources();
            view.setBackground(new BitmapDrawable(resources, bitmap));
        }
        return view;
    }

    /**
     * Called when the shared elements have arrived at their final location and
     * are ready to be transferred. The listener must be called to trigger the
     * transfer; AOSP's default triggers it immediately.
     */
    public void onSharedElementsArrived(List<String> sharedElementNames,
            List<View> sharedElements, OnSharedElementsReadyListener listener) {
        listener.onSharedElementsReady();
    }

    /**
     * Listener called to indicate the shared elements are ready to be hidden in
     * the source Activity and shown in the destination Activity.
     */
    public interface OnSharedElementsReadyListener {
        void onSharedElementsReady();
    }

    // AOSP delegates the two rasterizers to android.transition.TransitionUtils,
    // which does not exist in this framework; the logic below is that class's
    // createDrawableBitmap / createViewBitmap, minus the Picture indirection
    // (we draw straight into the destination Bitmap's Canvas, same pixels) and
    // minus the ViewOverlay reparenting dance (our View.draw rasterizes through
    // Java2D whether or not the view is attached to a window, so a detached
    // shared element still snapshots).
    private static Bitmap createDrawableBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        float scale = Math.min(1f, ((float) MAX_IMAGE_SIZE) / (width * height));
        if (drawable instanceof BitmapDrawable && scale == 1f) {
            // Return the same bitmap when no scale-down is needed.
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int bitmapWidth = (int) (width * scale);
        int bitmapHeight = (int) (height * scale);
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // Restore the drawable's own bounds afterwards: it is still live in the
        // view hierarchy we are snapshotting.
        Rect existingBounds = drawable.getBounds();
        int left = existingBounds.left;
        int top = existingBounds.top;
        int right = existingBounds.right;
        int bottom = existingBounds.bottom;
        drawable.setBounds(0, 0, bitmapWidth, bitmapHeight);
        drawable.draw(canvas);
        drawable.setBounds(left, top, right, bottom);
        return bitmap;
    }

    private static Bitmap createViewBitmap(View view, Matrix matrix, RectF bounds) {
        if (bounds == null) {
            return null;
        }
        int bitmapWidth = Math.round(bounds.width());
        int bitmapHeight = Math.round(bounds.height());
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null;
        }
        float scale = Math.min(1f, ((float) MAX_IMAGE_SIZE) / (bitmapWidth * bitmapHeight));
        bitmapWidth *= scale;
        bitmapHeight *= scale;
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null;
        }
        // AOSP documents that the caller's matrix is modified here.
        matrix.postTranslate(-bounds.left, -bounds.top);
        matrix.postScale(scale, scale);
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.concat(matrix);
        view.draw(canvas);
        return bitmap;
    }
}
