package com.google.android.play.core.assetpacks;

import android.app.Activity;

import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.Map;

// com.google.android.play.core.assetpacks.AssetPackManager -- the Play Core
// asset-pack download/locate API. Crossy (and any Unity title using Play
// Asset Delivery) ships its Addressables bundles as PAD asset packs;
// Unity's PlayAssetDeliveryAssetBundleProvider gates each bundle load on the
// pack reporting status COMPLETED via this API, and resolves the bundle path
// via getPackLocation(...).assetsPath(). Mac.apk has no Google Play, but the
// bundles are present in the APK assets and extracted to the data dir, so
// every pack is effectively an install-time pack that is already COMPLETED.
//
// This interface mirrors the exact public method surface Unity's
// PlayAssetDeliveryUnityWrapper calls (getPackStates / getPackLocation /
// fetch / registerListener / unregisterListener / cancel / removePack /
// showCellularDataConfirmation) plus the standard Play Core methods, so the
// wrapper's reflection probe (it loadClass()es this type and getMethod()s
// "getPackStates" expecting a com.google.android.gms.tasks.Task return)
// succeeds and the real API is honoured. Returns gms.tasks.Task (not the
// play.core.tasks variant) to match the wrapper's exact signature check.
public interface AssetPackManager {
    // Location.
    AssetPackLocation getPackLocation(String packName);
    Map<String, AssetPackLocation> getPackLocations();
    AssetLocation getAssetLocation(String packName, String assetPath);

    // State + download. getPackStates is the load-bearing one: Unity's
    // status-query handler reads each AssetPackState.status() from this Task's
    // result. fetch() begins (a no-op already-complete) download.
    Task<AssetPackStates> getPackStates(List<String> packNames);
    Task<AssetPackStates> fetch(List<String> packNames);
    AssetPackStates cancel(List<String> packNames);

    // Listeners.
    void registerListener(AssetPackStateUpdateListener listener);
    void unregisterListener(AssetPackStateUpdateListener listener);
    void clearListeners();

    // Pack management.
    Task<Void> removePack(String packName);

    // User-confirmation dialogs (never needed -- packs are install-time, never
    // require a cellular/large-download confirmation -- but part of the API).
    Task<Integer> showConfirmationDialog(Activity activity);
    Task<Integer> showCellularDataConfirmation(Activity activity);
}
