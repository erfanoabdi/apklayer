package android.content.pm;
/** AIDL callback delivering PackageStats. Mac.apk computes stats in-process. */
public interface IPackageStatsObserver {
    void onGetStatsCompleted(PackageStats pStats, boolean succeeded);
    abstract class Stub implements IPackageStatsObserver {
        public Stub() {}
        public android.os.IBinder asBinder() { return null; }
        public static IPackageStatsObserver asInterface(android.os.IBinder obj) { return null; }
    }
}
