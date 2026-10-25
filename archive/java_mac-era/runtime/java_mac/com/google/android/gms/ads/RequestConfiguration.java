// com.google.android.gms.ads.RequestConfiguration
//
// GMS Ads reference: global request configuration set via
// MobileAds.setRequestConfiguration. Purely a local settings holder; no
// backend involved, so we keep the real builder + getters.
package com.google.android.gms.ads;

import java.util.ArrayList;
import java.util.List;

public final class RequestConfiguration {
    // Child-directed / under-age / content-rating constants from the ref.
    public static final int TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED = -1;
    public static final int TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE = 0;
    public static final int TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE = 1;

    public static final int TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED = -1;
    public static final int TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE = 0;
    public static final int TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE = 1;

    public static final String MAX_AD_CONTENT_RATING_UNSPECIFIED = "";
    public static final String MAX_AD_CONTENT_RATING_G = "G";
    public static final String MAX_AD_CONTENT_RATING_PG = "PG";
    public static final String MAX_AD_CONTENT_RATING_T = "T";
    public static final String MAX_AD_CONTENT_RATING_MA = "MA";

    public static final int PUBLISHER_PRIVACY_PERSONALIZATION_STATE_DEFAULT = 0;
    public static final int PUBLISHER_PRIVACY_PERSONALIZATION_STATE_ENABLED = 1;
    public static final int PUBLISHER_PRIVACY_PERSONALIZATION_STATE_DISABLED = 2;

    private final int childDirected;
    private final int underAge;
    private final String maxAdContentRating;
    private final List<String> testDeviceIds;
    private final int publisherPrivacyPersonalizationState;

    private RequestConfiguration(Builder b) {
        this.childDirected = b.childDirected;
        this.underAge = b.underAge;
        this.maxAdContentRating = b.maxAdContentRating;
        this.testDeviceIds = b.testDeviceIds;
        this.publisherPrivacyPersonalizationState = b.publisherPrivacyPersonalizationState;
    }

    public int getTagForChildDirectedTreatment() { return childDirected; }

    public int getTagForUnderAgeOfConsent() { return underAge; }

    public String getMaxAdContentRating() { return maxAdContentRating; }

    public List<String> getTestDeviceIds() { return testDeviceIds; }

    public int getPublisherPrivacyPersonalizationState() {
        return publisherPrivacyPersonalizationState;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.childDirected = childDirected;
        b.underAge = underAge;
        b.maxAdContentRating = maxAdContentRating;
        b.testDeviceIds = new ArrayList<String>(testDeviceIds);
        b.publisherPrivacyPersonalizationState = publisherPrivacyPersonalizationState;
        return b;
    }

    public static final class Builder {
        private int childDirected = TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED;
        private int underAge = TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED;
        private String maxAdContentRating = MAX_AD_CONTENT_RATING_UNSPECIFIED;
        private List<String> testDeviceIds = new ArrayList<String>();
        private int publisherPrivacyPersonalizationState =
                PUBLISHER_PRIVACY_PERSONALIZATION_STATE_DEFAULT;

        public Builder setTagForChildDirectedTreatment(int v) {
            this.childDirected = v;
            return this;
        }

        public Builder setTagForUnderAgeOfConsent(int v) {
            this.underAge = v;
            return this;
        }

        public Builder setMaxAdContentRating(String v) {
            this.maxAdContentRating = v == null ? MAX_AD_CONTENT_RATING_UNSPECIFIED : v;
            return this;
        }

        public Builder setTestDeviceIds(List<String> ids) {
            this.testDeviceIds = ids == null ? new ArrayList<String>() : new ArrayList<String>(ids);
            return this;
        }

        public Builder setPublisherPrivacyPersonalizationState(int v) {
            this.publisherPrivacyPersonalizationState = v;
            return this;
        }

        public RequestConfiguration build() {
            return new RequestConfiguration(this);
        }
    }
}
