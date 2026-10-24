// android.content.pm.ComponentInfo -- common base for Activity /
// Service / Receiver / Provider metadata. AOSP carries the calling
// app's ApplicationInfo here so client code can read package-level
// state (uid, dataDir, etc.) off any specific component.
package android.content.pm;

public class ComponentInfo extends PackageItemInfo {
    public ApplicationInfo applicationInfo;
    public int    descriptionRes;
    public String processName;
    public String splitName;
    public boolean enabled = true;
    public boolean exported;
    public boolean directBootAware;
    /** AOSP API-31 ComponentInfo.attributionTags: "the attribution tags of this
     *  component" -- the `android:attributionTags` manifest attribute, used by
     *  the AppOps/privacy plumbing to say which attribution a component's
     *  operations are charged to. Mac.apk's manifest parser does not read the
     *  attribute yet, so this stays null on the *Info objects we generate,
     *  which is the same value AOSP reports for a component that declares
     *  none (the overwhelming majority). The field must EXIST because we
     *  present API 36 and a caller reading it would otherwise take a
     *  NoSuchFieldError. */
    public String[] attributionTags;

    public ComponentInfo() {}

    /** AOSP ComponentInfo(ComponentInfo orig) (ComponentInfo.java:110-120,
     *  android14-release), field for field over the subset this tree declares.
     *  Required by ProviderInfo's copy constructor. No null-check on `orig`,
     *  matching AOSP. */
    public ComponentInfo(ComponentInfo orig) {
        super(orig);
        applicationInfo  = orig.applicationInfo;
        descriptionRes   = orig.descriptionRes;
        processName      = orig.processName;
        splitName        = orig.splitName;
        enabled          = orig.enabled;
        exported         = orig.exported;
        directBootAware  = orig.directBootAware;
        attributionTags  = orig.attributionTags;
    }

