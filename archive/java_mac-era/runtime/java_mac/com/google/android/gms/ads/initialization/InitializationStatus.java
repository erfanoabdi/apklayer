// com.google.android.gms.ads.initialization.InitializationStatus
//
// GMS Ads reference: the per-adapter map handed to
// OnInitializationCompleteListener.onInitializationComplete. We report a
// single READY entry for the core SDK so apps see "initialized" and
// continue; no mediation network is actually contacted.
package com.google.android.gms.ads.initialization;

import java.util.LinkedHashMap;
import java.util.Map;

public interface InitializationStatus {
    Map<String, AdapterStatus> getAdapterStatusMap();

    // A ready-core-only status we hand to the init callback. No network.
    static InitializationStatus coreReady() {
        final Map<String, AdapterStatus> map = new LinkedHashMap<String, AdapterStatus>();
        map.put("com.google.android.gms.ads.MobileAds", new AdapterStatus() {
            @Override public State getInitializationState() { return State.READY; }
            @Override public String getDescription() { return "Ready"; }
            @Override public int getLatency() { return 0; }
        });
        return new InitializationStatus() {
            @Override public Map<String, AdapterStatus> getAdapterStatusMap() { return map; }
        };
    }
}
