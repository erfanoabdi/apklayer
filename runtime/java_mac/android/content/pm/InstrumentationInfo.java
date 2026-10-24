// android.content.pm.InstrumentationInfo -- metadata for an
// <instrumentation> declared in a manifest (test runners). Referenced by
// PackageInfo.instrumentation and by PackageManager.getInstrumentationInfo.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/pm/InstrumentationInfo.java
// AOSP: InstrumentationInfo extends PackageItemInfo. Mac.apk does not run
// instrumentation tests, but the class must exist (and carry its public
// fields) so apps/SDKs that enumerate it do not hit NoClassDefFoundError /
// NoSuchFieldError.
package android.content.pm;

public class InstrumentationInfo extends PackageItemInfo implements android.os.Parcelable {
    public String targetPackage;
    public String targetProcesses;
    public String sourceDir;
    public String publicSourceDir;
    public String[] splitNames;
    public String[] splitSourceDirs;
    public String[] splitPublicSourceDirs;
    public String dataDir;
    public boolean handleProfiling;
    public boolean functionalTest;

    public InstrumentationInfo() {}

    public InstrumentationInfo(InstrumentationInfo orig) {
        name = orig.name;
        packageName = orig.packageName;
        targetPackage = orig.targetPackage;
        targetProcesses = orig.targetProcesses;
        sourceDir = orig.sourceDir;
        publicSourceDir = orig.publicSourceDir;
        splitNames = orig.splitNames;
        splitSourceDirs = orig.splitSourceDirs;
        splitPublicSourceDirs = orig.splitPublicSourceDirs;
        dataDir = orig.dataDir;
        handleProfiling = orig.handleProfiling;
        functionalTest = orig.functionalTest;
    }

    @Override public String toString() {
        return "InstrumentationInfo{" + name + " -> " + targetPackage + "}";
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `InstrumentationInfo extends PackageItemInfo implements Parcelable`
    // (android11-release InstrumentationInfo.java:31). Order is AOSP
    // InstrumentationInfo.java:158-177 restricted to the fields this tree
    // declares: splitDependencies, deviceProtectedDataDir,
    // credentialProtectedDataDir, primaryCpuAbi, secondaryCpuAbi,
    // nativeLibraryDir and secondaryNativeLibraryDir are not modelled here.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeString(targetPackage);
        dest.writeString(targetProcesses);
        dest.writeString(sourceDir);
        dest.writeString(publicSourceDir);
        dest.writeStringArray(splitNames);
        dest.writeStringArray(splitSourceDirs);
        dest.writeStringArray(splitPublicSourceDirs);
        dest.writeString(dataDir);
        dest.writeInt(handleProfiling ? 1 : 0);
        dest.writeInt(functionalTest ? 1 : 0);
    }

    /** AOSP InstrumentationInfo.java:190-209, same subset and order. */
    private InstrumentationInfo(android.os.Parcel source) {
        super(source);
        targetPackage = source.readString();
        targetProcesses = source.readString();
        sourceDir = source.readString();
        publicSourceDir = source.readString();
        splitNames = source.createStringArray();
        splitSourceDirs = source.createStringArray();
        splitPublicSourceDirs = source.createStringArray();
        dataDir = source.readString();
        handleProfiling = source.readInt() != 0;
        functionalTest = source.readInt() != 0;
    }

    public static final android.os.Parcelable.Creator<InstrumentationInfo> CREATOR =
        new android.os.Parcelable.Creator<InstrumentationInfo>() {
            @Override public InstrumentationInfo createFromParcel(android.os.Parcel source) {
                return new InstrumentationInfo(source);
            }
            @Override public InstrumentationInfo[] newArray(int size) {
                return new InstrumentationInfo[size];
            }
        };
}
