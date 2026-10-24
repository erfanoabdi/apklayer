package com.google.android.play.core.assetpacks;

import java.util.Map;

// com.google.android.play.core.assetpacks.AssetPackStates -- the result of
// AssetPackManager.getPackStates(): a map of pack name -> AssetPackState
// plus the aggregate total byte count. Unity's status-query handler reads
// packStates() and totalBytes(); the C# PlayAssetDelivery runtime reads
// per-pack status to gate the bundle load. Abstract in Play Core; we
// mirror the public accessors.
public abstract class AssetPackStates {
    public abstract long totalBytes();
    public abstract Map<String, AssetPackState> packStates();

    static AssetPackStates of(long totalBytes, Map<String, AssetPackState> states) {
        return new SimpleAssetPackStates(totalBytes, states);
    }

    private static final class SimpleAssetPackStates extends AssetPackStates {
        private final long total;
        private final Map<String, AssetPackState> states;
        SimpleAssetPackStates(long total, Map<String, AssetPackState> states) {
            this.total = total; this.states = states;
        }
        @Override public long totalBytes() { return total; }
        @Override public Map<String, AssetPackState> packStates() { return states; }
        @Override public String toString() {
            return "AssetPackStates{totalBytes=" + total + ", packStates=" + states + "}";
        }
    }
}
