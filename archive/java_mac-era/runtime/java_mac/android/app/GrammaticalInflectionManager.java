package android.app;

/**
 * Per-app grammatical gender preference (API 34). Mac.apk stores the requested
 * gender and reads it back; there is no system-level grammatical-gender store to
 * consult, so the effective value is what the app last set (default UNSPECIFIED),
 * which is what an app reads to localise gendered strings.
 *
 * Source: frameworks/base/core/java/android/app/GrammaticalInflectionManager.java
 */
public class GrammaticalInflectionManager {

    public static final int GRAMMATICAL_GENDER_NOT_SPECIFIED = 0;
    public static final int GRAMMATICAL_GENDER_NEUTRAL = 1;
    public static final int GRAMMATICAL_GENDER_FEMININE = 2;
    public static final int GRAMMATICAL_GENDER_MASCULINE = 3;

    private volatile int mRequestedGender = GRAMMATICAL_GENDER_NOT_SPECIFIED;

    public GrammaticalInflectionManager() { }

    public int getApplicationGrammaticalGender() { return mRequestedGender; }

    public void setRequestedApplicationGrammaticalGender(int grammaticalGender) {
        mRequestedGender = grammaticalGender;
    }

    /** AOSP getSystemGrammaticalGender(): the SYSTEM-level (device-wide, set in
     *  Settings > Languages) grammatical gender, which is a different value from
     *  the per-app one above and requires READ_SYSTEM_GRAMMATICAL_GENDER. macOS
     *  has no such setting, and AOSP's own value when the user has never chosen
     *  one is GRAMMATICAL_GENDER_NOT_SPECIFIED -- the answer that tells an app
     *  to use its ungendered strings. Deliberately NOT the per-app value: an app
     *  reading this expects the device preference, and conflating the two would
     *  make it think the user had made a system-wide choice. */
    public int getSystemGrammaticalGender() { return GRAMMATICAL_GENDER_NOT_SPECIFIED; }
}
