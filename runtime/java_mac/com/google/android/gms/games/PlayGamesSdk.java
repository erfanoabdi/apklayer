package com.google.android.gms.games;

import android.content.Context;

// com.google.android.gms.games.PlayGamesSdk -- the v2 Play Games initializer.
// initialize() is a no-op registration call apps make in Application.onCreate.
// Mac.apk has no Play Games backend, so nothing is wired, but the call must
// succeed (it never returns a value). Ref: developers.google.com/android/reference
public final class PlayGamesSdk {
    private PlayGamesSdk() {}

    public static void initialize(Context context) {
        // No Play Games backend on a de-Googled device; nothing to initialize.
    }
}
