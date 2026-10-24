// com.google.android.gms.ads.initialization.AdapterStatus
//
// GMS Ads reference: per-mediation-adapter readiness reported in
// InitializationStatus. On a no-backend device the only adapter is the
// core SDK, which we report READY so init proceeds; no real mediation
// network is contacted.
package com.google.android.gms.ads.initialization;

public interface AdapterStatus {
    enum State { NOT_READY, READY }

    State getInitializationState();

    String getDescription();

    int getLatency();
}
