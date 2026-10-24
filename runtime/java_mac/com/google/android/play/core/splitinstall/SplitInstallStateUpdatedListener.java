package com.google.android.play.core.splitinstall;

import com.google.android.play.core.listener.StateUpdatedListener;

// com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener --
// the listener apps register on SplitInstallManager to observe module-install
// progress. Extends the generic StateUpdatedListener<SplitInstallSessionState>,
// matching the published Play Core type hierarchy. We never start a real
// install, so no state is ever delivered.
public interface SplitInstallStateUpdatedListener
        extends StateUpdatedListener<SplitInstallSessionState> {
}
