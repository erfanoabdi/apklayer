// android.widget.ImageSwitcher -- a ViewSwitcher whose two children are
// ImageViews; setImageResource/Drawable/URI push to the next child and flip.
// Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/ImageSwitcher.java
//   public class ImageSwitcher extends ViewSwitcher
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

public class ImageSwitcher extends ViewSwitcher {
    public ImageSwitcher(Context context) { super(context); }
    public ImageSwitcher(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public void setImageResource(int resid) {
        final ImageView image = (ImageView) getNextView();
        if (image != null) image.setImageResource(resid);
        showNext();
    }

    public void setImageURI(Uri uri) {
        final ImageView image = (ImageView) getNextView();
        if (image != null) image.setImageURI(uri);
        showNext();
    }

    public void setImageDrawable(Drawable drawable) {
        final ImageView image = (ImageView) getNextView();
        if (image != null) image.setImageDrawable(drawable);
        showNext();
    }
}
