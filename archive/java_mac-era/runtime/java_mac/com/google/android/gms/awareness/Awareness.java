package com.google.android.gms.awareness;

import android.content.Context;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.awareness.Awareness -- entry point for the Awareness API
// (Fence + Snapshot). The Awareness backend is absent on a de-Googled device, so
// every client operation fails cleanly (API_NOT_CONNECTED) and NEVER fabricates
// context data. Mirrors the published static factory.
// Ref: developers.google.com/android/reference/.../awareness/Awareness
public final class Awareness {
    private Awareness() {}

    public static FenceClient getFenceClient(Context context) { return FENCE; }
    public static SnapshotClient getSnapshotClient(Context context) { return SNAPSHOT; }

    private static <T> Task<T> notConnected() {
        return Tasks.forException(new ApiException(
                new Status(CommonStatusCodes.API_NOT_CONNECTED, "Awareness unavailable")));
    }

    private static final FenceClient FENCE = new FenceClient() {
        @Override public Task<Void> updateFences(Object fenceUpdateRequest) { return notConnected(); }
        @Override public Task<Object> queryFences(Object fenceQueryRequest) { return notConnected(); }
    };

    private static final SnapshotClient SNAPSHOT = new SnapshotClient() {
        @Override public Task<Object> getDetectedActivity() { return notConnected(); }
        @Override public Task<Object> getHeadphoneState() { return notConnected(); }
        @Override public Task<Object> getLocation() { return notConnected(); }
        @Override public Task<Object> getWeather() { return notConnected(); }
        @Override public Task<Object> getTimeIntervals() { return notConnected(); }
        @Override public Task<Object> getPlaces() { return notConnected(); }
    };
}
