package android.graphics.fonts;

import java.util.Collections;
import java.util.Set;

/**
 * AOSP-faithful android.graphics.fonts.SystemFonts (API 29). Real Android
 * returns the set of fonts available on the device. We do not enumerate macOS
 * system fonts into android.graphics.fonts.Font objects (Canvas text uses AWT
 * fonts directly), so getAvailableFonts() returns an empty immutable set, which
 * is a valid (if empty) faithful answer. Mirrors
 * frameworks/base/graphics/java/android/graphics/fonts/SystemFonts.java.
 */
public final class SystemFonts {
    private SystemFonts() {}

    public static Set<Font> getAvailableFonts() {
        // cont.304 PROBE: does pairip JNI-call this to read the system font set? (font fold, pass 16645)
        if (System.getenv("MACAPK_FONTPROBE") != null)
            System.err.println("[fontprobe] SystemFonts.getAvailableFonts() called by "
                + (new Throwable().getStackTrace().length) + "-frame caller");
        return Collections.emptySet();
    }
}
