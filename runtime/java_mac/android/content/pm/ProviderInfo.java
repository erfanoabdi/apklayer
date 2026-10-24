package android.content.pm;

import android.os.PatternMatcher;

// AOSP: ProviderInfo extends ComponentInfo extends PackageItemInfo. The
// inheritance is LOAD-BEARING: AndroidX-Startup's InitializationProvider.onCreate
// reads the provider's <meta-data> via `getfield
// android/content/pm/PackageItemInfo.metaData` on a ProviderInfo instance to
// enumerate Initializer classes; FirebaseInitProvider does similar. Mac.apk
// runs the guest JVM with -Xverify:none, so a getfield of an inherited field on
// a receiver whose class does NOT actually extend the field's declaring class
// is not rejected -- it reads a WRONG offset and returns a garbage (non-null)
// reference, which then faults inside the JVM's invokevirtual resolver on the
// next call (this was a launch crash for SDK-heavy apps such as GD Lite via
// androidx.startup). So ProviderInfo MUST extend ComponentInfo and inherit
// name/packageName/exported/metaData rather than redeclaring (shadowing) them.
public class ProviderInfo extends ComponentInfo implements android.os.Parcelable {
    /** Comma-separated list of authorities the provider serves (from
     *  android:authorities). ProviderInfo-specific (not on ComponentInfo). */
    public String  authority;
    /** android:initOrder -- providers with higher initOrder run earlier. */
    public int     initOrder;
    /** android:grantUriPermissions -- whether the provider grants temporary
     *  URI access. */
    public boolean grantUriPermissions;
    /** AOSP ProviderInfo.java:76-79 (android14-release), verbatim contract:
     *  "If true, this content provider allows multiple instances of itself to
     *  run in different process. If false, a single instances is always run in
     *  processName." Default false, as AOSP declares it.
     *
     *  Mac.apk is single-process, so this field is never ACTED on -- it exists
     *  because guests READ it. rikka.shizuku.ShizukuProvider (bundled by Aurora
     *  Store 4.8.4) does a plain getfield on it during provider setup and threw
     *  `NoSuchFieldError: Class android.content.pm.ProviderInfo does not have
     *  member field 'boolean multiprocess'` on EVERY boot. Boot caught and
     *  recovered, so it read as log noise, but a NoSuchFieldError aborts the
     *  reader mid-method: whatever that provider was doing after the read
     *  simply did not happen. Absent-because-unmodelled is still a behaviour
     *  difference from Android when the app's own bytecode names the field. */
    public boolean multiprocess;

    /** AOSP ProviderInfo.java:32-41 (android14-release):
     *      public String readPermission = null;    // "Optional permission
     *          required for read-only access this content provider."
     *      public String writePermission = null;   // "Optional permission
     *          required for read/write access this content provider."
     *
     *  POPULATED FROM THE MANIFEST, not defaulted to null. Manifest.java's
     *  provider walk mirrors PackageParser.parseProvider lines 5111-5135
     *  exactly, including the two fallbacks that make these fields non-trivial:
     *  an undeclared android:readPermission falls back to android:permission on
     *  the same <provider>, and if THAT is also absent it falls back to
     *  `<application android:permission>`. Only when all three are absent is the
     *  honest answer null -- which is also what a real device reports for such a
     *  provider, so a null here is a MEASURED null, not a placeholder.
     *
     *  Why it matters that these exist at all: com.zhiliaoapp.musically 46.4.3
     *  reads `providerInfo.readPermission` in the FIRST statement of two of its
     *  ContentProviders' onCreate (ShellProvider, ExportedShellProvider) and
     *  threw `NoSuchFieldError: ... does not have member field
     *  'java.lang.String readPermission'` on every boot. A NoSuchFieldError
     *  aborts the reader mid-method, so both providers' entire onCreate body
     *  after the read simply did not run -- and Boot logged them as
     *  "onCreate returned false", i.e. AOSP's signal for "this provider failed
     *  to install". */
    public String readPermission = null;
    public String writePermission = null;

    /** AOSP ProviderInfo.java:50-54: "If true, always apply URI permission
     *  grants, as per the forceUriPermissions attribute." From
     *  android:forceUriPermissions on the <provider>; default false, which is
     *  the value for every provider that does not declare it. */
    public boolean forceUriPermissions = false;

    /** AOSP ProviderInfo.java:56-64: "If non-null, these are the patterns that
     *  are allowed for granting URI permissions. Any URI that does not match
     *  one of these patterns will not allowed to be granted. If null, all URIs
     *  are allowed."
     *
     *  Built from `<grant-uri-permission>` children (PackageParser lines
     *  5240-5277). NULL when the provider declares none -- and null is
     *  MEANINGFUL here, not a placeholder: AOSP's own contract is that null
     *  means "all URIs are allowed", which is precisely the state of a provider
     *  with no <grant-uri-permission> element. */
    public PatternMatcher[] uriPermissionPatterns = null;

    /** AOSP ProviderInfo.java:66-72: "If non-null, these are path-specific
     *  permissions that are allowed for accessing the provider."
     *  Built from `<path-permission>` children (PackageParser lines 5292-5360).
     *  NULL when the provider declares none, exactly as AOSP defaults it. */
    public PathPermission[] pathPermissions = null;

    /** AOSP ProviderInfo.java:82-86. Bit in {@link #flags} indicating if the
     *  provider is visible to ephemeral applications. @hide on AOSP. */
    public static final int FLAG_VISIBLE_TO_INSTANT_APP = 0x100000;

