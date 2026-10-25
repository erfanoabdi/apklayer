package com.google.android.play.core.assetpacks;

// com.google.android.play.core.assetpacks.AssetLocation -- the location of a
// SINGLE asset file within a pack (as opposed to AssetPackLocation, the pack
// root). Returned by AssetPackManager.getAssetLocation(packName, assetPath).
// Abstract in Play Core; we mirror path()/offset()/size(). Provided for API
// completeness (Unity's bundle path goes through AssetPackLocation.assetsPath,
// not this, but apps may call getAssetLocation directly).
public abstract class AssetLocation {
    public abstract long offset();
    public abstract String path();
    public abstract long size();

    static AssetLocation of(String path, long offset, long size) {
        return new SimpleAssetLocation(path, offset, size);
    }

    private static final class SimpleAssetLocation extends AssetLocation {
        private final String path;
        private final long offset;
        private final long size;
        SimpleAssetLocation(String path, long offset, long size) {
            this.path = path; this.offset = offset; this.size = size;
        }
        @Override public long offset() { return offset; }
        @Override public String path() { return path; }
        @Override public long size() { return size; }
    }
}
