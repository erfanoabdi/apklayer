// android.view.textservice.SpellCheckerSubtype -- one locale a spell checker
// supports. Source: frameworks/base
// core/java/android/view/textservice/SpellCheckerSubtype.java (android16-release).
//
// Mac.apk ships no spell-checker service (see TextServicesManager), so the
// framework never produces one -- but this is a plain immutable value object on
// AOSP too and an app can construct one.
package android.view.textservice;

public final class SpellCheckerSubtype implements android.os.Parcelable {
    private final int mSubtypeNameResId;
    private final String mSubtypeLocale;
    private final String mSubtypeExtraValue;
    private final int mSubtypeHashCode;

    /** AOSP SpellCheckerSubtype(int nameId, String locale, String extraValue),
     *  deprecated at API 24 in favour of the language-tag form. */
    @Deprecated
    public SpellCheckerSubtype(int nameId, String locale, String extraValue) {
        mSubtypeNameResId = nameId;
        mSubtypeLocale = locale != null ? locale : "";
        mSubtypeExtraValue = extraValue != null ? extraValue : "";
        mSubtypeHashCode = java.util.Arrays.hashCode(
                new Object[] { nameId, mSubtypeLocale, mSubtypeExtraValue });
    }

    private SpellCheckerSubtype(android.os.Parcel source) {
        mSubtypeNameResId = source.readInt();
        String s = source.readString();
        mSubtypeLocale = s != null ? s : "";
        s = source.readString();
        mSubtypeExtraValue = s != null ? s : "";
        mSubtypeHashCode = java.util.Arrays.hashCode(
                new Object[] { mSubtypeNameResId, mSubtypeLocale, mSubtypeExtraValue });
    }

    public int getNameResId() { return mSubtypeNameResId; }
    @Deprecated
    public String getLocale() { return mSubtypeLocale; }
    /** AOSP getLanguageTag() (API 24): the BCP-47 tag. AOSP derives it from the
     *  legacy locale string when the subtype was built with the deprecated
     *  constructor, which is the only constructor in the public API. */
    public String getLanguageTag() {
        if (mSubtypeLocale.isEmpty()) return "";
        return java.util.Locale.forLanguageTag(mSubtypeLocale.replace('_', '-'))
                .toLanguageTag();
    }
    public String getExtraValue() { return mSubtypeExtraValue; }

    /** AOSP containsExtraValueKey / getExtraValueOf: the extra value is a
     *  comma-separated key(=value) list, exactly as on InputMethodSubtype. */
    public boolean containsExtraValueKey(String key) {
        return getExtraValueHashMap().containsKey(key);
    }
    public String getExtraValueOf(String key) { return getExtraValueHashMap().get(key); }
    private java.util.HashMap<String, String> getExtraValueHashMap() {
        java.util.HashMap<String, String> map = new java.util.HashMap<String, String>();
        if (mSubtypeExtraValue.isEmpty()) return map;
        for (String pair : mSubtypeExtraValue.split(",")) {
            final int eq = pair.indexOf('=');
            if (eq < 0) map.put(pair, null);
            else map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    /** AOSP getDisplayName(Context, String packageName, ApplicationInfo): the
     *  name resource resolved against the checker's own package, falling back to
     *  the locale's display name -- AOSP's own fallback. */
    public CharSequence getDisplayName(android.content.Context context, String packageName,
            android.content.pm.ApplicationInfo appInfo) {
        final java.util.Locale locale = mSubtypeLocale.isEmpty() ? null
                : java.util.Locale.forLanguageTag(mSubtypeLocale.replace('_', '-'));
        final String localeStr = locale != null ? locale.getDisplayName() : mSubtypeLocale;
        if (mSubtypeNameResId == 0 || context == null) return localeStr;
        try {
            CharSequence cs = context.getPackageManager()
                    .getText(packageName, mSubtypeNameResId, appInfo);
            return cs != null ? String.format(cs.toString(), localeStr) : localeStr;
        } catch (Throwable ignored) {
            return localeStr;
        }
    }

    @Override public int hashCode() { return mSubtypeHashCode; }
    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SpellCheckerSubtype)) return false;
        SpellCheckerSubtype s = (SpellCheckerSubtype) o;
        return s.hashCode() == hashCode()
                && s.getNameResId() == getNameResId()
                && s.getLocale().equals(getLocale())
                && s.getExtraValue().equals(getExtraValue());
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mSubtypeNameResId);
        dest.writeString(mSubtypeLocale);
        dest.writeString(mSubtypeExtraValue);
    }

    public static final android.os.Parcelable.Creator<SpellCheckerSubtype> CREATOR =
            new android.os.Parcelable.Creator<SpellCheckerSubtype>() {
        @Override public SpellCheckerSubtype createFromParcel(android.os.Parcel source) {
            return new SpellCheckerSubtype(source);
        }
        @Override public SpellCheckerSubtype[] newArray(int size) {
            return new SpellCheckerSubtype[size];
        }
    };
}
