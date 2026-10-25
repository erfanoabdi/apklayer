// android.content.pm.PermissionInfo -- metadata for a single permission
// declared by an app (<permission> in the manifest) or queried from the
// system. SDKs read protectionLevel/getProtection() to decide whether a
// permission is install-time, runtime-dangerous, or signature-gated.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/pm/PermissionInfo.java
// AOSP: PermissionInfo extends PackageItemInfo. The inheritance is
// load-bearing for the same reason as the other *Info classes (metaData /
// name read via getfield on the PackageItemInfo declaring class). Previously
// a bare stub -> any field/method access NoSuchFieldError/NoSuchMethodError.
package android.content.pm;

public class PermissionInfo extends PackageItemInfo implements android.os.Parcelable {
    /** @deprecated use getProtection()/getProtectionFlags(). The combined
     *  protection level + flags as a single int (AOSP keeps this for
     *  back-compat; many SDKs still read it directly). */
    @Deprecated
    public int protectionLevel;
    public String group;
    public int flags;
    public int descriptionRes;
    public int requestRes;
    public String backgroundPermission;
    public CharSequence nonLocalizedDescription;
    /** AOSP PermissionInfo.knownCerts (@SystemApi): the signing-certificate
     *  digests a PROTECTION_FLAG_KNOWN_SIGNER permission trusts. AOSP declares
     *  it @NonNull and pre-initializes it to Collections.emptySet(), so callers
     *  iterate it without a null check; the copy constructor carries it. */
    public java.util.Set<String> knownCerts = java.util.Collections.emptySet();

    // ---- protection levels (low byte of protectionLevel) ----
    public static final int PROTECTION_NORMAL              = 0;
    public static final int PROTECTION_DANGEROUS           = 1;
    public static final int PROTECTION_SIGNATURE           = 2;
    /** @deprecated */
    @Deprecated
    public static final int PROTECTION_SIGNATURE_OR_SYSTEM = 3;
    public static final int PROTECTION_INTERNAL            = 4;

    // ---- protection flags (upper bits of protectionLevel) ----
    public static final int PROTECTION_FLAG_PRIVILEGED            = 0x10;
    /** @deprecated */
    @Deprecated
    public static final int PROTECTION_FLAG_SYSTEM               = 0x10;
    public static final int PROTECTION_FLAG_DEVELOPMENT          = 0x20;
    public static final int PROTECTION_FLAG_APPOP                = 0x40;
    public static final int PROTECTION_FLAG_PRE23                = 0x80;
    public static final int PROTECTION_FLAG_INSTALLER            = 0x100;
    public static final int PROTECTION_FLAG_VERIFIER             = 0x200;
    public static final int PROTECTION_FLAG_PREINSTALLED         = 0x400;
    public static final int PROTECTION_FLAG_SETUP                = 0x800;
    public static final int PROTECTION_FLAG_INSTANT              = 0x1000;
    public static final int PROTECTION_FLAG_RUNTIME_ONLY         = 0x2000;
    public static final int PROTECTION_FLAG_OEM                  = 0x4000;
    public static final int PROTECTION_FLAG_VENDOR_PRIVILEGED    = 0x8000;
    public static final int PROTECTION_FLAG_SYSTEM_TEXT_CLASSIFIER = 0x10000;
    public static final int PROTECTION_FLAG_CONFIGURATOR         = 0x80000;
    public static final int PROTECTION_FLAG_INCIDENT_REPORT_APPROVER = 0x100000;
    public static final int PROTECTION_FLAG_APP_PREDICTOR        = 0x200000;
    public static final int PROTECTION_FLAG_COMPANION            = 0x800000;
    public static final int PROTECTION_FLAG_RETAIL_DEMO          = 0x1000000;
    public static final int PROTECTION_FLAG_RECENTS              = 0x2000000;
    public static final int PROTECTION_FLAG_ROLE                 = 0x4000000;
    public static final int PROTECTION_FLAG_KNOWN_SIGNER         = 0x8000000;

    /** @deprecated mask of the base protection level. */
    @Deprecated
    public static final int PROTECTION_MASK_BASE  = 0xf;
    /** @deprecated mask of the protection flags. */
    @Deprecated
    public static final int PROTECTION_MASK_FLAGS = 0xfff0;

    // ---- permission flags ----
    public static final int FLAG_COSTS_MONEY          = 1 << 0;
    public static final int FLAG_REMOVED              = 1 << 1;
    public static final int FLAG_HARD_RESTRICTED      = 1 << 2;
    public static final int FLAG_SOFT_RESTRICTED      = 1 << 3;
    public static final int FLAG_IMMUTABLY_RESTRICTED = 1 << 4;
    public static final int FLAG_INSTALLED            = 1 << 30;

