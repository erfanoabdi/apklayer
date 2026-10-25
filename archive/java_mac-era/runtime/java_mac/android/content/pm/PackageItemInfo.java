// android.content.pm.PackageItemInfo -- common base for all of
// PackageManager's *Info classes (ActivityInfo, ServiceInfo,
// ProviderInfo, etc.). Holds the basic identifying fields each one
// shares (name, packageName, label, icon, metadata bag).
package android.content.pm;

public class PackageItemInfo {
    public String name;
    public String packageName;
    public int    labelRes;
    public CharSequence nonLocalizedLabel;
    public int    icon;
    public int    banner;
    public int    logo;
    public android.os.Bundle metaData;
    public int    showUserIcon;
    /** AOSP API-35 PackageItemInfo.isArchived: "whether the package is
     *  currently in an archived state" (frameworks/base
     *  core/java/android/content/pm/PackageItemInfo.java:186). Mac.apk never
     *  archives a package -- see PackageManager.getArchivedPackage -- so this
     *  stays false for everything we generate, which is the honest answer and
     *  the same one a device gives for any normally-installed package. The
     *  field must EXIST regardless: we present API 36, and a modern app that
     *  reads it on an *Info it got from us would otherwise take a
     *  NoSuchFieldError. */
    public boolean isArchived;

    /** ⚠ AOSP's no-arg constructor is NOT empty: it sets
     *  `showUserIcon = UserHandle.USER_NULL` (PackageItemInfo.java:188-190).
     *  Zero -- the Java default this class used to leave in place -- is a VALID
     *  USER ID (the primary user), so every *Info we handed out claimed "badge
     *  this icon for user 0" instead of "no badge". Anything that branches on
     *  showUserIcon != USER_NULL (icon badging in a launcher, a chooser row, a
     *  settings list) therefore took the badged path unconditionally. */
    public PackageItemInfo() {
        showUserIcon = android.os.UserHandle.USER_NULL;
    }

    /** AOSP PackageItemInfo(PackageItemInfo orig) -- the copy constructor the
     *  whole *Info hierarchy chains through (ProviderInfo(ProviderInfo),
     *  ActivityInfo(ActivityInfo), ServiceInfo(ServiceInfo) all call
     *  super(orig)).
     *
     *  AOSP PackageItemInfo.java:180-192 (android14-release) verbatim over the
     *  fields this tree declares, INCLUDING both trims -- AOSP trims `name` and
     *  `nonLocalizedLabel`, and a copy that skipped them would hand back a
     *  different string than a device does. No null-check on `orig`: AOSP has
     *  none and NPEs, and swallowing that would turn a caller's bug into a
     *  silently blank *Info. */
    public PackageItemInfo(PackageItemInfo orig) {
        name              = orig.name;
        if (name != null) name = name.trim();
        packageName       = orig.packageName;
        labelRes          = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        if (nonLocalizedLabel != null) {
            nonLocalizedLabel = nonLocalizedLabel.toString().trim();
        }
        icon              = orig.icon;
        banner            = orig.banner;
        logo              = orig.logo;
        metaData          = orig.metaData;
        showUserIcon      = orig.showUserIcon;
        isArchived        = orig.isArchived;
    }

    // ---- Parcel marshalling ------------------------------------------------
    // AOSP's PackageItemInfo is NOT itself Parcelable (android11-release
    // PackageItemInfo.java:57 "public class PackageItemInfo {"): it carries a
    // public writeToParcel plus a protected (Parcel) constructor, and each
    // Parcelable LEAF (ActivityInfo, ApplicationInfo, PermissionInfo, ...)
    // chains into them. We mirror that shape exactly, because the chain is
    // what makes the field ORDER agree in both directions.
    //
    // AOSP writes these with writeString8 (a UTF-8 wire form added in API 31).
    // This tree's Parcel has only writeString (the UTF-16 form, faithful to
    // Parcel.cpp writeString16), and the two differ only in on-the-wire byte
    // encoding, never in the String value observed by a reader. Nothing in
    // Mac.apk hands a parcel to a real binder, so the encoding is unobservable
    // and writeString is the correct substitute throughout this package.

