package com.google.android.gms.ads.formats;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

// com.google.android.gms.ads.formats.MediaView -- hosts a native ad's main media.
public class MediaView extends FrameLayout {
    public MediaView(Context context) { super(context); }
    public MediaView(Context context, AttributeSet attrs) { super(context, attrs); }
    public void setImageScaleType(android.widget.ImageView.ScaleType scaleType) { }
    public void setMediaContent(Object mediaContent) { }
}
