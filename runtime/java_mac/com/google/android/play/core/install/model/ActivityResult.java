package com.google.android.play.core.install.model;

// com.google.android.play.core.install.model.ActivityResult -- the result codes
// delivered to onActivityResult after an in-app-update flow. We never launch a
// flow, so an app that does not get a callback simply never sees these; the
// constants exist so apps that compare resultCode against them compile. Values
// match the published Play Core @IntDef.
public final class ActivityResult {
    private ActivityResult() {}

    // Mirrors android.app.Activity.RESULT_OK / RESULT_CANCELED conventions.
    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_IN_APP_UPDATE_FAILED = 1;
}
