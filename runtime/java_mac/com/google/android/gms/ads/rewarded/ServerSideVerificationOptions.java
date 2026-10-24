// com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
//
// GMS Ads reference: SSV options set on a rewarded ad. Pure local data
// holder; never sent anywhere on a no-backend device.
package com.google.android.gms.ads.rewarded;

public class ServerSideVerificationOptions {
    private final String customData;
    private final String userId;

    private ServerSideVerificationOptions(Builder b) {
        this.customData = b.customData;
        this.userId = b.userId;
    }

    public String getCustomData() { return customData; }

    public String getUserId() { return userId; }

    public static final class Builder {
        private String customData = "";
        private String userId = "";

        public Builder setCustomData(String customData) {
            this.customData = customData == null ? "" : customData;
            return this;
        }

        public Builder setUserId(String userId) {
            this.userId = userId == null ? "" : userId;
            return this;
        }

        public ServerSideVerificationOptions build() {
            return new ServerSideVerificationOptions(this);
        }
    }
}
