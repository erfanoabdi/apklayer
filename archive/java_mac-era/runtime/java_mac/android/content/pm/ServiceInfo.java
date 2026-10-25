package android.content.pm;

// AOSP: ServiceInfo extends ComponentInfo extends PackageItemInfo. The
// inheritance is LOAD-BEARING (not cosmetic): consumers read the service's
// <meta-data> via `getfield android/content/pm/PackageItemInfo.metaData` on a
// ServiceInfo instance (Firebase ComponentDiscovery enumerates registrars this
// way). Mac.apk runs the guest JVM with -Xverify:none, so a getfield of an
// inherited field on a receiver whose class does NOT actually extend the
// field's declaring class is not rejected -- it reads a WRONG offset and
// returns a garbage (non-null) reference, which then faults inside the JVM's
// invokevirtual resolver on the next call. So ServiceInfo MUST extend
// ComponentInfo and inherit name/packageName/enabled/exported/metaData rather
// than redeclaring (shadowing) them.
//
// `name` is the fully-qualified service class name; `metaData` (inherited from
// PackageItemInfo) is the parsed <meta-data .../> children.
public class ServiceInfo extends ComponentInfo implements android.os.Parcelable {
    /** android:permission required to bind/start this service. */
    public String permission;
    /** Bitmask of the FLAG_* values below. */
    public int flags;
    /** Declared foreground-service types (FOREGROUND_SERVICE_TYPE_*). */
    public int mForegroundServiceType = FOREGROUND_SERVICE_TYPE_NONE;

    public static final int FLAG_STOP_WITH_TASK              = 0x0001;
    public static final int FLAG_ISOLATED_PROCESS            = 0x0002;
    public static final int FLAG_EXTERNAL_SERVICE            = 0x0004;
    public static final int FLAG_USE_APP_ZYGOTE              = 0x0008;
    public static final int FLAG_ALLOW_SHARED_ISOLATED_PROCESS = 0x0010;
    public static final int FLAG_VISIBLE_TO_INSTANT_APP      = 0x100000;
    public static final int FLAG_SINGLE_USER                 = 0x40000000;

    public static final int FOREGROUND_SERVICE_TYPE_NONE             = 0;
    public static final int FOREGROUND_SERVICE_TYPE_MANIFEST         = -1;
    public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC        = 1 << 0;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK   = 1 << 1;
    public static final int FOREGROUND_SERVICE_TYPE_PHONE_CALL       = 1 << 2;
    public static final int FOREGROUND_SERVICE_TYPE_LOCATION         = 1 << 3;
    public static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 1 << 4;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION = 1 << 5;
    public static final int FOREGROUND_SERVICE_TYPE_CAMERA           = 1 << 6;
    public static final int FOREGROUND_SERVICE_TYPE_MICROPHONE       = 1 << 7;
    public static final int FOREGROUND_SERVICE_TYPE_HEALTH           = 1 << 8;
    public static final int FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING = 1 << 9;
    public static final int FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED  = 1 << 10;
    public static final int FOREGROUND_SERVICE_TYPE_SHORT_SERVICE    = 1 << 11;
    public static final int FOREGROUND_SERVICE_TYPE_FILE_MANAGEMENT  = 1 << 12;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING = 1 << 13;
    public static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE      = 1 << 30;

    public ServiceInfo() {}

    /** AOSP ServiceInfo(ServiceInfo orig) (ServiceInfo.java:230-235): the copy
     *  constructor, chaining through ComponentInfo(ComponentInfo) so the whole
     *  inherited bag comes with it. Was missing, so `new ServiceInfo(other)`
     *  linked against nothing. */
    public ServiceInfo(ServiceInfo orig) {
        super(orig);
        permission = orig.permission;
        flags = orig.flags;
        mForegroundServiceType = orig.mForegroundServiceType;
    }

    /** AOSP ServiceInfo.getForegroundServiceType(). */
    public int getForegroundServiceType() { return mForegroundServiceType; }

    /** AOSP ServiceInfo.dump(Printer, String):237-239 -> the flags form at
     *  :241-246, verbatim (both lines are unconditional upstream, so a null
     *  permission prints as "permission=null" -- that is the real output). */
    public void dump(android.util.Printer pw, String prefix) {
        dump(pw, prefix, DUMP_FLAG_ALL);
    }

    void dump(android.util.Printer pw, String prefix, int dumpFlags) {
        super.dumpFront(pw, prefix);
        pw.println(prefix + "permission=" + permission);
        pw.println(prefix + "flags=0x" + Integer.toHexString(flags));
        super.dumpBack(pw, prefix, dumpFlags);
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `ServiceInfo extends ComponentInfo implements Parcelable`
    // (android11-release ServiceInfo.java:33). Order is AOSP
    // ServiceInfo.java:245-250; this tree declares every field it writes.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeString(permission);
        dest.writeInt(flags);
        dest.writeInt(mForegroundServiceType);
    }

    /** AOSP ServiceInfo.java:262-267. */
    private ServiceInfo(android.os.Parcel source) {
        super(source);
        permission = source.readString();
        flags = source.readInt();
        mForegroundServiceType = source.readInt();
    }

    public static final android.os.Parcelable.Creator<ServiceInfo> CREATOR =
        new android.os.Parcelable.Creator<ServiceInfo>() {
            @Override public ServiceInfo createFromParcel(android.os.Parcel source) {
                return new ServiceInfo(source);
            }
            @Override public ServiceInfo[] newArray(int size) {
                return new ServiceInfo[size];
            }
        };
}
