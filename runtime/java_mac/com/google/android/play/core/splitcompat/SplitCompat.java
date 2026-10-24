package com.google.android.play.core.splitcompat;

import android.content.Context;

// com.google.android.play.core.splitcompat.SplitCompat -- the helper apps call
// (typically in Application.attachBaseContext or an Activity's attachBaseContext)
// to make on-demand-delivered module code/resources loadable on pre-O devices.
//
// On Mac.apk all of the app's code already lives in the single loaded classpath
// (we do not split-deliver modules at runtime), so there is nothing to splice in:
// install() is a successful no-op. This is the important one to ship -- apps that
// call SplitCompat.install(this) in attachBaseContext crash EARLY (before any UI)
// if the class is missing. Returning true (success) lets boot continue.
// Signatures mirror the published Play Core SplitCompat.
public final class SplitCompat {
    private SplitCompat() {}

    public static boolean install(Context context) {
        // Nothing to emulate: the full app classpath is already present.
        return true;
    }

    public static boolean installActivity(Context context) {
        // Activity-scoped variant; same no-op success.
        return true;
    }
}
