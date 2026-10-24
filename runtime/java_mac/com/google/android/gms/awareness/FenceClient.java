package com.google.android.gms.awareness;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.awareness.FenceClient -- registers context "fences"
// (geofence-like contextual triggers). The Awareness backend is absent on a
// de-Googled device, so updateFences / queryFences fail cleanly. The fence
// request/query model types are passed as Object to avoid modeling the full
// Awareness surface. Mirrors the published intent.
// Ref: developers.google.com/android/reference/.../awareness/FenceClient
public interface FenceClient {

    Task<Void> updateFences(Object fenceUpdateRequest);

    Task<Object> queryFences(Object fenceQueryRequest);
}
