package com.google.android.play.core.assetpacks;

import com.google.android.play.core.assetpacks.model.AssetPackStorageMethod;

// com.google.android.play.core.assetpacks.AssetPackLocation -- the on-disk
// location of an installed asset pack. Unity's PlayAssetDeliveryUnityWrapper
// .getAssetPackPath() returns assetsPath(); the C# PlayAssetDelivery runtime
// uses it to resolve the bundle file. Abstract in Play Core; we mirror
// path()/assetsPath()/packStorageMethod().
//
// TWO storage methods, and the distinction is load-bearing on Mac.apk:
//
//   STORAGE_FILES -- the pack's assets are extracted to the filesystem and
//   path()/assetsPath() return a real directory. Used for on-demand packs,
//   whose bundles Mac.apk extracts under <dataDir>/aa/Android.
//
//   APK_ASSETS -- the pack's assets live INSIDE an APK and must be read via
//   the AssetManager; per the Play Core contract path()/assetsPath() are
//   BOTH null ("To access assets from packs installed as APKs, use Asset
//   Manager."). Used for Unity's core install-time packs (UnityDataAssetPack
//   / UnityStreamingAssetsPack): their content is in the base APK assets, so
//   the Unity player must keep its default in-APK streaming-assets
//   RuntimePath (jar:file://<apk>!/assets/aa). Reporting a STORAGE_FILES dir
//   for the core packs instead makes the player RELOCATE the Addressables
//   RuntimePath to that dir, which routes the core bundles through a
//   local-file + UnityCache decompress path that stalls the boot before the
//   splash ever runs. APK_ASSETS (null paths) is the contract-correct "it is
//   in the APK" signal that keeps the working in-APK path.
public abstract class AssetPackLocation {
    public abstract int packStorageMethod();
    public abstract String path();
    public abstract String assetsPath();

    // STORAGE_FILES location (extracted-to-filesystem pack).
    static AssetPackLocation of(String path, String assetsPath) {
        return new SimpleAssetPackLocation(
            AssetPackStorageMethod.STORAGE_FILES, path, assetsPath);
    }

    // APK_ASSETS location (pack lives inside the APK; null paths per the
    // Play Core contract). Tells the Unity player to keep its in-APK path.
    static AssetPackLocation apkAssets() {
        return new SimpleAssetPackLocation(
            AssetPackStorageMethod.APK_ASSETS, null, null);
    }

    private static final class SimpleAssetPackLocation extends AssetPackLocation {
        private final int storageMethod;
        private final String path;
        private final String assetsPath;
        SimpleAssetPackLocation(int storageMethod, String path, String assetsPath) {
            this.storageMethod = storageMethod;
            this.path = path; this.assetsPath = assetsPath;
        }
        @Override public int packStorageMethod() { return storageMethod; }
        @Override public String path() { return path; }
        @Override public String assetsPath() { return assetsPath; }
        @Override public String toString() {
            return "AssetPackLocation{packStorageMethod=" + storageMethod
                + ", path=" + path + ", assetsPath=" + assetsPath + "}";
        }
    }
}
