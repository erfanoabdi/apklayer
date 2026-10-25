package android.view.inputmethod;
// android.view.inputmethod.InputMethodSubtype: one locale/mode of an IME.
// Mac.apk exposes no IMEs (text arrives from the host keyboard through the Cocoa
// bridge), so InputMethodManager never hands one out -- but the class is a plain
// immutable value object on AOSP too, and an app CAN build one itself with the
// deprecated constructors or the Builder, so every accessor answers real state
// rather than a constant.
//
// Source: frameworks/base core/java/android/view/inputmethod/InputMethodSubtype.java
// (android16-release).
public final class InputMethodSubtype implements android.os.Parcelable {
    private final int subtypeId;
    private final String locale;
    private final String mode;
    private final int mSubtypeNameResId;
    private final int mSubtypeIconResId;
    private final String mSubtypeLanguageTag;
    private final String mSubtypeExtraValue;
    private final boolean mIsAuxiliary;
    private final boolean mOverridesImplicitlyEnabledSubtype;
    private final boolean mIsAsciiCapable;
    private final CharSequence mSubtypeNameOverride;
    private final int mLayoutLabelResource;
    private final CharSequence mLayoutLabelNonLocalized;
    private final android.icu.util.ULocale mPhysicalKeyboardHintLanguageTag;
    private final String mPhysicalKeyboardHintLayoutType;

    /** Not AOSP API; kept because this tree already shipped it. */
    public InputMethodSubtype() { this(0, "", ""); }
    /** Not AOSP API; kept because this tree already shipped it. */
    public InputMethodSubtype(int subtypeId, String locale, String mode) {
        this(new InputMethodSubtypeBuilder()
                .setSubtypeId(subtypeId).setSubtypeLocale(locale).setSubtypeMode(mode));
    }

    /** AOSP InputMethodSubtype(int nameId, int iconId, String locale, String
     *  mode, String extraValue, boolean isAuxiliary, boolean
     *  overridesImplicitlyEnabledSubtype), deprecated at API 19 in favour of the
     *  Builder. */
    @Deprecated
    public InputMethodSubtype(int nameId, int iconId, String locale, String mode,
            String extraValue, boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype) {
        this(nameId, iconId, locale, mode, extraValue, isAuxiliary,
                overridesImplicitlyEnabledSubtype, 0);
    }

    /** AOSP InputMethodSubtype(..., int id): the same, with an explicit subtype
     *  id. AOSP derives the id from a hash of the other fields when 0 is passed,
     *  and that derived value is also what hashCode() returns -- reproduced, so
     *  two subtypes built from equal arguments compare and hash equal. */
    @Deprecated
    public InputMethodSubtype(int nameId, int iconId, String locale, String mode,
            String extraValue, boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype,
            int id) {
        this(new InputMethodSubtypeBuilder()
                .setSubtypeNameResId(nameId)
                .setSubtypeIconResId(iconId)
                .setSubtypeLocale(locale)
                .setSubtypeMode(mode)
                .setSubtypeExtraValue(extraValue)
                .setIsAuxiliary(isAuxiliary)
                .setOverridesImplicitlyEnabledSubtype(overridesImplicitlyEnabledSubtype)
                .setSubtypeId(id));
    }

    private InputMethodSubtype(InputMethodSubtypeBuilder b) {
        locale = b.mSubtypeLocale;
        mode = b.mSubtypeMode;
        mSubtypeNameResId = b.mSubtypeNameResId;
        mSubtypeIconResId = b.mSubtypeIconResId;
        mSubtypeLanguageTag = b.mSubtypeLanguageTag;
        mSubtypeExtraValue = b.mSubtypeExtraValue;
        mIsAuxiliary = b.mIsAuxiliary;
        mOverridesImplicitlyEnabledSubtype = b.mOverridesImplicitlyEnabledSubtype;
        mIsAsciiCapable = b.mIsAsciiCapable;
        mSubtypeNameOverride = b.mSubtypeNameOverride;
        mLayoutLabelResource = b.mLayoutLabelResource;
        mLayoutLabelNonLocalized = b.mLayoutLabelNonLocalized;
        mPhysicalKeyboardHintLanguageTag = b.mPhysicalKeyboardHintLanguageTag;
        mPhysicalKeyboardHintLayoutType = b.mPhysicalKeyboardHintLayoutType;
        // AOSP: an explicit id wins; otherwise the id IS the field hash.
        subtypeId = b.mSubtypeId != 0 ? b.mSubtypeId : hashCodeInternal();
    }

