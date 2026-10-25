package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

public class PackageInfo implements Parcelable {
    public String packageName;
    public int versionCode;
    public long longVersionCode;
    public String versionName;
    public ApplicationInfo applicationInfo;
    // Install/update timestamps. Real Android fills these from the package
    // database, i.e. from a value that was fixed at install time and does not
    // move afterwards. These are DELIBERATELY not initialised here (AOSP
    // PackageInfo.java:113/119 declare them with no initializer): the value is
    // the generator's job, and MacApkPackageManager.getPackageInfo derives a
    // STABLE one from the APK file's mtime.
    //
    // They used to be assigned System.currentTimeMillis() in the constructor,
    // and since getPackageInfo news up a PackageInfo per call, two calls a
    // millisecond apart returned two different install times. That is the
    // re-read-forever shape: an app that caches firstInstallTime and compares
    // it against a later read concludes it was reinstalled, every time it
    // looks. Measured by the fwprobe answer-differential as
    // PM.stability.pkgInfo.{firstInstallTime,lastUpdateTime}Same = 0 against 1
    // on the reference.
    public long firstInstallTime;
    public long lastUpdateTime;
    public String[] requestedPermissions;
    public int[] requestedPermissionsFlags;
    public String[] splitNames;
    public Signature[] signatures;
    // AOSP public component arrays, populated only when the caller passes the
    // matching GET_* flag (GET_ACTIVITIES, GET_SERVICES, GET_PROVIDERS,
    // GET_RECEIVERS, GET_PERMISSIONS, GET_INSTRUMENTATION). Apps that
    // enumerate their own manifest (plugin frameworks, component discovery)
    // read these; absent fields throw NoSuchFieldError. Left null until a
    // GET_*-flagged getPackageInfo populates them, matching AOSP's contract
    // (the array is null, not empty, when the flag was not requested).
    public ActivityInfo[] activities;
    public ServiceInfo[] services;
    public ProviderInfo[] providers;
    public ActivityInfo[] receivers;
    public PermissionInfo[] permissions;
    public InstrumentationInfo[] instrumentation;
    public FeatureInfo[] reqFeatures;
    public ConfigurationInfo[] configPreferences;
    /** Shared user id this package runs under (android:sharedUserId). */
    public String sharedUserId;
    public int sharedUserLabel;
    /** Group ids the package is a member of. */
    public int[] gids;

    // ---- GET_* flags PackageManager consumers pass to request the above ----
    /** AOSP PackageInfo.REQUESTED_PERMISSION_REQUIRED (1<<0): set on every
     *  entry of requestedPermissionsFlags ("deprecated but for compatibility").
     *  getPackageArchiveInfo already writes this VALUE by hand because the
     *  constant did not exist; now it does. */
    public static final int REQUESTED_PERMISSION_REQUIRED = 0x00000001;
    /** AOSP API-31: the permission was implicitly added by the platform for a
     *  legacy targetSdk, not declared by the app. */
    public static final int REQUESTED_PERMISSION_IMPLICIT = 0x00000004;
    /** AOSP API-31: `<uses-permission android:usesPermissionFlags="neverForLocation">`. */
    public static final int REQUESTED_PERMISSION_NEVER_FOR_LOCATION = 0x00010000;
    public static final int REQUESTED_PERMISSION_GRANTED = 0x00000002;
    // API 28+. GMS's GoogleSignatureVerifier reads this via JNI GetFieldID
    // during Firebase init; the field must exist (and be of type SigningInfo)
    // or GetFieldID throws NoSuchFieldError uncaught on the Unity main thread.
    // Populated by MacApkPackageManager.getPackageInfo with a no-signers
    // SigningInfo (mirrors our null `signatures` stance).
    public SigningInfo signingInfo;

    /** AOSP PackageInfo.java:129 / :135. The base APK's android:revisionCode and
     *  the per-split revision codes. A non-split install carries revision 0 and
     *  a null split array, which is what we leave them at. */
    public int   baseRevisionCode;
    public int[] splitRevisionCodes;

    /** AOSP PackageInfo.java:306. The `<uses-feature-group>` sets the package
     *  declares. Null when the manifest declares none (the overwhelmingly
     *  common case, and the probe's). */
    public FeatureGroupInfo[] featureGroups;

    /** AOSP PackageInfo.java:395. True only for an APEX module; Mac.apk loads
     *  APKs, never APEXes. */
    public boolean isApex;

