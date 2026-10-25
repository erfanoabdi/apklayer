// android.content.pm.SharedLibraryInfo -- one entry in the list
// PackageManager.getSharedLibraries(int) returns. AOSP API-26+.
//
// Shape mirrored from android14-release
// core/java/android/content/pm/SharedLibraryInfo.java: the four TYPE_
// constants and VERSION_UNDEFINED are verbatim, and the accessor set is the
// public surface an app can actually reach.
//
// WHY THIS EXISTS (2026-08-19): Aurora Store's InstallerStatusReceiver -- the
// manifest-declared receiver that learns an install finished -- calls
// PackageManager.getSharedLibraries(int) while handling the status broadcast.
// We had neither this class nor that method, so the receiver threw
//     NoSuchMethodError: 'java.util.List
//      android.content.pm.PackageManager.getSharedLibraries(int)'
// AFTER a completely successful install, before it reached the code that
// records the app as installed. The install was perfect -- helper exit 0,
// status=0 INSTALL_SUCCEEDED, real icon staged -- and the store still showed
// "installing" forever, because the one method that would have ended that
// state was missing. Measured on TikTok (com.zhiliaoapp.musically 2024604030).
//
// Note this only became reachable once component-less broadcasts started
// reaching MANIFEST receivers (the install-completion contract,
// docs/install-completion-contract_2026-08-18.md). Before that the receiver
// was never invoked at all, so the missing method could not be observed --
// a fix exposing the next defect, which is the normal shape of this work.
package android.content.pm;

import java.util.Collections;
import java.util.List;

public final class SharedLibraryInfo implements android.os.Parcelable {

    /** AOSP: "shared library type: builtin. This library is a part of the
     *  platform and is declared in platform.xml" -- i.e. it lives in a jar on
     *  the framework classpath rather than in an apk. */
    public static final int TYPE_BUILTIN = 0;
    /** AOSP: declared by an apk, resolved by name, single version. */
    public static final int TYPE_DYNAMIC = 1;
    /** AOSP: declared by an apk, versioned, multiple versions can coexist. */
    public static final int TYPE_STATIC = 2;
    /** AOSP API-31+: an SDK shipped as a package. */
    public static final int TYPE_SDK_PACKAGE = 3;

    /** AOSP: the version of a builtin/dynamic library is undefined -- only
     *  static libraries carry a real one. */
    public static final int VERSION_UNDEFINED = -1;

    private final String name;
    private final int type;
    private final long version;
    private final String path;
    private final VersionedPackage declaringPackage;

    public SharedLibraryInfo(String name, int type) {
        this(name, type, VERSION_UNDEFINED, null, null);
    }

    public SharedLibraryInfo(String name, int type, long version,
                             String path, VersionedPackage declaringPackage) {
        this.name = name;
        this.type = type;
        this.version = version;
        this.path = path;
        this.declaringPackage = declaringPackage;
    }

    public String getName() { return name; }

    public int getType() { return type; }

    /** AOSP: "For static libraries this is the declared version and for
     *  dynamic and builtin it is VERSION_UNDEFINED as these cannot be
     *  versioned." */
    public long getLongVersion() { return version; }

    /** AOSP deprecates this in favour of getLongVersion(); kept because app
     *  bytecode compiled against an older SDK still calls it. */
    @Deprecated
    public int getVersion() {
        return version <= Integer.MAX_VALUE ? (int) version : -1;
    }

    /** AOSP: "Only libraries with TYPE_BUILTIN are in jar files." Null for a
     *  builtin whose jar path we do not surface -- see getSharedLibraries in
     *  MacApkPackageManager for why we do not invent one. */
    public String getPath() { return path; }

    /** AOSP: the package that declares a dynamic/static library. A builtin is
     *  declared by the platform, not by a package, so this is null -- which is
     *  what a device reports for the platform libraries too. */
    public VersionedPackage getDeclaringPackage() { return declaringPackage; }

    public boolean isBuiltin() { return type == TYPE_BUILTIN; }
    public boolean isDynamic() { return type == TYPE_DYNAMIC; }
    public boolean isStatic()  { return type == TYPE_STATIC; }
    public boolean isSdk()     { return type == TYPE_SDK_PACKAGE; }

    /** AOSP: the jars/apks backing this library. Empty rather than null,
     *  matching AOSP's non-null contract for the accessor. */
    public List<String> getAllCodePaths() {
        return path == null ? Collections.<String>emptyList()
                            : Collections.singletonList(path);
    }

    /** AOSP: libraries this one depends on, or null when there are none. The
     *  platform libraries we report are self-contained. */
    public List<SharedLibraryInfo> getDependencies() { return null; }

    /** AOSP: packages that depend on this library. We do not track reverse
     *  dependencies; AOSP itself returns an empty list when nothing does. */
    public List<VersionedPackage> getDependentPackages() {
        return Collections.<VersionedPackage>emptyList();
    }

    /** AOSP SharedLibraryInfo.getOptionalDependentPackages() (API 35,
     *  SharedLibraryInfo.java:458-463): of the dependent packages, the subset
     *  that declares `<uses-sdk-library ... optional="true">`. Only ever
     *  non-empty for a TYPE_SDK_PACKAGE library.
     *
     *  We track no reverse dependencies at all (getDependentPackages above is
     *  already empty), so the optional subset is necessarily empty too -- and
     *  AOSP returns the same empty list whenever the field is null, which is
     *  its state for every non-SDK library on a device. @NonNull upstream. */
    public List<VersionedPackage> getOptionalDependentPackages() {
        return Collections.<VersionedPackage>emptyList();
    }

    /** AOSP SharedLibraryInfo.getCertDigests() (API 35,
     *  SharedLibraryInfo.java:471-476): the SHA-256 certificate digests an SDK
     *  library is signed with, used by an app to pin the library it links.
     *
     *  This class carries no certificate state -- our libraries are all
     *  TYPE_BUILTIN entries backed by code inside this process, which is signed
     *  as part of Mac.apk itself and not as a separate library package -- so
     *  the honest answer is the empty list, which is exactly what AOSP returns
     *  for every library whose digests were never recorded (all builtin and
     *  dynamic ones). @NonNull upstream, so this must not be null. */
    public List<String> getCertDigests() {
        return Collections.<String>emptyList();
    }

    @Override public String toString() {
        return "SharedLibraryInfo{name:" + name + ", type:" + type
             + ", version:" + version + "}";
    }

    // 2026-08-23 conformance sweep: AOSP's SharedLibraryInfo carries several
    // fields we do not track (packageName, codePaths list, isNative,
    // certDigests, optionalDependentPackages -- SharedLibraryInfo.java
    // L94-108/491-508), so this writes our own flat (name, type, version,
    // path, declaringPackage) fields in a self-consistent order rather than
    // AOSP's exact wire shape. Absent fields are simply absent, not padded.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(type);
        dest.writeLong(version);
        dest.writeString(path);
        dest.writeParcelable(declaringPackage, flags);
    }

    public static final android.os.Parcelable.Creator<SharedLibraryInfo> CREATOR =
            new android.os.Parcelable.Creator<SharedLibraryInfo>() {
        @Override public SharedLibraryInfo createFromParcel(android.os.Parcel in) {
            String name = in.readString();
            int type = in.readInt();
            long version = in.readLong();
            String path = in.readString();
            VersionedPackage declaringPackage = in.readParcelable(
                    SharedLibraryInfo.class.getClassLoader());
            return new SharedLibraryInfo(name, type, version, path, declaringPackage);
        }
        @Override public SharedLibraryInfo[] newArray(int size) { return new SharedLibraryInfo[size]; }
    };
}
