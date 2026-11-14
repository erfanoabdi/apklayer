package org.robolectric.android.internal;

import android.app.Application;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

/**
 * apklayer's own: on Android ActivityThread.getApplication() / currentApplication() is null until the app's Application is made and attached
 * (handleBindApplication sets mInitialApplication after LoadedApk.makeApplication: the class's static initializer, its constructor and
 * attachBaseContext all see null). Robolectric shows a stand-in Application of package "android" (on the system context) and then the app's own
 * before its attach; Tencent ACE's JNI_OnLoad (PGR) built its first state from that stand-in. patches/robolectric/0029 sets {@link #binding}
 * over that window and ShadowActivityThread.getApplication answers null in it. apklayer's host code that needs the app in that window
 * (the hub attach from attachBaseContext, the shared system-service state) asks {@link #hostApplication} instead.
 */
public final class ApklayerBinding {
    /** True while the app's Application is being made and attached (AndroidTestEnvironment.installAndCreateApplication). */
    public static volatile boolean binding;

    private ApklayerBinding() {}

    /** What ActivityThread.getApplication() answered before 0029, in the binding window too: mInitialApplication, else Robolectric's application. */
    public static Application hostApplication() {
        Object thread = RuntimeEnvironment.getActivityThread();
        Application initial = thread == null ? null : ReflectionHelpers.getField(thread, "mInitialApplication");
        return initial != null ? initial : RuntimeEnvironment.getApplication();
    }
}
