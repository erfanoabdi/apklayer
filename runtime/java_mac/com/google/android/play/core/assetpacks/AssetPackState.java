package com.google.android.play.core.assetpacks;

// com.google.android.play.core.assetpacks.AssetPackState -- the download
// state of a single asset pack. In Play Core this is an abstract class
// (AutoValue base); we mirror the public accessor surface exactly so
// Unity's PlayAssetDeliveryUnityWrapper status-query handler (which calls
// name()/status()/errorCode()) and the C# PlayAssetDelivery runtime read
// the expected values. On Mac.apk every pack is install-time (already in
// the APK assets, extracted to the data dir), so it is always COMPLETED
// with NO_ERROR and fully "downloaded".
public abstract class AssetPackState {
    public abstract String name();
    public abstract int status();
    public abstract int errorCode();
    public abstract long bytesDownloaded();
    public abstract long totalBytesToDownload();
    public abstract int transferProgressPercentage();

    // Factory for an already-installed (COMPLETED) pack of the given
    // total size. Used by AssetPackManagerImpl for every pack it reports.
    static AssetPackState completed(String name, long totalBytes) {
        return new CompletedAssetPackState(name, totalBytes);
    }

    private static final class CompletedAssetPackState extends AssetPackState {
        private final String name;
        private final long total;
        CompletedAssetPackState(String name, long total) {
            this.name = name; this.total = total;
        }
        @Override public String name() { return name; }
        @Override public int status() {
            return com.google.android.play.core.assetpacks.model.AssetPackStatus.COMPLETED;
        }
        @Override public int errorCode() {
            return com.google.android.play.core.assetpacks.model.AssetPackErrorCode.NO_ERROR;
        }
        @Override public long bytesDownloaded() { return total; }
        @Override public long totalBytesToDownload() { return total; }
        @Override public int transferProgressPercentage() { return 100; }

        @Override public String toString() {
            return "AssetPackState{name=" + name + ", status=COMPLETED, errorCode=0"
                + ", bytesDownloaded=" + total + ", totalBytesToDownload=" + total
                + ", transferProgressPercentage=100}";
        }
    }
}