    // ---- Parcel marshalling ------------------------------------------------
    // Like PackageItemInfo, AOSP's ComponentInfo is not itself Parcelable
    // (android11-release ComponentInfo.java:36); it just contributes its own
    // fields between the base's and the leaf's. Order below is AOSP
    // ComponentInfo.java:197-206 verbatim.

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        // DIVERGENCE from AOSP ComponentInfo.java:199, which is an unconditional
        // `applicationInfo.writeToParcel(dest, parcelableFlags)` and therefore
        // NPEs on a null applicationInfo. On real Android that cannot happen:
        // PackageManagerService always attaches an ApplicationInfo to every
        // component it generates. In THIS tree it can and does -- e.g.
        // MacApkPackageManager.getServiceInfo / getProviderInfo build their
        // ServiceInfo / ProviderInfo without one, and getPackageInfo puts
        // exactly those objects into PackageInfo.services / .providers. So the
        // faithful-to-the-letter form would turn a plain
        // getPackageInfo(GET_SERVICES) round-trip into a NullPointerException.
        // writeTypedObject emits AOSP's own present/absent shape (writeInt(1) +
        // body, or writeInt(0)) -- the same one AOSP uses for the nullable
        // PackageInfo.applicationInfo at PackageInfo.java:466-471 -- so the
        // non-null case is byte-identical and only the case AOSP never reaches
        // behaves differently.
        dest.writeTypedObject(applicationInfo, parcelableFlags);
        dest.writeString(processName);
        dest.writeString(splitName);
        dest.writeStringArray(attributionTags);
        dest.writeInt(descriptionRes);
        dest.writeInt(enabled ? 1 : 0);
        dest.writeInt(exported ? 1 : 0);
        dest.writeInt(directBootAware ? 1 : 0);
    }

    /** AOSP ComponentInfo.java:208-217. */
    protected ComponentInfo(android.os.Parcel source) {
        super(source);
        applicationInfo = source.readTypedObject(ApplicationInfo.CREATOR);
        processName = source.readString();
        splitName = source.readString();
        attributionTags = source.createStringArray();
        descriptionRes = source.readInt();
        enabled = (source.readInt() != 0);
        exported = (source.readInt() != 0);
        directBootAware = (source.readInt() != 0);
    }

    // AOSP ComponentInfo.isEnabled(): a component is only really enabled if
    // BOTH it and its owning app are. Callers (PackageManager component-enable
    // checks) use this instead of reading the `enabled` field directly because
    // the field alone ignores an app disabled as a whole.
    public boolean isEnabled() {
        return enabled && applicationInfo.enabled;
    }

    /** AOSP ComponentInfo.getApplicationInfo() override -- the owning app. */
    @Override protected ApplicationInfo getApplicationInfo() { return applicationInfo; }

    /** AOSP ComponentInfo.dumpFront(Printer, String):196-217 verbatim. */
    @Override protected void dumpFront(android.util.Printer pw, String prefix) {
        super.dumpFront(pw, prefix);
        if (processName != null && !processName.equals(packageName)) {
            pw.println(prefix + "processName=" + processName);
        }
        if (splitName != null) {
            pw.println(prefix + "splitName=" + splitName);
        }
        if (attributionTags != null && attributionTags.length > 0) {
            StringBuilder tags = new StringBuilder();
            tags.append(attributionTags[0]);
            for (int i = 1; i < attributionTags.length; i++) {
                tags.append(", ");
                tags.append(attributionTags[i]);
            }
            pw.println(prefix + "attributionTags=[" + tags + "]");
        }
        pw.println(prefix + "enabled=" + enabled + " exported=" + exported
                + " directBootAware=" + directBootAware);
        if (descriptionRes != 0) {
            pw.println(prefix + "description=" + descriptionRes);
        }
    }

    /** AOSP ComponentInfo.dumpBack(Printer, String):219-221 -> the flags form
     *  at :223-234. Note AOSP's null branch prints "ApplicationInfo: null"
     *  rather than skipping, so the line count of a dump is stable. */
    @Override protected void dumpBack(android.util.Printer pw, String prefix) {
        dumpBack(pw, prefix, DUMP_FLAG_ALL);
    }

    void dumpBack(android.util.Printer pw, String prefix, int dumpFlags) {
        if ((dumpFlags & DUMP_FLAG_APPLICATION) != 0) {
            if (applicationInfo != null) {
                pw.println(prefix + "ApplicationInfo:");
                applicationInfo.dump(pw, prefix + "  ", dumpFlags);
            } else {
                pw.println(prefix + "ApplicationInfo: null");
            }
        }
        super.dumpBack(pw, prefix);
    }

    /** AOSP ComponentInfo overrides loadLabel to insert the OWNING APPLICATION
     *  between the component's own label and the bare component name. Quoted
     *  from core/java/android/content/pm/ComponentInfo.java (loadUnsafeLabel,
     *  which loadLabel mirrors):
     *
     *      if (labelRes != 0) { label = pm.getText(packageName, labelRes, ai);
     *                           if (label != null) return label; }
     *      if (ai.nonLocalizedLabel != null) return ai.nonLocalizedLabel;
     *      if (ai.labelRes != 0) { label = pm.getText(packageName, ai.labelRes, ai);
     *                              if (label != null) return label; }
     *      return name;
     *
     *  We inherited PackageItemInfo's version, which goes straight from the
     *  component's own labelRes to `name` -- so an activity that declares NO
     *  android:label (the common case; the label usually sits on <application>)
     *  reported its CLASS NAME as the title.
     *
     *  Measured victim (2026-08-02): opensudoku's action bar read
     *  "TitleScreenActivity" where the emulator reads "Open Sudoku". Its
     *  <activity> has no android:label at all and <application> carries
     *  android:label="@string/app_name". Fleet-wide: this is every app whose
     *  activities inherit the application label, which is most of them. */
    @Override
    public CharSequence loadLabel(android.content.pm.PackageManager pm) {
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        if (labelRes != 0) {
            CharSequence own = macapkLabelFromRes(labelRes);
            if (own != null) return own;
        }
        ApplicationInfo ai = applicationInfo;
        if (ai != null) {
            if (ai.nonLocalizedLabel != null) return ai.nonLocalizedLabel;
            if (ai.labelRes != 0) {
                CharSequence app = macapkLabelFromRes(ai.labelRes);
                if (app != null) return app;
            }
        }
        if (name != null) return name;
        return packageName != null ? packageName : "";
    }

    /** Resolve a label string resource, or null when it does not resolve --
     *  null (not the id, not an empty string) so each tier of the AOSP chain
     *  above can fall through cleanly to the next. */
    private static CharSequence macapkLabelFromRes(int res) {
        try {
            android.content.res.Resources r = android.content.res.Resources.getSystem();
            if (r != null) {
                String s = r.getString(res);
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // AOSP ComponentInfo.getIconResource()/getLogoResource()/getBannerResource():
    // fall back to the owning application's icon/logo/banner when the
    // component doesn't declare its own (icon/logo/banner are inherited from
    // PackageItemInfo). Icon-loading UI (task descriptions, notifications,
    // launcher shortcuts) calls these on an ActivityInfo/ServiceInfo/etc.
    public final int getIconResource() {
        return icon != 0 ? icon : applicationInfo.icon;
    }
    public final int getLogoResource() {
        return logo != 0 ? logo : applicationInfo.logo;
    }
    public final int getBannerResource() {
        return banner != 0 ? banner : applicationInfo.banner;
    }
}
