package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowVMRuntime;

/** Android runtime queries and GC hints; object allocation remains the underlying JVM's. */
@Implements(className = "dalvik.system.VMRuntime", isInAndroidSdk = false)
public class ShadowHostVMRuntime extends ShadowVMRuntime {
    // AOSP art/runtime/runtime.h Runtime::GetVersion() and dalvik_system_VMRuntime.cc.
    @Implementation protected String vmVersion() { return "2.1.0"; }
    @Implementation protected String vmLibrary() { return "libart.so"; }

    @Implementation protected String vmInstructionSet() { return getCurrentInstructionSet(); }
    @Implementation protected static String getCurrentInstructionSet() {
        return switch (HostBuild.appAbi()) {
            case "arm64-v8a" -> "arm64";
            case "armeabi-v7a" -> "arm";
            default -> "x86_64";
        };
    }
    @Implementation protected boolean is64Bit() { return !"armeabi-v7a".equals(HostBuild.appAbi()); }

    // ActivityThread.handleBindApplication tells the runtime the app's target SDK (AppEnvironment does here); before that, as AOSP, SDK_VERSION_CUR_DEVELOPMENT.
    private int targetSdkVersion = 10000;
    @Implementation protected int getTargetSdkVersion() { return targetSdkVersion; }
    @Implementation protected void setTargetSdkVersion(int target) { targetSdkVersion = target; }
    // hiddenapi exemptions an app's bypass library sets (GuestHiddenApi)
    @Implementation protected void setHiddenApiExemptions(String[] signaturePrefixes) { GuestHiddenApi.exempt(signaturePrefixes); }

    // AOSP's default; setTargetHeapUtilization retains its Java validation and synchronization.
    // This is a GC hint, permitted to be ignored; it cannot control HotSpot's heap policy.
    private float targetHeapUtilization = 0.6f;
    @Implementation protected float getTargetHeapUtilization() { return targetHeapUtilization; }
    @Implementation protected void nativeSetTargetHeapUtilization(float target) { targetHeapUtilization = target; }
}
