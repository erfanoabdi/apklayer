package com.google.android.gms.awareness;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.awareness.SnapshotClient -- one-shot reads of the
// device's current context (location, activity, weather, headphone state). The
// Awareness backend is absent on a de-Googled device, so every snapshot read
// fails cleanly. NEVER fabricates context data. Responses are typed as Object to
// avoid modeling the full Awareness surface. Mirrors the published intent.
// Ref: developers.google.com/android/reference/.../awareness/SnapshotClient
public interface SnapshotClient {

    Task<Object> getDetectedActivity();

    Task<Object> getHeadphoneState();

    Task<Object> getLocation();

    Task<Object> getWeather();

    Task<Object> getTimeIntervals();

    Task<Object> getPlaces();
}
