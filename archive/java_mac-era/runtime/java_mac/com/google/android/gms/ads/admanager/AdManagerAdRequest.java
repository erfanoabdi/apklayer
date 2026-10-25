package com.google.android.gms.ads.admanager;
public final class AdManagerAdRequest {
    private AdManagerAdRequest() {}
    public static final class Builder extends com.google.android.gms.ads.AbstractAdRequestBuilder<Builder> {
        public Builder() {}
        public Builder addCustomTargeting(String key, String value) { return this; }
        public Builder addCustomTargeting(String key, java.util.List<String> values) { return this; }
        public Builder setPublisherProvidedId(String publisherProvidedId) { return this; }
        public Builder addCategoryExclusion(String categoryExclusion) { return this; }
        public AdManagerAdRequest build() { return new AdManagerAdRequest(); }
    }
}