    private int hashCodeInternal() {
        return java.util.Arrays.hashCode(new Object[] {
                mSubtypeNameResId, mSubtypeIconResId, locale, mode, mSubtypeExtraValue,
                mIsAuxiliary, mOverridesImplicitlyEnabledSubtype, mIsAsciiCapable,
                mSubtypeLanguageTag });
    }

    /** AOSP getLocale(), deprecated at API 24 in favour of getLanguageTag(). */
    @Deprecated
    public String getLocale()    { return locale; }
    public String getMode()      { return mode; }
    /** AOSP getNameResId(): the string resource for this subtype's display name. */
    public int getNameResId()    { return mSubtypeNameResId; }
    /** AOSP getIconResId(): the drawable resource for this subtype's icon. */
    public int getIconResId()    { return mSubtypeIconResId; }
    /** AOSP getNameOverride() (API 34): a literal name that wins over
     *  getNameResId(). @NonNull -- empty when none was set. */
    public CharSequence getNameOverride() {
        return mSubtypeNameOverride != null ? mSubtypeNameOverride : "";
    }
    /** AOSP isAuxiliary(): an auxiliary subtype is skipped by the normal
     *  IME-switching cycle. */
    public boolean isAuxiliary() { return mIsAuxiliary; }
    /** AOSP overridesImplicitlyEnabledSubtype(). */
    public boolean overridesImplicitlyEnabledSubtype() {
        return mOverridesImplicitlyEnabledSubtype;
    }
    /** AOSP isAsciiCapable(). */
    public boolean isAsciiCapable() { return mIsAsciiCapable; }

    /** AOSP getLayoutLabelResource() / getLayoutLabelNonLocalized() (API 36):
     *  the physical-keyboard layout label. @NonNull; empty when unset. */
    public int getLayoutLabelResource() { return mLayoutLabelResource; }
    public CharSequence getLayoutLabelNonLocalized() {
        return mLayoutLabelNonLocalized != null ? mLayoutLabelNonLocalized : "";
    }
    /** AOSP getLayoutDisplayName(Context, ApplicationInfo) (API 36): the
     *  non-localized label if there is one, else the resource resolved against
     *  the IME's own package. */
    public CharSequence getLayoutDisplayName(android.content.Context context,
            android.content.pm.ApplicationInfo appInfo) {
        if (mLayoutLabelNonLocalized != null && mLayoutLabelNonLocalized.length() > 0) {
            return mLayoutLabelNonLocalized;
        }
        return resolveResource(context, appInfo, mLayoutLabelResource);
    }

    /** AOSP getPhysicalKeyboardHintLanguageTag() /
     *  getPhysicalKeyboardHintLayoutType() (API 34). */
    public android.icu.util.ULocale getPhysicalKeyboardHintLanguageTag() {
        return mPhysicalKeyboardHintLanguageTag;
    }
    public String getPhysicalKeyboardHintLayoutType() {
        return mPhysicalKeyboardHintLayoutType != null ? mPhysicalKeyboardHintLayoutType : "";
    }

    /** AOSP getDisplayName(Context, String packageName, ApplicationInfo): the
     *  user-visible name, which is the override if set, else the name resource
     *  resolved against the IME's package, else the locale's display name --
     *  AOSP's own fallback chain. */
    public CharSequence getDisplayName(android.content.Context context, String packageName,
            android.content.pm.ApplicationInfo appInfo) {
        if (mSubtypeNameOverride != null && mSubtypeNameOverride.length() > 0) {
            return mSubtypeNameOverride;
        }
        CharSequence name = resolveResource(context, appInfo, mSubtypeNameResId);
        if (name != null && name.length() > 0) return name;
        final String tag = getLanguageTag();
        if (!tag.isEmpty()) {
            return java.util.Locale.forLanguageTag(tag).getDisplayName();
        }
        return locale != null ? locale : "";
    }

