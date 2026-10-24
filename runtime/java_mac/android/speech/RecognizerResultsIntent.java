// android.speech.RecognizerResultsIntent -- the constants a voice-search
// activity uses to hand structured web results back. AOSP:
// frameworks/base/core/java/android/speech/RecognizerResultsIntent.java
package android.speech;

public class RecognizerResultsIntent {
    /** AOSP has the implicit public default constructor. */
    public RecognizerResultsIntent() {}

    public static final String ACTION_VOICE_SEARCH_RESULTS =
            "android.speech.action.VOICE_SEARCH_RESULTS";

    public static final String EXTRA_VOICE_SEARCH_RESULT_STRINGS =
            "android.speech.extras.VOICE_SEARCH_RESULT_STRINGS";
    public static final String EXTRA_VOICE_SEARCH_RESULT_URLS =
            "android.speech.extras.VOICE_SEARCH_RESULT_URLS";
    public static final String EXTRA_VOICE_SEARCH_RESULT_HTML =
            "android.speech.extras.VOICE_SEARCH_RESULT_HTML";
    public static final String EXTRA_VOICE_SEARCH_RESULT_HTML_BASE_URLS =
            "android.speech.extras.VOICE_SEARCH_RESULT_HTML_BASE_URLS";
    // NOTE the doubled "EXTRA_" in the literal -- that is AOSP's, not a typo
    // here, and the wire value is what matters.
    public static final String EXTRA_VOICE_SEARCH_RESULT_HTTP_HEADERS =
            "android.speech.extras.EXTRA_VOICE_SEARCH_RESULT_HTTP_HEADERS";

    /** The scheme of a URL whose content is carried inline in the HTML extra. */
    public static final String URI_SCHEME_INLINE = "inline";
}
