package com.google.android.play.core.appupdate;

import android.content.Context;

// com.google.android.play.core.appupdate.AppUpdateManagerFactory -- the static
// factory apps call to obtain an AppUpdateManager. Mirrors the published Play
// Core factory: a single static create(Context). Returns our clean-unavailable
// AppUpdateManagerImpl so the in-app-update flow degrades to "no update" instead
// of crashing on a missing class.
public final class AppUpdateManagerFactory {
    private AppUpdateManagerFactory() {}

    public static AppUpdateManager create(Context context) {
        return new AppUpdateManagerImpl(context);
    }
}
