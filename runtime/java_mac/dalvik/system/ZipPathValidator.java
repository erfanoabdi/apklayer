// dalvik.system.ZipPathValidator -- API 33+ guard that lets the platform reject
// zip entries with dangerous (path-traversal) names while a ZipFile/ZipInputStream
// is being read. Apps and libraries that target T+ occasionally install a custom
// Callback (or clear the default one) to control this behavior. A missing class
// makes `ZipPathValidator.setCallback(...)` / `clearCallback()` fail to resolve.
//
// AOSP (libcore/ojluni/.../dalvik/system/ZipPathValidator.java) surface:
//   public static void setCallback(Callback callback)
//   public static void clearCallback()
//   public static boolean isClear()                 // some builds
//   public interface Callback {
//       default void onZipEntryAccess(String path) throws ZipException {}
//   }
//
// Mac.apk uses the JDK's own java.util.zip stack, which already validates entry
// names; we do not wire this callback into that stack, so the validator never
// fires. We retain the installed callback so getter-style probes see what was
// set, but no zip read invokes it (an honest no-op: the guard exists but is not
// hooked, matching "the callback is registered but the platform path-checks are
// handled by the JDK").
package dalvik.system;

import java.util.zip.ZipException;

public final class ZipPathValidator {

    /** AOSP-shaped callback interface. onZipEntryAccess is invoked by the
     *  platform for each entry name; a custom impl may throw ZipException to
     *  reject it. The default does nothing (accept). */
    public interface Callback {
        default void onZipEntryAccess(String path) throws ZipException {
            // default: accept all entry names
        }
    }

    // The AOSP default callback rejects entries with ".." or leading "/".
    private static volatile Callback callback = new Callback() {};

    private ZipPathValidator() {}

    /** AOSP: install a custom validation callback. */
    public static void setCallback(Callback callback) {
        if (callback == null) {
            throw new NullPointerException("callback == null");
        }
        ZipPathValidator.callback = callback;
    }

    /** AOSP: restore the default (clear any custom callback). */
    public static void clearCallback() {
        callback = new Callback() {};
    }

    /** AOSP (some builds): whether the current callback is the cleared default.
     *  We track this by identity of the default-class instance; since we always
     *  install a fresh default on clear, report true only when no custom
     *  callback was set. */
    public static boolean isClear() {
        return callback != null
                && callback.getClass().getEnclosingClass() == ZipPathValidator.class;
    }
}
