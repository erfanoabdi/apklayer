package android.os.flagging;
/** Android 16 fast read-only flag storage. Mac.apk ships no aconfig storage, so
 *  every flag reports its caller-supplied default. */
public class AconfigPackage {
    private AconfigPackage() {}
    public static AconfigPackage load(String packageName) { return new AconfigPackage(); }
    public boolean getBooleanFlagValue(String flagName, boolean defaultValue) { return defaultValue; }
}
