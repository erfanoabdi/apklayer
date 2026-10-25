package com.google.android.play.core.install.model;

// com.google.android.play.core.install.model.AppUpdateType -- the update flavours
// an app can request (FLEXIBLE = background download + app-controlled restart;
// IMMEDIATE = full-screen blocking flow). Values match the published Play Core
// @IntDef. We never allow either type (no Play Store), so AppUpdateInfo
// .isUpdateTypeAllowed returns false for both.
public final class AppUpdateType {
    private AppUpdateType() {}

    public static final int FLEXIBLE = 0;
    public static final int IMMEDIATE = 1;
}
