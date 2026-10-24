// android.util.Config -- the API-1 build-flavour flags. Deprecated since
// API 3 and hardwired in AOSP itself (they are compile-time constants there
// too), but a surprising number of ancient libraries still reference them.
// AOSP: frameworks/base/core/java/android/util/Config.java
//
// NOTE: javac inlines these into the caller, so a class compiled against the
// real SDK never reads them back. They exist here for reflection and for code
// our DEX converter emits from a `sget-boolean`.
package android.util;

/** @deprecated This class is not useful, it just returns the same value for all constants. */
@Deprecated
public final class Config {
    private Config() {}

    /** @deprecated Always false. */
    @Deprecated public static final boolean DEBUG = false;
    /** @deprecated Always true. */
    @Deprecated public static final boolean RELEASE = true;
    /** @deprecated Always false. */
    @Deprecated public static final boolean PROFILE = false;
    /** @deprecated Always false. */
    @Deprecated public static final boolean LOGV = false;
    /** @deprecated Always true. */
    @Deprecated public static final boolean LOGD = true;
}
