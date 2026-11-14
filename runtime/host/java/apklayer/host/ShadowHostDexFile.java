package apklayer.host;

import dalvik.system.DexFile;
import java.lang.reflect.Constructor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDexFile;

/**
 * dalvik.system.DexFile's optimization queries as ART answers them for an installed, dexopted app (art/runtime/native/dalvik_system_DexFile.cc,
 * art/libartbase/base/compiler_filter.cc). Robolectric's ShadowDexFile does not call through: getDexFileOptimizationInfo returned null and
 * the same for VMRuntime.getBaseApkOptimizationInfo, a native here: Play Store's background executor died on
 * {@code ApplicationRuntime.getBaseApkOptimizationInfo().isOptimized()} (a NullPointerException ended the process).
 * Classes here never come from an oat file; the answer is the one a device gives after install-time dexopt.
 */
@Implements(value = DexFile.class, isInAndroidSdk = false, callThroughByDefault = false)
public class ShadowHostDexFile extends ShadowDexFile {
    static final String FILTER = "speed-profile", REASON = "install";

    @Implementation protected static DexFile.OptimizationInfo getDexFileOptimizationInfo(String fileName, String instructionSet) { return info(); }

    /** The status of an installed app's base.apk (also VMRuntime.getBaseApkOptimizationInfo, behind the SDK's ApplicationRuntime). */
    static DexFile.OptimizationInfo info() {
        try {
            Constructor<DexFile.OptimizationInfo> c = DexFile.OptimizationInfo.class.getDeclaredConstructor(String.class, String.class);
            c.setAccessible(true);
            return c.newInstance(FILTER, REASON);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    @Implementation protected static String[] getDexFileOptimizationStatus(String fileName, String instructionSet) { return new String[] {FILTER, REASON}; }

    // compiler_filter.cc: assume-verified < extract < verify < space-profile < space < speed-profile < speed < everything-profile < everything
    private static int rank(String filter) {
        return filter == null ? -1 : switch (filter) {
            case "assume-verified" -> 0; case "extract" -> 1; case "verify" -> 2; case "space-profile" -> 3; case "space" -> 4;
            case "speed-profile" -> 5; case "speed" -> 6; case "everything-profile" -> 7; case "everything" -> 8; default -> -1;
        };
    }

    @Implementation protected static boolean isValidCompilerFilter(String filter) { return rank(filter) >= 0; }
    @Implementation protected static boolean isVerifiedCompilerFilter(String filter) { return rank(filter) >= 2; }
    @Implementation protected static boolean isOptimizedCompilerFilter(String filter) { return rank(filter) >= 3; }
    @Implementation protected static boolean isProfileGuidedCompilerFilter(String filter) { int r = rank(filter); return r == 3 || r == 5 || r == 7; }

    @Implementation protected static String getNonProfileGuidedCompilerFilter(String filter) {
        return isProfileGuidedCompilerFilter(filter) ? filter.substring(0, filter.length() - "-profile".length()) : filter;
    }

    @Implementation protected static String getSafeModeCompilerFilter(String filter) { return rank(filter) > 2 ? "verify" : filter; }

    @Implementation protected static boolean isReadOnlyJavaDclEnforced() { return false; }
}
