// com.google.android.gms.ads.AdLoader
//
// GMS Ads reference: loads native ads. On a de-Googled device with no ad
// backend, every load fails NO_FILL (delivered to the AdListener async
// like the real SDK); the OnNativeAdLoadedListener is never invoked. No
// native ad is ever produced.
package com.google.android.gms.ads;

import android.content.Context;

import com.google.android.gms.ads.internal.MainThread;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;

public class AdLoader {
    private final AdListener adListener;

    private AdLoader(AdListener adListener) {
        this.adListener = adListener;
    }

    public void loadAd(AdRequest adRequest) {
        failNoFill();
    }

    public void loadAds(AdRequest adRequest, int numberOfAds) {
        failNoFill();
    }

    public boolean isLoading() { return false; }

    public String getAdUnitId() { return null; }

    private void failNoFill() {
        final AdListener l = adListener;
        if (l == null) return;
        MainThread.post(new Runnable() {
            @Override public void run() { l.onAdFailedToLoad(LoadAdError.noFill()); }
        });
    }

    public static class Builder {
        private final Context context;
        private final String adUnitId;
        private AdListener adListener;

        public Builder(Context context, String adUnitId) {
            this.context = context;
            this.adUnitId = adUnitId;
        }

        public Builder forNativeAd(NativeAd.OnNativeAdLoadedListener listener) {
            return this;
        }

        public Builder withAdListener(AdListener listener) {
            this.adListener = listener;
            return this;
        }

        public Builder withNativeAdOptions(NativeAdOptions options) {
            return this;
        }

        public AdLoader build() {
            return new AdLoader(adListener);
        }
    }
}
