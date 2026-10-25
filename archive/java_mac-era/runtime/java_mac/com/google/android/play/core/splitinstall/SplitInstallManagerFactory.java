package com.google.android.play.core.splitinstall;

import android.content.Context;

// com.google.android.play.core.splitinstall.SplitInstallManagerFactory -- the
// static factory apps call to obtain a SplitInstallManager. Mirrors the
// published Play Core factory (a single static create(Context)). Returns our
// clean-unavailable SplitInstallManagerImpl so on-demand modules degrade to
// "API not available" instead of crashing on a missing class.
public final class SplitInstallManagerFactory {
    private SplitInstallManagerFactory() {}

    public static SplitInstallManager create(Context context) {
        return new SplitInstallManagerImpl(context);
    }
}
