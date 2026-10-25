package com.google.android.gms.ads.instream;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
public final class InstreamAdView extends FrameLayout {
    public InstreamAdView(Context context) { super(context); }
    public InstreamAdView(Context context, AttributeSet attrs) { super(context, attrs); }
    public InstreamAdView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }
    public void setInstreamAd(InstreamAd instreamAd) { }
    public void destroy() { }
}