    private static CharSequence resolveResource(android.content.Context context,
            android.content.pm.ApplicationInfo appInfo, int resId) {
        if (resId == 0 || context == null) return "";
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            if (pm != null && appInfo != null) {
                CharSequence cs = pm.getText(appInfo.packageName, resId, appInfo);
                if (cs != null) return cs;
            }
            return context.getText(resId);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** AOSP getLanguageTag() (API 24). @NonNull; empty when unset. */
    public String getLanguageTag() {
        return mSubtypeLanguageTag != null ? mSubtypeLanguageTag : "";
    }
    /** AOSP getExtraValue(): the raw comma-separated key(=value) list. */
    public String getExtraValue() {
        return mSubtypeExtraValue != null ? mSubtypeExtraValue : "";
    }
    /** AOSP containsExtraValueKey(String): AOSP parses the extra value into a
     *  HashMap keyed on the part before '=' (or the whole token when there is
     *  no '='). Same parse here. */
    public boolean containsExtraValueKey(String key) {
        return getExtraValueHashMap().containsKey(key);
    }
    /** AOSP getExtraValueOf(String): the value for a key, or null. */
    public String getExtraValueOf(String key) {
        return getExtraValueHashMap().get(key);
    }
    private java.util.HashMap<String, String> getExtraValueHashMap() {
        java.util.HashMap<String, String> map = new java.util.HashMap<String, String>();
        final String extra = getExtraValue();
        if (extra.isEmpty()) return map;
        for (String pair : extra.split(",")) {
            final int eq = pair.indexOf('=');
            if (eq < 0) map.put(pair, null);
            else map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    @Override public int hashCode() { return subtypeId; }
    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof InputMethodSubtype)) return false;
        InputMethodSubtype s = (InputMethodSubtype) o;
        return s.subtypeId == subtypeId
                && java.util.Objects.equals(s.locale, locale)
                && java.util.Objects.equals(s.mode, mode)
                && java.util.Objects.equals(s.mSubtypeExtraValue, mSubtypeExtraValue)
                && java.util.Objects.equals(s.mSubtypeLanguageTag, mSubtypeLanguageTag)
                && s.mSubtypeNameResId == mSubtypeNameResId
                && s.mSubtypeIconResId == mSubtypeIconResId
                && s.mIsAuxiliary == mIsAuxiliary
                && s.mOverridesImplicitlyEnabledSubtype == mOverridesImplicitlyEnabledSubtype
                && s.mIsAsciiCapable == mIsAsciiCapable;
    }

    @Override public int describeContents() { return 0; }

