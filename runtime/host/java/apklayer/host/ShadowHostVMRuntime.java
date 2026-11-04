package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowVMRuntime;

/** VMRuntime.vmInstructionSet() names the CPU of the runtime ("arm64", "x86_64"): SoLoader and others pick the native library directory by it. */
@Implements(className = "dalvik.system.VMRuntime", isInAndroidSdk = false)
public class ShadowHostVMRuntime extends ShadowVMRuntime {
    @Implementation protected String vmInstructionSet() { return HostBuild.APP_ABI.equals("arm64-v8a") ? "arm64" : "x86_64"; }
}
