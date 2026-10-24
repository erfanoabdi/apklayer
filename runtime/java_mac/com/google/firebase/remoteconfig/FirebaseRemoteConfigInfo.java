package com.google.firebase.remoteconfig;
public interface FirebaseRemoteConfigInfo {
    long getFetchTimeMillis();
    int  getLastFetchStatus();
    FirebaseRemoteConfigSettings getConfigSettings();
}
