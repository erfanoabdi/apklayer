package com.google.android.play.core.assetpacks;

import android.content.Context;

// com.google.android.play.core.assetpacks.AssetPackManagerFactory -- the Play
// Core entry point. Real apps and Unity's wrapper call
// AssetPackManagerFactory.getInstance(Context) to obtain the AssetPackManager.
// One instance per process (Play Core caches it); we do the same.
public final class AssetPackManagerFactory {
    private AssetPackManagerFactory() {}

    private static volatile AssetPackManager instance;

    public static AssetPackManager getInstance(Context context) {
        AssetPackManager m = instance;
        if (m == null) {
            synchronized (AssetPackManagerFactory.class) {
                m = instance;
                if (m == null) {
                    m = new AssetPackManagerImpl(
                        context != null ? context.getApplicationContext() : null);
                    instance = m;
                }
            }
        }
        return m;
    }
}
