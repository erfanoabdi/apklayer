// android.text.style.ImageSpan -- AOSP span that draws an inline image
// (Drawable / Bitmap / resource / Uri) in place of a text run. AOSP:
//   frameworks/base/core/java/android/text/style/ImageSpan.java
//   public class ImageSpan extends DynamicDrawableSpan
//
// Very common in chat / markdown / emoji rendering. The Drawable is resolved
// lazily and bounded to its intrinsic size when the caller hasn't already set
// bounds (mirrors AOSP, which expects the supplied Drawable to be pre-bounded
// but falls back to intrinsic size). DynamicDrawableSpan owns the measure +
// draw; ImageSpan only resolves the Drawable.
package android.text.style;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

public class ImageSpan extends DynamicDrawableSpan {
    private Drawable mDrawable;
    private final Context mContext;
    private final Uri mContentUri;
    private final int mResourceId;
    private String mSource;

    @Deprecated
    public ImageSpan(Bitmap b) {
        this(null, b, ALIGN_BOTTOM);
    }
    @Deprecated
    public ImageSpan(Bitmap b, int verticalAlignment) {
        this(null, b, verticalAlignment);
    }
    public ImageSpan(Context context, Bitmap b) {
        this(context, b, ALIGN_BOTTOM);
    }
    public ImageSpan(Context context, Bitmap b, int verticalAlignment) {
        super(verticalAlignment);
        mContext = context;
        mDrawable = (context != null)
            ? new BitmapDrawable(context.getResources(), b)
            : new BitmapDrawable(b);
        mContentUri = null;
        mResourceId = 0;
    }

    public ImageSpan(Drawable drawable) {
        this(drawable, ALIGN_BOTTOM);
    }
    public ImageSpan(Drawable drawable, int verticalAlignment) {
        super(verticalAlignment);
        mDrawable = drawable;
        mContext = null;
        mContentUri = null;
        mResourceId = 0;
    }
    public ImageSpan(Drawable drawable, String source) {
        this(drawable, source, ALIGN_BOTTOM);
    }
    public ImageSpan(Drawable drawable, String source, int verticalAlignment) {
        super(verticalAlignment);
        mDrawable = drawable;
        mSource = source;
        mContext = null;
        mContentUri = null;
        mResourceId = 0;
    }

    public ImageSpan(Context context, Uri uri) {
        this(context, uri, ALIGN_BOTTOM);
    }
    public ImageSpan(Context context, Uri uri, int verticalAlignment) {
        super(verticalAlignment);
        mContext = context;
        mContentUri = uri;
        mSource = (uri != null) ? uri.toString() : null;
        mResourceId = 0;
    }

    public ImageSpan(Context context, int resourceId) {
        this(context, resourceId, ALIGN_BOTTOM);
    }
    public ImageSpan(Context context, int resourceId, int verticalAlignment) {
        super(verticalAlignment);
        mContext = context;
        mResourceId = resourceId;
        mContentUri = null;
    }

    @Override
    public Drawable getDrawable() {
        if (mDrawable != null) {
            return boundIfNeeded(mDrawable);
        }
        Drawable drawable = null;
        if (mContentUri != null && mContext != null) {
            try {
                java.io.InputStream is = mContext.getContentResolver()
                    .openInputStream(mContentUri);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                drawable = new BitmapDrawable(mContext.getResources(), bitmap);
                if (is != null) is.close();
            } catch (Exception e) {
                // AOSP logs "Failed to loaded content " + uri; we resolve null.
            }
        } else if (mContext != null) {
            try {
                drawable = mContext.getResources().getDrawable(mResourceId);
            } catch (Exception e) {
                // missing resource -> null, like AOSP's catch
            }
        }
        mDrawable = drawable;
        return boundIfNeeded(drawable);
    }

    private static Drawable boundIfNeeded(Drawable d) {
        if (d == null) return null;
        if (d.getBounds().isEmpty()) {
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            if (w > 0 && h > 0) d.setBounds(0, 0, w, h);
        }
        return d;
    }

    public String getSource() { return mSource; }

    public Uri getContentUri() { return mContentUri; }

    public int getResourceId() { return mResourceId; }

    public Drawable getDrawableLayout() { return mDrawable; }
}
