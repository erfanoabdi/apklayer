package com.google.android.play.core.install.model;

// com.google.android.play.core.install.model.UpdateAvailability -- the constants
// AppUpdateInfo.updateAvailability() can return. On a de-Googled device there is
// no Play Store backend to consult, so we always report UPDATE_NOT_AVAILABLE
// (the honest answer: we cannot determine that an update exists). Values match
// the published Play Core @IntDef.
public final class UpdateAvailability {
    private UpdateAvailability() {}

    public static final int UNKNOWN = 0;
    public static final int UPDATE_NOT_AVAILABLE = 1;
    public static final int UPDATE_AVAILABLE = 2;
    public static final int DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS = 3;
}
