package com.google.android.play.core.assetpacks.model;

// Play Core asset-pack storage method. STORAGE_FILES means the pack's
// assets are extracted to the filesystem (readable via a path);
// APK_ASSETS means they live inside an APK and must be read via the
// AssetManager. Mac.apk extracts every bundle to the data dir's
// aa/Android tree, so the pack is STORAGE_FILES.
public final class AssetPackStorageMethod {
    private AssetPackStorageMethod() {}
    public static final int STORAGE_FILES = 0;
    public static final int APK_ASSETS = 1;
}