    /** AOSP ProviderInfo.java:88-93: "If set, a single instance of the provider
     *  will run for all users on the device. Set from the android:singleUser
     *  attribute." */
    public static final int FLAG_SINGLE_USER = 0x40000000;

    /** AOSP ProviderInfo.java:95-100: "Options that have been set in the
     *  provider declaration in the manifest. These include: FLAG_SINGLE_USER."
     *
     *  Mac.apk is single-user and single-process, so FLAG_SINGLE_USER is never
     *  ACTED on -- but it is still REPORTED faithfully from android:singleUser
     *  because ContentProvider.attachInfo reads it into mSingleUser and guest
     *  code can read it back. FLAG_VISIBLE_TO_INSTANT_APP is never set: we do
     *  not model instant apps, and a device only sets that bit for a provider
     *  in an instant-app-visible package, which ours never is. */
    public int flags = 0;

    /** AOSP ProviderInfo.java:102-108, @Deprecated with the note "This flag is
     *  now being ignored." From android:syncable. Carried because the field is
     *  readable from guest bytecode; nothing in this tree consumes it, exactly
     *  as nothing in AOSP does any more. */
    @Deprecated
    public boolean isSyncable = false;

    // name, packageName, exported, metaData are INHERITED from
    // ComponentInfo/PackageItemInfo -- do NOT redeclare (shadowing reintroduces
    // the wrong-offset getfield crash above).
    public ProviderInfo() {}

    /** AOSP ProviderInfo.dump(Printer, String):148-150 -> the flags form at
     *  :152-157, verbatim. Both lines are unconditional upstream. */
    public void dump(android.util.Printer pw, String prefix) {
        dump(pw, prefix, DUMP_FLAG_ALL);
    }

    public void dump(android.util.Printer pw, String prefix, int dumpFlags) {
        super.dumpFront(pw, prefix);
        pw.println(prefix + "authority=" + authority);
        pw.println(prefix + "flags=0x" + Integer.toHexString(flags));
        super.dumpBack(pw, prefix, dumpFlags);
    }

    /** AOSP ProviderInfo.java:113-126, the copy constructor, field for field. */
    public ProviderInfo(ProviderInfo orig) {
        super(orig);
        authority = orig.authority;
        readPermission = orig.readPermission;
        writePermission = orig.writePermission;
        grantUriPermissions = orig.grantUriPermissions;
        forceUriPermissions = orig.forceUriPermissions;
        uriPermissionPatterns = orig.uriPermissionPatterns;
        pathPermissions = orig.pathPermissions;
        multiprocess = orig.multiprocess;
        initOrder = orig.initOrder;
        flags = orig.flags;
        isSyncable = orig.isSyncable;
    }

    /** AOSP ProviderInfo.java:172-174. */
    @Override public String toString() {
        return "ContentProviderInfo{name=" + authority + " className=" + name + "}";
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `ProviderInfo extends ComponentInfo implements Parcelable`
    // (android11-release ProviderInfo.java:29). Order is AOSP
    // ProviderInfo.java:146-159. As of this change the field set is COMPLETE
    // against AOSP android14-release and the order below is AOSP's verbatim
    // (the note that used to sit here -- "readPermission, writePermission,
    // forceUriPermissions, uriPermissionPatterns, pathPermissions, flags and
    // isSyncable are not modelled here" -- is now stale and has been removed
    // rather than left to mislead).
    //
    // The wire ENCODING still differs from a device's: AOSP uses
    // writeString8/readString8 and this tree's Parcel has writeString/
    // readString. These parcels never leave the process, so the encoding is
    // unobservable; the ORDER matches so the file stays readable against the
    // source it mirrors.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeString(authority);
        dest.writeString(readPermission);
        dest.writeString(writePermission);
        dest.writeInt(grantUriPermissions ? 1 : 0);
        dest.writeInt(forceUriPermissions ? 1 : 0);
        dest.writeTypedArray(uriPermissionPatterns, parcelableFlags);
        dest.writeTypedArray(pathPermissions, parcelableFlags);
        dest.writeInt(multiprocess ? 1 : 0);
        dest.writeInt(initOrder);
        dest.writeInt(flags);
        dest.writeInt(isSyncable ? 1 : 0);
    }

    /** AOSP ProviderInfo.java:177-189, now the FULL field set and AOSP's exact
     *  order. (AOSP uses writeString8/readString8 and this tree uses
     *  writeString/readString; the parcels never leave the process, so the
     *  encoding differs from a device's while the ORDER matches the source this
     *  mirrors.) */
    private ProviderInfo(android.os.Parcel source) {
        super(source);
        authority = source.readString();
        readPermission = source.readString();
        writePermission = source.readString();
        grantUriPermissions = source.readInt() != 0;
        forceUriPermissions = source.readInt() != 0;
        uriPermissionPatterns = source.createTypedArray(PatternMatcher.CREATOR);
        pathPermissions = source.createTypedArray(PathPermission.CREATOR);
        multiprocess = source.readInt() != 0;
        initOrder = source.readInt();
        flags = source.readInt();
        isSyncable = source.readInt() != 0;
    }

    public static final android.os.Parcelable.Creator<ProviderInfo> CREATOR =
        new android.os.Parcelable.Creator<ProviderInfo>() {
            @Override public ProviderInfo createFromParcel(android.os.Parcel source) {
                return new ProviderInfo(source);
            }
            @Override public ProviderInfo[] newArray(int size) {
                return new ProviderInfo[size];
            }
        };
}
