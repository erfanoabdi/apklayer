package com.google.android.gms.common.api;

// com.google.android.gms.common.api.Result -- the final result of invoking a
// (legacy, pre-Task) GMS API method. Every Result carries a Status. Apps
// implement ResultCallback<R extends Result> and inspect result.getStatus().
public interface Result {
    Status getStatus();
}