    /** AOSP PackageInfo.attributions (API 31, PackageInfo.java:247): the
     *  `<attribution>` elements the manifest declares, or null when it declares
     *  none -- which is what Mac.apk's parser (which does not read the element
     *  yet) leaves it at, and what AOSP reports for nearly every package. The
     *  FIELD must exist: we present API 36, so a getfield on it has to link. */
    public Attribution[] attributions;

    /** AOSP PackageInfo.mArchiveTimeMillis (API 35), private with a getter. */
    private long mArchiveTimeMillis;

    /** AOSP PackageInfo.mApexPackageName (API 34), private with a getter. */
    private String mApexPackageName;

    public PackageInfo() {}

    /** AOSP PackageInfo.getArchiveTimeMillis() (API 35): "the time at which the
     *  app was archived for the user", in System.currentTimeMillis() units.
     *
     *  Mac.apk never archives a package (see PackageManager.getArchivedPackage
     *  for the full reasoning), so this is 0 for everything we generate -- the
     *  same value AOSP reports for any package that has not been archived. */
    public long getArchiveTimeMillis() { return mArchiveTimeMillis; }

    /** AOSP's @hide setter, kept so a future archiving path can fill it. */
    public void setArchiveTimeMillis(long value) { mArchiveTimeMillis = value; }

    /** AOSP PackageInfo.getApexPackageName() (API 34): "if the package is an
     *  APEX package, returns the name of the APEX; null otherwise." Mac.apk
     *  loads APKs and never APEXes (isApex is permanently false), so this is
     *  always null -- the AOSP answer for any non-APEX package. */
    public String getApexPackageName() { return mApexPackageName; }

    /** AOSP's @hide setter. */
    public void setApexPackageName(String apexPackageName) {
        mApexPackageName = apexPackageName;
    }
    /** v1.0.110: API-28 method. F-Droid reads this in its repo / app
     *  metadata pipeline. AOSP returns longVersionCode when set, else
     *  falls back to versionCode. */
    public long getLongVersionCode() {
        return longVersionCode != 0L ? longVersionCode : (long) versionCode;
    }
    public void setLongVersionCode(long v) {
        this.longVersionCode = v;
        this.versionCode = (int) (v & 0xFFFFFFFFL);
    }

