package com.google.android.gms.ads.formats;
// Ad Manager (formerly DFP) banner options. Inert on a de-Googled device.
public final class PublisherAdViewOptions {
    private PublisherAdViewOptions() { }
    public static final class Builder {
        public Builder setManualImpressionsEnabled(boolean enabled) { return this; }
        public Builder setAppEventListener(Object listener) { return this; }
        public PublisherAdViewOptions build() { return new PublisherAdViewOptions(); }
    }
    public boolean getManualImpressionsEnabled() { return false; }
}
