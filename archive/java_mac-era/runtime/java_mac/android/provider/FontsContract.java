package android.provider;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;

/**
 * The platform downloadable-fonts client.
 *
 * A "font provider" is an ordinary ContentProvider installed by ANOTHER app
 * (Google Play Services ships the common one). Mac.apk runs exactly one app and
 * installs no font provider, so there is never a provider package to resolve.
 *
 * That is why fetchFonts throws PackageManager.NameNotFoundException and
 * requestFonts reports FAIL_REASON_PROVIDER_NOT_FOUND: both are AOSP's OWN
 * documented outcomes for "the provider package is not installed", and both are
 * the exact paths androidx.core.provider.FontsContractCompat already handles by
 * falling back to a bundled font. Returning an empty successful result instead
 * would tell the app the download succeeded and yield no glyphs.
 *
 * @deprecated AOSP deprecated this in API 29 in favour of androidx.core.provider.
 *
 * Source: frameworks/base/core/java/android/provider/FontsContract.java
 */
@Deprecated
public class FontsContract {

    private FontsContract() { }

    /** Columns a font provider's cursor exposes. */
    @Deprecated
    public static final class Columns implements BaseColumns {
        private Columns() { }
        public static final String FILE_ID = "file_id";
        public static final String TTC_INDEX = "font_ttc_index";
        public static final String VARIATION_SETTINGS = "font_variation_settings";
        public static final String WEIGHT = "font_weight";
        public static final String ITALIC = "font_italic";
        public static final String RESULT_CODE = "result_code";

        public static final int RESULT_CODE_OK = 0;
        public static final int RESULT_CODE_FONT_NOT_FOUND = 1;
        public static final int RESULT_CODE_FONT_UNAVAILABLE = 2;
        public static final int RESULT_CODE_MALFORMED_QUERY = 3;
    }

    @Deprecated
    public static class FontInfo {
        private final Uri mUri;
        private final int mTtcIndex;
        private final android.graphics.fonts.FontVariationAxis[] mAxes;
        private final int mWeight;
        private final boolean mItalic;
        private final int mResultCode;

        /** @hide */
        public FontInfo(Uri uri, int ttcIndex, android.graphics.fonts.FontVariationAxis[] axes,
                int weight, boolean italic, int resultCode) {
            mUri = uri;
            mTtcIndex = ttcIndex;
            mAxes = axes;
            mWeight = weight;
            mItalic = italic;
            mResultCode = resultCode;
        }

        public Uri getUri() { return mUri; }
        public int getTtcIndex() { return mTtcIndex; }
        public android.graphics.fonts.FontVariationAxis[] getAxes() { return mAxes; }
        public int getWeight() { return mWeight; }
        public boolean isItalic() { return mItalic; }
        public int getResultCode() { return mResultCode; }
    }

    @Deprecated
    public static class FontFamilyResult {
        public static final int STATUS_OK = 0;
        public static final int STATUS_WRONG_CERTIFICATES = 1;
        public static final int STATUS_UNEXPECTED_DATA_PROVIDED = 2;
        public static final int STATUS_REJECTED = 3;

        private final int mStatusCode;
        private final FontInfo[] mFonts;

        /** @hide */
        public FontFamilyResult(int statusCode, FontInfo[] fonts) {
            mStatusCode = statusCode;
            mFonts = fonts;
        }

        public int getStatusCode() { return mStatusCode; }
        public FontInfo[] getFonts() { return mFonts; }
    }

    @Deprecated
    public static class FontRequestCallback {
        public static final int FAIL_REASON_PROVIDER_NOT_FOUND = -1;
        public static final int FAIL_REASON_WRONG_CERTIFICATES = -2;
        public static final int FAIL_REASON_FONT_LOAD_ERROR = -3;
        public static final int FAIL_REASON_FONT_NOT_FOUND = Columns.RESULT_CODE_FONT_NOT_FOUND;
        public static final int FAIL_REASON_FONT_UNAVAILABLE = Columns.RESULT_CODE_FONT_UNAVAILABLE;
        public static final int FAIL_REASON_MALFORMED_QUERY = Columns.RESULT_CODE_MALFORMED_QUERY;

        public FontRequestCallback() { }

        public void onTypefaceRetrieved(Typeface typeface) { }

        public void onTypefaceRequestFailed(int reason) { }
    }

    /**
     * AOSP resolves the provider package, verifies its signing certificates and
     * queries it. No font provider is installed here, so the resolution step is
     * where it stops -- and NameNotFoundException is exactly what AOSP throws
     * then.
     */
    public static FontFamilyResult fetchFonts(Context context, CancellationSignal cancellationSignal,
            FontRequest request) throws PackageManager.NameNotFoundException {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(request, "request");
        throw new PackageManager.NameNotFoundException(
                "No package found for authority: " + request.getProviderAuthority());
    }

    /**
     * AOSP builds a Typeface from descriptors the provider returned. There can
     * be no such descriptors here (see fetchFonts), so this returns null --
     * AOSP's own answer when the fonts cannot be loaded.
     */
    public static Typeface buildTypeface(Context context, CancellationSignal cancellationSignal,
            FontInfo[] fonts) {
        return null;
    }

    /** Reports FAIL_REASON_PROVIDER_NOT_FOUND on the caller's Handler. */
    public static void requestFonts(Context context, FontRequest request, Handler handler,
            CancellationSignal cancellationSignal, FontRequestCallback callback) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(request, "request");
        java.util.Objects.requireNonNull(handler, "handler");
        java.util.Objects.requireNonNull(callback, "callback");
        handler.post(() -> {
            try {
                callback.onTypefaceRequestFailed(
                        FontRequestCallback.FAIL_REASON_PROVIDER_NOT_FOUND);
            } catch (Throwable ignored) { }
        });
    }
}