    // AOSP's writeToParcel order, extended to the fields this class now carries.
    // The first three keep the shape this tree already shipped so a parcel
    // written before this change still reads back its locale / mode / id.
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(locale);
        dest.writeString(mode);
        dest.writeInt(subtypeId);
        dest.writeInt(mSubtypeNameResId);
        dest.writeInt(mSubtypeIconResId);
        dest.writeString(mSubtypeLanguageTag);
        dest.writeString(mSubtypeExtraValue);
        dest.writeInt(mIsAuxiliary ? 1 : 0);
        dest.writeInt(mOverridesImplicitlyEnabledSubtype ? 1 : 0);
        dest.writeInt(mIsAsciiCapable ? 1 : 0);
    }

    public static final Creator<InputMethodSubtype> CREATOR = new Creator<InputMethodSubtype>() {
        @Override public InputMethodSubtype createFromParcel(android.os.Parcel s) {
            // Read every field BEFORE constructing: the builder takes them in a
            // different order than the wire does, and inlining the reads as
            // arguments would evaluate them in argument order and swap two.
            final String l = s.readString();
            final String m = s.readString();
            final int id = s.readInt();
            final int nameId = s.readInt();
            final int iconId = s.readInt();
            final String tag = s.readString();
            final String extra = s.readString();
            final boolean aux = s.readInt() != 0;
            final boolean overrides = s.readInt() != 0;
            final boolean ascii = s.readInt() != 0;
            return new InputMethodSubtypeBuilder()
                    .setSubtypeLocale(l).setSubtypeMode(m).setSubtypeId(id)
                    .setSubtypeNameResId(nameId).setSubtypeIconResId(iconId)
                    .setLanguageTag(tag).setSubtypeExtraValue(extra)
                    .setIsAuxiliary(aux).setOverridesImplicitlyEnabledSubtype(overrides)
                    .setIsAsciiCapable(ascii)
                    .build();
        }
        @Override public InputMethodSubtype[] newArray(int size) { return new InputMethodSubtype[size]; }
    };

    /** AOSP InputMethodSubtype.InputMethodSubtypeBuilder (API 19). */
    public static class InputMethodSubtypeBuilder {
        private int mSubtypeId;
        private String mSubtypeLocale = "";
        private String mSubtypeMode = "";
        private int mSubtypeNameResId;
        private int mSubtypeIconResId;
        private String mSubtypeLanguageTag = "";
        private String mSubtypeExtraValue = "";
        private boolean mIsAuxiliary;
        private boolean mOverridesImplicitlyEnabledSubtype;
        private boolean mIsAsciiCapable;
        private CharSequence mSubtypeNameOverride = "";
        private int mLayoutLabelResource;
        private CharSequence mLayoutLabelNonLocalized = "";
        private android.icu.util.ULocale mPhysicalKeyboardHintLanguageTag;
        private String mPhysicalKeyboardHintLayoutType = "";

        public InputMethodSubtypeBuilder() {}

        public InputMethodSubtypeBuilder setIsAuxiliary(boolean isAuxiliary) {
            mIsAuxiliary = isAuxiliary; return this;
        }
        public InputMethodSubtypeBuilder setOverridesImplicitlyEnabledSubtype(boolean v) {
            mOverridesImplicitlyEnabledSubtype = v; return this;
        }
        public InputMethodSubtypeBuilder setIsAsciiCapable(boolean isAsciiCapable) {
            mIsAsciiCapable = isAsciiCapable; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeIconResId(int subtypeIconResId) {
            mSubtypeIconResId = subtypeIconResId; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeNameResId(int subtypeNameResId) {
            mSubtypeNameResId = subtypeNameResId; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeNameOverride(CharSequence nameOverride) {
            mSubtypeNameOverride = nameOverride; return this;
        }
        public InputMethodSubtypeBuilder setLayoutLabelResource(int layoutLabelResource) {
            mLayoutLabelResource = layoutLabelResource; return this;
        }
        public InputMethodSubtypeBuilder setLayoutLabelNonLocalized(CharSequence label) {
            mLayoutLabelNonLocalized = label; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeId(int subtypeId) {
            mSubtypeId = subtypeId; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeLocale(String subtypeLocale) {
            mSubtypeLocale = subtypeLocale == null ? "" : subtypeLocale; return this;
        }
        public InputMethodSubtypeBuilder setLanguageTag(String languageTag) {
            mSubtypeLanguageTag = languageTag == null ? "" : languageTag; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeMode(String subtypeMode) {
            mSubtypeMode = subtypeMode == null ? "" : subtypeMode; return this;
        }
        public InputMethodSubtypeBuilder setSubtypeExtraValue(String subtypeExtraValue) {
            mSubtypeExtraValue = subtypeExtraValue == null ? "" : subtypeExtraValue; return this;
        }
        public InputMethodSubtypeBuilder setPhysicalKeyboardHint(
                android.icu.util.ULocale languageTag, String layoutType) {
            mPhysicalKeyboardHintLanguageTag = languageTag;
            mPhysicalKeyboardHintLayoutType =
                    java.util.Objects.requireNonNull(layoutType, "layoutType cannot be null");
            return this;
        }
        public InputMethodSubtype build() { return new InputMethodSubtype(this); }
    }
}
