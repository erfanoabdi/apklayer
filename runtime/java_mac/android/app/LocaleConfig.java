// android.app.LocaleConfig -- "The LocaleConfig of an application", i.e. the set
// of locales an app declares it supports via
// android:localeConfig="@xml/locales_config" in its manifest. API 33.
//
// AOSP ref: frameworks/base/core/java/android/app/LocaleConfig.java
// (android16-release). AOSP's Context constructor resolves the app's
// android:localeConfig resource and parses the <locale-config> XML; when the app
// declares none, getStatus() reports STATUS_NOT_SPECIFIED and
// getSupportedLocales() returns null, and when parsing fails it reports
// STATUS_PARSING_FAILED. A per-app language picker (AppCompat 1.6+, and every
// Settings-style in-app picker) reads exactly those two to decide whether to
// offer a language list at all.
//
// Mac.apk does not parse the localeConfig resource, so the honest answer is
// AOSP's "the app declared none": STATUS_NOT_SPECIFIED plus a null locale list.
// That makes a picker fall back to its own hard-coded list -- which is what it
// does on any app without a localeConfig -- instead of showing an empty list,
// which is what an empty-but-non-null LocaleList would produce.
package android.app;

import android.content.Context;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;

public class LocaleConfig implements Parcelable {

    /** AOSP STATUS_SUCCESS: "The LocaleConfig is parsed successfully." */
    public static final int STATUS_SUCCESS = 0;
    /** AOSP STATUS_NOT_SPECIFIED: "No android:localeConfig tag in the
     *  AndroidManifest.xml, or the app has no locale config." */
    public static final int STATUS_NOT_SPECIFIED = 1;
    /** AOSP STATUS_PARSING_FAILED: "The localeConfig tag is present but could
     *  not be parsed." */
    public static final int STATUS_PARSING_FAILED = 2;

    /** AOSP TAG_LOCALE_CONFIG / TAG_LOCALE: the XML element names. */
    public static final String TAG_LOCALE_CONFIG = "locale-config";
    public static final String TAG_LOCALE = "locale";

    private final LocaleList mLocales;
    private final Locale mDefaultLocale;
    private final int mStatus;

    /** AOSP LocaleConfig(Context): reads the app's android:localeConfig
     *  resource. We do not parse it (see the class comment), which is AOSP's
     *  STATUS_NOT_SPECIFIED case. */
    public LocaleConfig(Context context) {
        mLocales = null;
        mDefaultLocale = null;
        mStatus = STATUS_NOT_SPECIFIED;
    }

    /** AOSP LocaleConfig(LocaleList): "Return a LocaleConfig with the given
     *  LocaleList", used by an app that computes its supported locales itself.
     *  This one carries real state and reports STATUS_SUCCESS, as AOSP's does. */
    public LocaleConfig(LocaleList locales) {
        mLocales = locales;
        mDefaultLocale = null;
        mStatus = STATUS_SUCCESS;
    }

    private LocaleConfig(Parcel in) {
        mLocales = null;
        mDefaultLocale = null;
        mStatus = in != null ? in.readInt() : STATUS_NOT_SPECIFIED;
    }

    /** AOSP fromContextIgnoringOverride(Context): the app's MANIFEST-declared
     *  LocaleConfig, deliberately ignoring any override installed with
     *  LocaleManager.setOverrideLocaleConfig. Same manifest answer as the
     *  Context constructor. */
    public static LocaleConfig fromContextIgnoringOverride(Context context) {
        return new LocaleConfig(context);
    }

    /** AOSP getSupportedLocales(): "@return the LocaleList, or null if the
     *  LocaleConfig is not specified or could not be parsed." */
    public LocaleList getSupportedLocales() { return mLocales; }

    /** AOSP getDefaultLocale(): the locale named by android:defaultLocale in the
     *  <locale-config> tag, null when unspecified. */
    public Locale getDefaultLocale() { return mDefaultLocale; }

    /** AOSP getStatus(): STATUS_SUCCESS / STATUS_NOT_SPECIFIED /
     *  STATUS_PARSING_FAILED. */
    public int getStatus() { return mStatus; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeInt(mStatus); }

    public static final Parcelable.Creator<LocaleConfig> CREATOR =
            new Parcelable.Creator<LocaleConfig>() {
        @Override public LocaleConfig createFromParcel(Parcel in) { return new LocaleConfig(in); }
        @Override public LocaleConfig[] newArray(int size) { return new LocaleConfig[size]; }
    };
}
