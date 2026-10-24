package com.google.android.play.core.assetpacks;

// com.google.android.play.core.assetpacks.AssetPackStateUpdateListener --
// the callback Play Core invokes as a pack's download state changes. In
// Play Core this extends com.google.android.play.core.listener
// .StateUpdateListener<AssetPackState>; the only method callers use is
// onStateUpdate(AssetPackState). Unity registers one via the wrapper's
// registerDownloadStatusListener. On Mac.apk packs are install-time
// (already COMPLETED), so AssetPackManagerImpl fires onStateUpdate once
// with COMPLETED right after registration (and on each fetch/getPackStates),
// matching real Play Core's behaviour of notifying the current state.
public interface AssetPackStateUpdateListener {
    void onStateUpdate(AssetPackState state);
}
