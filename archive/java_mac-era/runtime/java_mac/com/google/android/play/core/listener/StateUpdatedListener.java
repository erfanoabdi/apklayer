package com.google.android.play.core.listener;

// com.google.android.play.core.listener.StateUpdatedListener<StateT> -- the
// generic state-update callback base used by both InstallStateUpdatedListener
// (in-app updates) and SplitInstallStateUpdatedListener (dynamic features).
// Mirrors the published Play Core base interface.
public interface StateUpdatedListener<StateT> {
    void onStateUpdate(StateT state);
}
