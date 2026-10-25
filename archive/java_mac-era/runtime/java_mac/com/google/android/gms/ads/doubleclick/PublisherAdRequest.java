package com.google.android.gms.ads.doubleclick;
public final class PublisherAdRequest {
    private PublisherAdRequest() {}
    public static final class Builder extends com.google.android.gms.ads.AbstractAdRequestBuilder<Builder> {
        public Builder() {}
        public Builder addCustomTargeting(String key, String value) { return this; }
        public Builder addCustomTargeting(String key, java.util.List<String> values) { return this; }
        public Builder setPublisherProvidedId(String publisherProvidedId) { return this; }
        public Builder addCategoryExclusion(String categoryExclusion) { return this; }
        public Builder setAppEventListener(AppEventListener appEventListener) { return this; }
        public PublisherAdRequest build() { return new PublisherAdRequest(); }
    }
}
