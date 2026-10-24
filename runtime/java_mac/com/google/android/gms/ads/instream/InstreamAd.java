package com.google.android.gms.ads.instream;
/** An instream (in-video) ad. Never produced on a de-Googled device. */
public abstract class InstreamAd {
    public abstract float getMediaAspectRatio();
    public abstract void destroy();
    public interface InstreamAdLoadCallback {
        void onInstreamAdLoaded(InstreamAd ad);
        void onInstreamAdFailedToLoad(int errorCode);
    }
}
