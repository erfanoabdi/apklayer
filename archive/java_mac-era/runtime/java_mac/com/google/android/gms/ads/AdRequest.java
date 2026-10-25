// com.google.android.gms.ads.AdRequest
//
// GMS Ads reference: an ad request handed to AdView.loadAd / *.load. On a
// no-backend device the request is never sent; we keep the full builder
// surface so app code constructs it normally, then every load fails
// NO_FILL. No targeting data leaves the device.
package com.google.android.gms.ads;

import java.util.HashSet;
import java.util.Set;

public class AdRequest {
    // Legacy ErrorCode constants the older SDK exposed on AdRequest; some
    // apps still reference AdRequest.ERROR_CODE_NO_FILL.
    public static final int ERROR_CODE_INTERNAL_ERROR = 0;
    public static final int ERROR_CODE_INVALID_REQUEST = 1;
    public static final int ERROR_CODE_NETWORK_ERROR = 2;
    public static final int ERROR_CODE_NO_FILL = 3;
    public static final int ERROR_CODE_APP_ID_MISSING = 8;
    public static final int ERROR_CODE_MEDIATION_NO_FILL = 9;
    public static final int ERROR_CODE_REQUEST_ID_MISMATCH = 10;

    public static final String DEVICE_ID_EMULATOR = "B3EEABB8EE11C2BE770B684D95219ECB";

    private final Set<String> keywords;
    private final String contentUrl;
    private final android.os.Bundle extras;

    protected AdRequest(Builder b) {
        this.keywords = b.keywords;
        this.contentUrl = b.contentUrl;
        this.extras = b.extras;
    }

    public Set<String> getKeywords() { return keywords; }

    public String getContentUrl() { return contentUrl; }

    // Mediation/custom-event extras lookups. We never had a real network
    // adapter, so these return what the app put in (or empty).
    public android.os.Bundle getCustomEventExtrasBundle(Class<?> adapterClass) {
        return extras;
    }

    public android.os.Bundle getNetworkExtrasBundle(Class<?> adapterClass) {
        return extras;
    }

    public boolean isTestDevice(android.content.Context context) { return false; }

    public static class Builder extends AbstractAdRequestBuilder<Builder> {
        final Set<String> keywords = new HashSet<String>();
        String contentUrl;
        final android.os.Bundle extras = new android.os.Bundle();

        public Builder() { }

        public Builder addKeyword(String keyword) {
            if (keyword != null) keywords.add(keyword);
            return this;
        }

        public Builder setContentUrl(String contentUrl) {
            this.contentUrl = contentUrl;
            return this;
        }

        public Builder setRequestAgent(String requestAgent) { return this; }

        public Builder addNetworkExtrasBundle(Class<?> adNetworkClass,
                                              android.os.Bundle networkExtras) {
            return this;
        }

        public Builder addCustomEventExtrasBundle(Class<?> adapterClass,
                                                  android.os.Bundle customEventExtras) {
            return this;
        }

        public Builder setNeighboringContentUrls(java.util.List<String> urls) { return this; }

        public Builder setAdString(String adString) { return this; }

        public Builder setHttpTimeoutMillis(int httpTimeoutMillis) { return this; }

        public AdRequest build() { return new AdRequest(this); }
    }
}
