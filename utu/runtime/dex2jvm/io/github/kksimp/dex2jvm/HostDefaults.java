// HostDefaults -- apklayer's converter defaults. The only file in this package that is ours: everything else is the upstream
// kksimp/dex2jvm source (Apache-2.0, commit in ../../../../.dex2jvm-commit), copied unchanged. Options reads these when no system
// property or environment variable overrides them.
//
//   SYNTHETIC_PREFIX  prefix of the members the converter invents (outlined and split method parts, spill fields).
//   REDIRECT_UNSAFE   an app's sun.misc.Unsafe calls go to apklayer.loader.ApklayerUnsafe (see runtime/host).
//   REDIRECT_EXIT     null: apklayer runs one app per JVM, so System.exit really exits.
//   LIBRARY           "classloader": the class hierarchy comes from the framework classes on the converter's own class path (the
//                     classes the app runs against), never an SDK android.jar or the bundled API index.

package io.github.kksimp.dex2jvm;

public final class HostDefaults {
    private HostDefaults() {}

    public static final String SYNTHETIC_PREFIX = "apklayer";
    public static final String REDIRECT_UNSAFE = "apklayer/loader/ApklayerUnsafe";
    public static final String REDIRECT_EXIT = null;
    public static final String LOG_TAG = "apklayer-dex";
    public static final String LIBRARY = "classloader";
}
