package android.view.inputmethod;
// android.view.inputmethod.InputMethodInfo: descriptor for one installed IME.
// Mac.apk has no installable IMEs (text is entered through the host keyboard),
// so InputMethodManager.getEnabledInputMethodList returns an empty list; this
// class exists so that list's element type resolves and so apps that reflect
// over the type don't hit NoClassDefFoundError.
public final class InputMethodInfo implements android.os.Parcelable {
    private final String id;
    private final String packageName;
    private final String serviceName;
    public InputMethodInfo() { this("", "", ""); }
    public InputMethodInfo(String id, String packageName, String serviceName) {
        this.id = id; this.packageName = packageName; this.serviceName = serviceName;
    }

    /** AOSP InputMethodInfo(String packageName, String className, CharSequence
     *  label, String settingsActivity): the "build a descriptor by hand" form,
     *  used by tests and by an app enumerating its own IME service. AOSP derives
     *  id = packageName + "/" + className. */
    public InputMethodInfo(String packageName, String className, CharSequence label,
            String settingsActivity) {
        this.packageName = packageName;
        this.serviceName = className;
        this.id = new android.content.ComponentName(packageName, className)
                .flattenToShortString();
        this.label = label;
        this.settingsActivity = settingsActivity;
    }

    /** AOSP InputMethodInfo(Context, ResolveInfo): parses the service's
     *  &lt;input-method&gt; meta-data out of the package. Mac.apk installs ONE
     *  app and no IME services, so no ResolveInfo for an input method can be
     *  produced by the PackageManager -- AOSP's own failure mode for a
     *  ResolveInfo whose serviceInfo carries no input-method meta-data is
     *  XmlPullParserException, which is what this raises rather than
     *  fabricating a descriptor for a service that does not exist. */
    public InputMethodInfo(android.content.Context context,
            android.content.pm.ResolveInfo service)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        android.content.pm.ServiceInfo si = service != null ? service.serviceInfo : null;
        if (si == null) {
            throw new org.xmlpull.v1.XmlPullParserException(
                    "Unable to create context for: null service");
        }
        this.packageName = si.packageName;
        this.serviceName = si.name;
        this.id = new android.content.ComponentName(si.packageName, si.name)
                .flattenToShortString();
        this.serviceInfo = si;
        throw new org.xmlpull.v1.XmlPullParserException(
                "No android.view.im meta-data for " + this.id);
    }

    // ---- AOSP API-36 surface completion (android.view lane) ------------------
    // Everything below reads state this descriptor carries. Mac.apk has no
    // installable IMEs (see the class header), so the framework never builds an
    // instance and every answer is the one AOSP gives for an IME that declared
    // no optional meta-data -- which is the common case even on a device.

    public static final String ACTION_STYLUS_HANDWRITING_SETTINGS =
            "android.view.inputmethod.action.STYLUS_HANDWRITING_SETTINGS";
    public static final String ACTION_IME_LANGUAGE_SETTINGS =
            "android.view.inputmethod.action.IME_LANGUAGE_SETTINGS";

    private CharSequence label;
    private String settingsActivity;
    private android.content.pm.ServiceInfo serviceInfo;

    /** AOSP InputMethodInfo.getComponent(): the IME service's ComponentName. */
    public android.content.ComponentName getComponent() {
        return new android.content.ComponentName(packageName, serviceName);
    }

    /** AOSP InputMethodInfo.getServiceInfo(): the parsed ServiceInfo, non-null
     *  only for a descriptor built from a real ResolveInfo. */
    public android.content.pm.ServiceInfo getServiceInfo() { return serviceInfo; }

    /** AOSP InputMethodInfo.getSettingsActivity(): the activity class the IME
     *  declared for its own settings, null when it declared none. */
    public String getSettingsActivity() { return settingsActivity; }

    /** AOSP InputMethodInfo.getIsDefaultResourceId(): the boolean resource that
     *  decides whether this IME is a system default. 0 when unspecified, which
     *  is AOSP's value for an IME with no android:isDefault attribute. */
    public int getIsDefaultResourceId() { return 0; }

    /** AOSP InputMethodInfo.getConfigChanges(): the ActivityInfo.CONFIG_* mask
     *  the IME service handles itself. 0 when it declared none. */
    public int getConfigChanges() { return 0; }

    /** AOSP InputMethodInfo.getSubtypeAt(int): AOSP indexes into its
     *  InputMethodSubtypeArray, which throws IndexOutOfBoundsException when
     *  empty. getSubtypeCount() is 0 here, so every index is out of range and
     *  the throw is the correct, AOSP-identical outcome. */
    public InputMethodSubtype getSubtypeAt(int index) {
        throw new IndexOutOfBoundsException("index=" + index + ", size=0");
    }

    /** AOSP InputMethodInfo.loadIcon(PackageManager): the IME service's icon.
     *  With no ServiceInfo there is nothing to load an icon from, and AOSP
     *  returns the package's default icon in that situation. */
    public android.graphics.drawable.Drawable loadIcon(
            android.content.pm.PackageManager pm) {
        if (serviceInfo != null && pm != null) {
            return serviceInfo.loadIcon(pm);
        }
        return null;
    }

    /** AOSP InputMethodInfo.shouldShowInInputMethodPicker(): whether the IME
     *  opted out of the system picker. Default true, as on AOSP. */
    public boolean shouldShowInInputMethodPicker() { return true; }

    /** AOSP InputMethodInfo.supportsStylusHandwriting() /
     *  supportsConnectionlessStylusHandwriting(): declared by the IME's
     *  meta-data. No IME declares it here (there are none), and a stylus IME
     *  could not be honoured anyway -- macOS delivers no stylus source. */
    public boolean supportsStylusHandwriting() { return false; }
    public boolean supportsConnectionlessStylusHandwriting() { return false; }

    /** AOSP InputMethodInfo.suppressesSpellChecker(): an IME that does its own
     *  spell checking asks the platform to stand down. Default false. */
    public boolean suppressesSpellChecker() { return false; }

    /** AOSP InputMethodInfo.createStylusHandwritingSettingsActivityIntent() /
     *  createImeLanguageSettingsActivityIntent(): AOSP returns null when the IME
     *  declared no such settings activity, which is the case for every
     *  descriptor this tree can produce. */
    public android.content.Intent createStylusHandwritingSettingsActivityIntent() {
        return null;
    }
    public android.content.Intent createImeLanguageSettingsActivityIntent() { return null; }

    /** AOSP InputMethodInfo.dump(Printer, String): the dumpsys dump. */
    public void dump(android.util.Printer pw, String prefix) {
        if (pw == null) return;
        pw.println(prefix + "mId=" + id
                + " mSettingsActivityName=" + settingsActivity
                + " mIsVrOnly=false"
                + " mSupportsSwitchingToNextInputMethod=false");
        pw.println(prefix + "mSubtypeCount=" + getSubtypeCount());
    }
    public String getId()            { return id; }
    public String getPackageName()   { return packageName; }
    public String getServiceName()   { return serviceName; }
    public CharSequence loadLabel(android.content.pm.PackageManager pm) {
        // AOSP loads the service's own label; a hand-built descriptor carries
        // the one it was given.
        return label != null ? label : "Mac.apk";
    }
    public int getSubtypeCount()     { return 0; }
    @Override public String toString() { return "InputMethodInfo{" + id + "}"; }

    // AOSP's InputMethodInfo carries a real ServiceInfo + an
    // InputMethodSubtypeArray plus ~10 boolean/int flags
    // (InputMethodInfo.java L149-215/1025-1039); this class tracks only
    // (id, packageName, serviceName) as plain strings, matching "Mac.apk
    // has no installable IMEs" above -- getEnabledInputMethodList() is
    // always empty, so no live instance of this class is ever actually
    // parceled today. Writing our own 3-field subset rather than AOSP's
    // exact wire shape, per the absent-fields-not-padded rule.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(packageName);
        dest.writeString(serviceName);
    }

    public static final android.os.Parcelable.Creator<InputMethodInfo> CREATOR =
            new android.os.Parcelable.Creator<InputMethodInfo>() {
        @Override public InputMethodInfo createFromParcel(android.os.Parcel in) {
            return new InputMethodInfo(in.readString(), in.readString(), in.readString());
        }
        @Override public InputMethodInfo[] newArray(int size) { return new InputMethodInfo[size]; }
    };
}