    /** AOSP PackageItemInfo.java:433-443. */
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        dest.writeString(name);
        dest.writeString(packageName);
        dest.writeInt(labelRes);
        android.text.TextUtils.writeToParcel(nonLocalizedLabel, dest, parcelableFlags);
        dest.writeInt(icon);
        dest.writeInt(logo);
        dest.writeBundle(metaData);
        dest.writeInt(banner);
        dest.writeInt(showUserIcon);
        dest.writeBoolean(isArchived);
    }

    /** AOSP PackageItemInfo.java:463-474. Note banner is written AFTER metaData
     *  even though the field is declared before it -- the declaration order and
     *  the wire order are not the same thing, so this reads in write order. */
    protected PackageItemInfo(android.os.Parcel source) {
        name = source.readString();
        packageName = source.readString();
        labelRes = source.readInt();
        nonLocalizedLabel =
            android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        icon = source.readInt();
        logo = source.readInt();
        metaData = source.readBundle();
        banner = source.readInt();
        showUserIcon = source.readInt();
        isArchived = source.readBoolean();
    }

    // AOSP PackageItemInfo.loadLabel(PackageManager): returns the explicit
    // nonLocalizedLabel, else the labelRes string, else falls back to the
    // package name. We have no resource label lookup, so return the best
    // non-null identifier. (HCR 1.8.1 calls ApplicationInfo.loadLabel during
    // boot; the call inherits this through the *Info hierarchy.)
    public CharSequence loadLabel(android.content.pm.PackageManager pm) {
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        // lane layout B, 2026-08-02: RESOLVE labelRes. AOSP's loadLabel reads
        // the string resource before falling back to the component name, and
        // `android:label="@string/app_name"` (the overwhelmingly common form)
        // arrives here as labelRes, not nonLocalizedLabel -- so we always fell
        // through to `name`, the fully-qualified CLASS name. Measured victim:
        // the tobsolitaire toolbar read "GameSelector" where the emulator reads
        // "Simple Solitaire Collection".
        // See docs/canvas-ab-analyst-report-b_2026-08-02.md item 10.
        if (labelRes != 0) {
            try {
                android.content.res.Resources r =
                    android.content.res.Resources.getSystem();
                if (r != null) {
                    String s = r.getString(labelRes);
                    if (s != null && !s.isEmpty()) return s;
                }
            } catch (Throwable ignored) {}
        }
        if (name != null) return name;
        return packageName != null ? packageName : "";
    }

    public CharSequence loadDescription(android.content.pm.PackageManager pm) {
        return null;
    }

    // AOSP PackageItemInfo.loadIcon(PackageManager): returns the item's icon
    // Drawable, falling back through the application icon to the framework
    // default -- and per the AOSP contract it NEVER returns null (callers such
    // as ImageView.setImageDrawable dereference the result directly). Prior to
    // this method existing, the whole *Info hierarchy was missing loadIcon, so
    // ANY app calling applicationInfo.loadIcon(pm) got a NoSuchMethodError that
    // aborted the calling frame mid-flight. Concretely this fired at the TOP of
    // a repackaged Minecraft's Activity.onCreate (an injected credits dialog
    // calls loadIcon before onCreate assigns `MainActivity.mInstance = this`)
    // -> onCreate aborted early -> mInstance stayed null -> the Google Play
    // store factory (which reads the static mInstance) NPE'd -> the license /
    // store boot step never ran -> the native boot worklist stalled before the
    // Ore-UI menu (pure-black screen). Generic: any app that loads its own icon
    // (notifications, task description, launcher shortcuts) exercises this path.
    public android.graphics.drawable.Drawable loadIcon(
            android.content.pm.PackageManager pm) {
        if (pm != null) {
            try {
                android.graphics.drawable.Drawable d = pm.getApplicationIcon(packageName);
                if (d != null) return d;
            } catch (Throwable ignored) {}
            try {
                android.graphics.drawable.Drawable d = pm.getDefaultActivityIcon();
                if (d != null) return d;
            } catch (Throwable ignored) {}
        }
        // Ultimate non-null fallback (AOSP resolves to the default activity icon
        // here and never returns null). A transparent ColorDrawable is a valid,
        // drawable, side-effect-free placeholder.
        return new android.graphics.drawable.ColorDrawable(0);
    }

    // AOSP PackageItemInfo.loadUnbadgedIcon: same as loadIcon minus the
    // work-profile badge overlay, which Mac.apk has no concept of.
    public android.graphics.drawable.Drawable loadUnbadgedIcon(
            android.content.pm.PackageManager pm) {
        return loadIcon(pm);
    }

    // AOSP PackageItemInfo.loadXmlMetaData(PackageManager, String): opens the
    // XML resource referenced by the named <meta-data> entry
    // (aosp-mirror/platform_frameworks_base main,
    //  core/java/android/content/pm/PackageItemInfo.java):
    //
    //     public XmlResourceParser loadXmlMetaData(PackageManager pm, String name) {
    //         if (metaData != null) {
    //             int resid = metaData.getInt(name);
    //             if (resid != 0) {
    //                 return pm.getXml(packageName, resid, getApplicationInfo());
    //             }
    //         }
    //         return null;
    //     }
    //
    // This used to be a hardcoded `return null` on the premise that Mac.apk had
    // no per-resource XML parser. That premise is stale, and the cost of leaving
    // it was not "one app degrades gracefully" -- it is that androidx
    // FileProvider cannot initialise on ANY app. FileProvider.parsePathStrategy
    // has exactly one way to reach its paths declaration:
    //
    //     in = info.loadXmlMetaData(pm, META_DATA_FILE_PROVIDER_PATHS);
    //     if (in == null) throw new IllegalArgumentException(
    //             "Missing " + META_DATA_FILE_PROVIDER_PATHS + " meta-data");
    //
    // so null here is not a soft failure, it is a thrown IllegalArgumentException
    // out of attachInfo. Measured on Mastodon 2.13.1 (2026-07-30): its
    // TweakedFileProvider aborted at boot with exactly that message even though
    // the manifest declares the meta-data correctly. FileProvider is how nearly
    // every modern app hands a file to another app (camera capture, attachment
    // pick, share, export), and androidx.startup's InitializationProvider
    // discovers its Initializer list through this same call.
    //
    // Everything the real implementation needs already existed:
    //   * Manifest.java parses <meta-data> children of <provider> including the
    //     android:resource form, and MacApkPackageManager.resolveContentProvider
    //     turns them into this Bundle under GET_META_DATA.
    //   * putTypedMeta's "reference" branch stores the RESOURCE ID for a
    //     non-integer reference such as @xml/fileprovider_paths, matching AOSP
    //     PackageParser.parseMetaData's putInt(name, v.resourceId) for
    //     android:resource. So metaData.getInt(name) is already the right id.
    //
    // ⚠ THE DIVERGENCE THAT USED TO BE HERE IS GONE (v1.1.0). This body used to
    // inline what AOSP's ApplicationPackageManager.getXml does internally,
    // because neither PackageManager.getXml(String, int, ApplicationInfo) nor
    // ComponentInfo.getApplicationInfo() existed in this tree. Both do now, so
    // the AOSP call is made directly and the two routes cannot drift. Same
    // null-on-failure contract either way (AOSP: "If the meta-data name is not
    // defined or the XML resource could not be found, null is returned").
    //
    // Note BaseBundle.getInt(k, def) returns the default on a TYPE mismatch
    // rather than throwing, so a String-valued android:value entry yields 0 here
    // and we correctly return null instead of opening a bogus resource.
    public android.content.res.XmlResourceParser loadXmlMetaData(
            android.content.pm.PackageManager pm, String name) {
        if (metaData == null || pm == null || name == null) return null;
        int resid = metaData.getInt(name, 0);
        if (resid == 0) return null;
        return pm.getXml(packageName, resid, getApplicationInfo());
    }

    /** AOSP PackageItemInfo dump flags (PackageItemInfo.java:431-441). @hide
     *  upstream, so they are package-private here -- they exist to let the
     *  *Info subclasses share one dump implementation the way AOSP does, not
     *  as app-visible API. */
    static final int DUMP_FLAG_DETAILS     = 1 << 0;
    static final int DUMP_FLAG_APPLICATION = 1 << 1;
    static final int DUMP_FLAG_ALL = DUMP_FLAG_DETAILS | DUMP_FLAG_APPLICATION;

    /** AOSP PackageItemInfo.dumpFront(Printer, String)
     *  (frameworks/base .../PackageItemInfo.java:440-452) verbatim. `protected`
     *  upstream and here: the *Info leaves call it from their own dump().
     *  Reproducing the exact TEXT matters -- `adb shell dumpsys package` output
     *  and any app that pipes an *Info through a Printer are parsed by tooling
     *  that expects these key=value shapes. */
    protected void dumpFront(android.util.Printer pw, String prefix) {
        if (name != null) {
            pw.println(prefix + "name=" + name);
        }
        pw.println(prefix + "packageName=" + packageName);
        if (labelRes != 0 || nonLocalizedLabel != null || icon != 0 || banner != 0) {
            pw.println(prefix + "labelRes=0x" + Integer.toHexString(labelRes)
                    + " nonLocalizedLabel=" + nonLocalizedLabel
                    + " icon=0x" + Integer.toHexString(icon)
                    + " banner=0x" + Integer.toHexString(banner));
        }
    }

    /** AOSP PackageItemInfo.dumpBack(Printer, String):455-457 -- the body is
     *  literally the comment "no back here". Subclasses override it. */
    protected void dumpBack(android.util.Printer pw, String prefix) {
        // no back here
    }

    /** AOSP PackageItemInfo.loadBanner(PackageManager):303-311. Unlike
     *  loadIcon this one is @Nullable: loadDefaultBanner returns null upstream
     *  ("there is no default banner"), so a component with no android:banner
     *  correctly reports none rather than a transparent placeholder. */
    public android.graphics.drawable.Drawable loadBanner(
            android.content.pm.PackageManager pm) {
        if (banner != 0 && pm != null) {
            android.graphics.drawable.Drawable dr =
                pm.getDrawable(packageName, banner, getApplicationInfo());
            if (dr != null) return dr;
        }
        return loadDefaultBanner(pm);
    }

    /** AOSP PackageItemInfo.loadLogo(PackageManager):323-331. @Nullable, same
     *  reasoning as loadBanner. */
    public android.graphics.drawable.Drawable loadLogo(
            android.content.pm.PackageManager pm) {
        if (logo != 0 && pm != null) {
            android.graphics.drawable.Drawable d =
                pm.getDrawable(packageName, logo, getApplicationInfo());
            if (d != null) return d;
        }
        return loadDefaultLogo(pm);
    }

    /** AOSP PackageItemInfo.loadDefaultBanner / loadDefaultLogo: both `return
     *  null` in the base class; only ApplicationInfo overrides loadDefaultIcon.
     *  @hide upstream, kept for the override chain. */
    protected android.graphics.drawable.Drawable loadDefaultBanner(
            android.content.pm.PackageManager pm) {
        return null;
    }
    protected android.graphics.drawable.Drawable loadDefaultLogo(
            android.content.pm.PackageManager pm) {
        return null;
    }

    /** AOSP PackageItemInfo.DisplayNameComparator (API 1): orders any two
     *  *Info objects by their USER-VISIBLE label, using a locale-aware
     *  Collator rather than String.compareTo -- which is the whole point of
     *  the class, and why an app sorting a component list uses it instead of
     *  sorting by name.
     *
     *  Body verbatim from PackageItemInfo.java:394-412 (android16-release),
     *  including the fall back to `name` when loadLabel returns null and the
     *  `final` on compare(). */
    public static class DisplayNameComparator
            implements java.util.Comparator<PackageItemInfo> {
        private final java.text.Collator sCollator = java.text.Collator.getInstance();
        private final android.content.pm.PackageManager mPM;

        public DisplayNameComparator(android.content.pm.PackageManager pm) { mPM = pm; }

        @Override public final int compare(PackageItemInfo aa, PackageItemInfo ab) {
            CharSequence sa = aa.loadLabel(mPM);
            if (sa == null) sa = aa.name;
            CharSequence sb = ab.loadLabel(mPM);
            if (sb == null) sb = ab.name;
            return sCollator.compare(String.valueOf(sa), String.valueOf(sb));
        }
    }

    /** AOSP PackageItemInfo.getApplicationInfo(): "Get the ApplicationInfo for
     *  the application to which this item belongs, if available, otherwise
     *  returns null." @hide upstream but load-bearing for loadBanner/loadLogo
     *  above (and for the AOSP form of loadXmlMetaData). The base class knows of
     *  no owning application; ComponentInfo and ApplicationInfo override it. */
    protected ApplicationInfo getApplicationInfo() { return null; }
}
