package com.google.firebase.remoteconfig;
public interface ConfigUpdateListener {
    void onUpdate(ConfigUpdate configUpdate);
    void onError(FirebaseRemoteConfigException error);
}
