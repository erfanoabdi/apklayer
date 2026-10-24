// com.macapk.loader.FwGapNewClasses -- the BISECT GATE for the platform classes
// added by the 2026-09-26 framework-gap lane (docs/fwgap-new-platform-
// classes_2026-09-26.md), and the single list of what that lane added.
//
// MACAPK_FWGAP_NEWCLASSES (default ON). Set it to 0 and AppClassLoader
// resolves every class named below as if the framework did not ship it: the
// parent (framework) lookup is skipped and the name goes straight to the app's
// own classpath, which returns the app's R8 API stub when it bundles one (13 of
// the 62 corpus apps do, for ServiceStartNotAllowedException) and
// ClassNotFoundException otherwise. For a load that goes THROUGH the app's
// class loader that is the pre-change answer, and debug/fwgap_gate.sh proves it
// by driving a real AppClassLoader: under =0 it must resolve its probe names
// exactly as the pre-change jars (lane classes filtered out) do.
//
// What =0 does NOT undo, stated so a no-difference A/B is not over-read:
//  * Lookups the app loader does not get the last word on. Boot builds
//    AppClassLoader with the system loader as parent and jvm_loader.cpp puts
//    android-framework.jar on -Djava.class.path. A guest's JNI FindClass goes
//    through bionic_shim.cpp's macapk_jni_FindClass_guard, which asks the app
//    loader FIRST (gated) but on a miss falls back to HotSpot's jni_FindClass,
//    and on a NATIVELY ATTACHED thread (no Java frame) that one uses the system
//    loader -- which still finds these classes. Pre-change the same lookup
//    ended in NoClassDefFoundError. Framework classes that link one of these
//    internally resolve through the system loader too.
//  * Build-time effects. The classes are still in the jar, so FRAMEWORK_ID /
//    JAVA_ID moved and the DEX converter's ClassHierarchyOracle sees them when
//    it bakes StackMapTable frames; the one-time re-conversion is not reverted.
//  * Patch P1 (ForegroundServiceStartNotAllowedException re-parented onto
//    ServiceStartNotAllowedException). A superclass is fixed at compile time,
//    so it cannot be switched by an env var; it is the one change of this lane
//    outside the gate.
// So a Unity A/B under =0 is evidence only if the android/Manifest$permission
// lookup is shown to MISS under =0 -- a `[jni-guard] FindClass(
// "android/Manifest$permission") -> NULL, threw` line on stderr -- rather than
// being served by the system loader on a native thread.
//
// THE HOOK. This class does nothing by itself: the switch is the
// `if (FwGapNewClasses.hidden(normalised))` block in
// AppClassLoader.loadClass(String, boolean) (patch P0 in the doc). Without that
// block =0 is INERT, and debug/fwgap_gate.sh FAILS its "=0 resolves ... EXACTLY
// as the pre-change framework" row rather than letting an inert switch pass.
//
// Cost when ON: one non-volatile boolean test per loadClass.
package com.macapk.loader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FwGapNewClasses {
    /** The env var; "0" hides the classes below from app class loading. */
    public static final String ENV = "MACAPK_FWGAP_NEWCLASSES";

    /**
     * Every TOP-LEVEL class this lane added (nested classes are covered by
     * their outer name). debug/fwgap_gate.sh asserts this list is exactly the
     * set of files the lane created, so the bisect gate cannot drift from the
     * change it bisects.
     */
    static final String[] TOP_LEVEL = {
        "android.Manifest",
        "android.R",
        "android.app.AuthenticationRequiredException",
        "android.app.BackgroundServiceStartNotAllowedException",
        "android.app.ForegroundServiceTypeException",
        "android.app.InvalidForegroundServiceTypeException",
        "android.app.MissingForegroundServiceTypeException",
        "android.app.ServiceStartNotAllowedException",
        "android.app.StartForegroundCalledOnStoppedServiceException",
        "android.app.admin.DelegatedAdminReceiver",
        "android.app.admin.DeviceAdminReceiver",
        "android.app.admin.DeviceAdminService",
        "android.media.tv.TvContract",
        "android.opengl.ETC1",
        "android.opengl.Visibility",
        "android.system.Int64Ref",
        "android.system.StructCmsghdr",
        "android.system.StructMsghdr",
        "android.system.StructPollfd",
        "android.system.StructTimespec",
        "android.system.StructTimeval",
        "android.system.SystemCleaner",
        "android.system.VmSocketAddress",
        "android.view.OrientationListener",
        "libcore.util.Objects",
    };

    private static final Set<String> SET =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(TOP_LEVEL)));

    private static final boolean HIDE = "0".equals(System.getenv(ENV));

    private FwGapNewClasses() {}

    /** True when the gate is OFF (=0) and {@code dottedName} is one of ours. */
    public static boolean hidden(String dottedName) {
        if (!HIDE) {
            return false;
        }
        int d = dottedName.indexOf('$');
        return SET.contains(d < 0 ? dottedName : dottedName.substring(0, d));
    }

    /** For the gate script: whether this process runs with the gate off. */
    public static boolean isHiding() {
        return HIDE;
    }

    /** For the gate script: the list, in declaration order. */
    public static String[] topLevel() {
        return TOP_LEVEL.clone();
    }
}
