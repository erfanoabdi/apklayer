package com.google.android.play.core.assetpacks.model;

// Play Core asset-pack download status constants. Values match the
// Play Core / Play Asset Delivery library exactly (AOSP / Google Play
// Core reference) -- Unity's PlayAssetDeliveryAssetBundleProvider gates
// the bundle load on status()==COMPLETED (4), so the value is
// load-bearing.
public final class AssetPackStatus {
    private AssetPackStatus() {}
    public static final int UNKNOWN = 0;
    public static final int PENDING = 1;
    public static final int DOWNLOADING = 2;
    public static final int TRANSFERRING = 3;
    public static final int COMPLETED = 4;
    public static final int FAILED = 5;
    public static final int CANCELED = 6;
    public static final int WAITING_FOR_WIFI = 7;
    public static final int NOT_INSTALLED = 8;
    public static final int REQUIRES_USER_CONFIRMATION = 9;
}