    // ---- Parcelable ----
    // Field order is AOSP PackageInfo.java:461-513 (android11-release),
    // restricted to the fields this class declares. AOSP additionally marshals
    // isStub, coreApp, requiredForAllUsers, restrictedAccountType,
    // requiredAccountType, overlayTarget, overlayCategory, overlayPriority,
    // mOverlayIsStatic, compileSdkVersion and compileSdkVersionCodename, none of
    // which this tree models.
    //
    // ⚠ HISTORY, so this is not "simplified" back: every *Info here used to ride
    // dest.writeValue(...). That worked only while Parcel aliased live objects
    // instead of marshalling them. Once Parcel became real (a) writeValue THROWS
    // "unable to marshal value" for anything neither Parcelable nor
    // Serializable, which every one of these arrays was, and (b) even had it
    // succeeded, readValue answers a VAL_PARCELABLEARRAY with a Parcelable[],
    // never the concrete component type, so `(ActivityInfo[]) readValue(null)`
    // was a guaranteed ClassCastException. writeTypedArray/createTypedArray is
    // AOSP's own choice for these nine homogeneous arrays and has neither
    // problem: the element type comes from the CALLER's CREATOR, and
    // Creator.newArray gives the array its exact component type.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeStringArray(splitNames);
        dest.writeInt(versionCode);
        // AOSP writes `versionCodeMajor` in this slot (PackageInfo.java:465) and
        // composes the two halves on demand in getLongVersionCode(). This tree
        // stores the composed value in its own longVersionCode field instead, so
        // it is marshalled where its AOSP counterpart sits rather than appended
        // at the end -- that keeps every other field on AOSP's relative order.
        dest.writeLong(longVersionCode);
        dest.writeString(versionName);
        dest.writeInt(baseRevisionCode);
        dest.writeIntArray(splitRevisionCodes);
        dest.writeString(sharedUserId);
        dest.writeInt(sharedUserLabel);
        // AOSP PackageInfo.java:466-471 hand-rolls the present-flag; that is
        // exactly what writeTypedObject emits (writeInt(1) + body, or
        // writeInt(0)), so this is the same bytes with the shared helper.
        dest.writeTypedObject(applicationInfo, flags);
        dest.writeLong(firstInstallTime);
        dest.writeLong(lastUpdateTime);
        dest.writeIntArray(gids);
        dest.writeTypedArray(activities, flags);
        dest.writeTypedArray(receivers, flags);
        dest.writeTypedArray(services, flags);
        dest.writeTypedArray(providers, flags);
        dest.writeTypedArray(instrumentation, flags);
        dest.writeTypedArray(permissions, flags);
        dest.writeStringArray(requestedPermissions);
        dest.writeIntArray(requestedPermissionsFlags);
        dest.writeTypedArray(signatures, flags);
        dest.writeTypedArray(configPreferences, flags);
        dest.writeTypedArray(reqFeatures, flags);
        dest.writeTypedArray(featureGroups, flags);
        // AOSP writes attributions immediately before installLocation
        // (PackageInfo.java:614-615), which is where it goes here.
        dest.writeTypedArray(attributions, flags);
        dest.writeInt(installLocation);
        dest.writeTypedObject(signingInfo, flags);
        dest.writeBoolean(isApex);
        dest.writeString(mApexPackageName);
        dest.writeLong(mArchiveTimeMillis);
    }

    /** AOSP PackageInfo.java:526-556, same subset and order as the writer. */
    private PackageInfo(Parcel source) {
        packageName = source.readString();
        splitNames = source.createStringArray();
        versionCode = source.readInt();
        longVersionCode = source.readLong();
        versionName = source.readString();
        baseRevisionCode = source.readInt();
        splitRevisionCodes = source.createIntArray();
        sharedUserId = source.readString();
        sharedUserLabel = source.readInt();
        applicationInfo = source.readTypedObject(ApplicationInfo.CREATOR);
        firstInstallTime = source.readLong();
        lastUpdateTime = source.readLong();
        gids = source.createIntArray();
        activities = source.createTypedArray(ActivityInfo.CREATOR);
        receivers = source.createTypedArray(ActivityInfo.CREATOR);
        services = source.createTypedArray(ServiceInfo.CREATOR);
        providers = source.createTypedArray(ProviderInfo.CREATOR);
        instrumentation = source.createTypedArray(InstrumentationInfo.CREATOR);
        permissions = source.createTypedArray(PermissionInfo.CREATOR);
        requestedPermissions = source.createStringArray();
        requestedPermissionsFlags = source.createIntArray();
        signatures = source.createTypedArray(Signature.CREATOR);
        configPreferences = source.createTypedArray(ConfigurationInfo.CREATOR);
        reqFeatures = source.createTypedArray(FeatureInfo.CREATOR);
        featureGroups = source.createTypedArray(FeatureGroupInfo.CREATOR);
        attributions = source.createTypedArray(Attribution.CREATOR);
        installLocation = source.readInt();
        signingInfo = source.readTypedObject(SigningInfo.CREATOR);
        isApex = source.readBoolean();
        mApexPackageName = source.readString();
        mArchiveTimeMillis = source.readLong();
    }

    public static final Parcelable.Creator<PackageInfo> CREATOR =
        new Parcelable.Creator<PackageInfo>() {
            @Override public PackageInfo createFromParcel(Parcel source) {
                return new PackageInfo(source);
            }
            @Override public PackageInfo[] newArray(int size) {
                return new PackageInfo[size];
            }
        };

    /** AOSP PackageInfo.java:311-350 (the constants) and :352 (the field, whose
     *  initializer really is INTERNAL_ONLY). ⚠ The initializer is NOT the value
     *  a parsed package reports: PackageParser defaults an undeclared
     *  `<manifest android:installLocation>` to PARSE_DEFAULT_INSTALL_LOCATION ==
     *  INSTALL_LOCATION_UNSPECIFIED (-1) and generatePackageInfo copies that
     *  over, which is why a normally-installed app answers -1 rather than 1.
     *  MacApkPackageManager.getPackageInfo assigns it for that reason. */
    public static final int INSTALL_LOCATION_UNSPECIFIED     = -1;
    public static final int INSTALL_LOCATION_AUTO            = 0;
    public static final int INSTALL_LOCATION_INTERNAL_ONLY   = 1;
    public static final int INSTALL_LOCATION_PREFER_EXTERNAL = 2;
    public int installLocation = INSTALL_LOCATION_INTERNAL_ONLY;
}

