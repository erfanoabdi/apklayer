// com.google.android.gms.ads.nativead.MediaView
//
// GMS Ads reference: the view that renders a native ad's media asset. On
// a no-backend device no media is ever delivered, so this stays an empty
// container. The setters exist so app code that configures it compiles.
package com.google.android.gms.ads.nativead;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.gms.ads.MediaContent;

public class MediaView extends FrameLayout {
    private MediaContent mediaContent;
    private ImageView.ScaleType scaleType;

    public MediaView(Context context) { super(context); }

    public MediaView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public MediaView(Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setMediaContent(MediaContent mediaContent) {
        this.mediaContent = mediaContent;
    }

    public MediaContent getMediaContent() { return mediaContent; }

    public void setImageScaleType(ImageView.ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public ImageView.ScaleType getImageScaleType() { return scaleType; }
}
