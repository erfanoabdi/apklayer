package com.google.android.play.core.appupdate;

import com.google.android.play.core.install.model.AppUpdateType;

// com.google.android.play.core.appupdate.AppUpdateOptions -- options bag passed
// to startUpdateFlowForResult. We honour the builder shape so apps compile and
// configure it normally; since no update is ever offered the values are unused.
public final class AppUpdateOptions {
    private final int appUpdateType;
    private final boolean allowAssetPackDeletion;

    private AppUpdateOptions(int appUpdateType, boolean allowAssetPackDeletion) {
        this.appUpdateType = appUpdateType;
        this.allowAssetPackDeletion = allowAssetPackDeletion;
    }

    public int appUpdateType() { return appUpdateType; }

    public boolean allowAssetPackDeletion() { return allowAssetPackDeletion; }

    public static AppUpdateOptions defaultOptions(int appUpdateType) {
        return newBuilder(appUpdateType).build();
    }

    public static Builder newBuilder(int appUpdateType) {
        return new Builder().setAppUpdateType(appUpdateType);
    }

    public static final class Builder {
        private int appUpdateType = AppUpdateType.FLEXIBLE;
        private boolean allowAssetPackDeletion;

        Builder() {}

        public Builder setAppUpdateType(int appUpdateType) {
            this.appUpdateType = appUpdateType;
            return this;
        }

        public Builder setAllowAssetPackDeletion(boolean allowAssetPackDeletion) {
            this.allowAssetPackDeletion = allowAssetPackDeletion;
            return this;
        }

        public AppUpdateOptions build() {
            return new AppUpdateOptions(appUpdateType, allowAssetPackDeletion);
        }
    }
}
