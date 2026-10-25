package com.google.android.play.core.tasks;

// com.google.android.play.core.tasks.OnFailureListener -- Play Core's own
// failure callback (receives the Exception the task failed with).
public interface OnFailureListener {
    void onFailure(Exception e);
}