    public PermissionInfo() {}

    /** AOSP PermissionInfo(PermissionInfo orig): a full field-by-field clone.
     *  Any app that clones a PermissionInfo (DEX descriptor
     *  (Landroid/content/pm/PermissionInfo;)V) got NoSuchMethodError without it.
     *
     *  AOSP's body is `super(orig)` plus its own 8 fields, where the super is
     *  PackageItemInfo(PackageItemInfo). Our PackageItemInfo has no copy
     *  constructor yet (see the followup), so the base fields are copied inline
     *  here with exactly the semantics AOSP's base copy constructor has: name
     *  and nonLocalizedLabel are trim()ed, and metaData is carried by REFERENCE
     *  (AOSP does not deep-copy the Bundle, so a clone observing the original's
     *  meta-data is the contract, and mutating one is visible through the other).
     *  banner/logo/metaData/showUserIcon must come along or a clone silently
     *  loses them. */
    public PermissionInfo(PermissionInfo orig) {
        // PackageItemInfo(PackageItemInfo orig) fields.
        name = orig.name;
        if (name != null) name = name.trim();
        packageName = orig.packageName;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        if (nonLocalizedLabel != null) {
            nonLocalizedLabel = nonLocalizedLabel.toString().trim();
        }
        icon = orig.icon;
        banner = orig.banner;
        logo = orig.logo;
        metaData = orig.metaData;
        showUserIcon = orig.showUserIcon;
        // PermissionInfo's own fields.
        protectionLevel = orig.protectionLevel;
        flags = orig.flags;
        group = orig.group;
        backgroundPermission = orig.backgroundPermission;
        descriptionRes = orig.descriptionRes;
        requestRes = orig.requestRes;
        nonLocalizedDescription = orig.nonLocalizedDescription;
        knownCerts = orig.knownCerts;
    }

    /** AOSP PermissionInfo.getProtection(): the base protection level
     *  (PROTECTION_NORMAL/DANGEROUS/SIGNATURE/INTERNAL). */
    public int getProtection() {
        return protectionLevel & PROTECTION_MASK_BASE;
    }

    /** AOSP PermissionInfo.getProtectionFlags(): the protection flag bits. */
    public int getProtectionFlags() {
        return protectionLevel & ~PROTECTION_MASK_BASE;
    }

    public CharSequence loadDescription(PackageManager pm) {
        return nonLocalizedDescription;
    }

    @Override public String toString() {
        return "PermissionInfo{" + name + " level=0x"
            + Integer.toHexString(protectionLevel) + "}";
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `PermissionInfo extends PackageItemInfo implements Parcelable`
    // (android11-release PermissionInfo.java:52). Order is AOSP
    // PermissionInfo.java:617-626, plus knownCerts last, which AOSP main added
    // at the same position (PermissionInfo.java:746).
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeInt(protectionLevel);
        dest.writeInt(flags);
        dest.writeString(group);
        dest.writeString(backgroundPermission);
        dest.writeInt(descriptionRes);
        dest.writeInt(requestRes);
        android.text.TextUtils.writeToParcel(nonLocalizedDescription, dest, parcelableFlags);
        // AOSP uses Parcelling.BuiltIn.ForStringSet, whose wire form is a count
        // (-1 for null) followed by that many strings -- byte-for-byte what
        // writeStringArray emits. This tree has no Parcelling registry, so the
        // set is flattened through the array primitive and rebuilt below.
        dest.writeStringArray(knownCerts == null
                ? null : knownCerts.toArray(new String[0]));
    }

    /** AOSP PermissionInfo.java:677-686 plus the knownCerts read AOSP main does
     *  at PermissionInfo.java:807. */
    private PermissionInfo(android.os.Parcel source) {
        super(source);
        protectionLevel = source.readInt();
        flags = source.readInt();
        group = source.readString();
        backgroundPermission = source.readString();
        descriptionRes = source.readInt();
        requestRes = source.readInt();
        nonLocalizedDescription =
            android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        String[] certs = source.createStringArray();
        // AOSP declares knownCerts @NonNull and defaults it to an empty set, so
        // a null on the wire must not resurface as a null field -- callers
        // iterate it without a guard. ArraySet is AOSP's own container here.
        knownCerts = (certs == null)
                ? java.util.Collections.<String>emptySet()
                : new android.util.ArraySet<String>(java.util.Arrays.asList(certs));
    }

    public static final android.os.Parcelable.Creator<PermissionInfo> CREATOR =
        new android.os.Parcelable.Creator<PermissionInfo>() {
            @Override public PermissionInfo createFromParcel(android.os.Parcel source) {
                return new PermissionInfo(source);
            }
            @Override public PermissionInfo[] newArray(int size) {
                return new PermissionInfo[size];
            }
        };
}
