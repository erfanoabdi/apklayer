package com.google.android.gms.wearable;

import com.google.android.gms.tasks.Task;
import java.util.Map;
import java.util.Set;

// com.google.android.gms.wearable.CapabilityClient -- discovers which Wear nodes
// advertise a given capability. No node is connected on a de-Googled device, so
// every query resolves to empty. Mirrors the published filter constants + method
// intent; CapabilityInfo is modeled as Object to avoid pulling in the full model
// since nothing produces a populated one here.
// Ref: developers.google.com/android/reference/.../wearable/CapabilityClient
public interface CapabilityClient {

    int FILTER_ALL = 0;
    int FILTER_REACHABLE = 1;

    Task<Map<String, Object>> getAllCapabilities(int nodeFilter);

    Task<Object> getCapability(String capability, int nodeFilter);

    Task<Void> addListener(OnCapabilityChangedListener listener, String capability);

    Task<Boolean> removeListener(OnCapabilityChangedListener listener);

    interface OnCapabilityChangedListener {
        void onCapabilityChanged(Object capabilityInfo);
    }
}
