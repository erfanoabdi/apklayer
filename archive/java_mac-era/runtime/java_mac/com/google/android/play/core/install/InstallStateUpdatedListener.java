package com.google.android.play.core.install;

import com.google.android.play.core.listener.StateUpdatedListener;

// com.google.android.play.core.install.InstallStateUpdatedListener -- the
// listener apps register on AppUpdateManager to observe flexible-update progress.
// Extends the generic StateUpdatedListener<InstallState>, matching the published
// Play Core type hierarchy.
public interface InstallStateUpdatedListener extends StateUpdatedListener<InstallState> {
}
