package android.hidl.base.V1_0;

/** Compile-only stand-in (tests/apps/build.sh: compile-only/ is on the classpath, never in the APK): the framework carries the real IBase and its generated Stub. */
public interface IBase {
    static IBase getService(String serviceName) throws android.os.RemoteException { return null; }

    abstract class Stub implements IBase {
        public final void registerAsService(String serviceName) throws android.os.RemoteException {}
    }
}
