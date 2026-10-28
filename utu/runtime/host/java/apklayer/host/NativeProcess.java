package apklayer.host;

import org.robolectric.shadows.ShadowProcess;

/** Real process identity when Java clients participate in native kernel binder. */
final class NativeProcess {
    private NativeProcess() {}
    static void install() {
        ShadowProcess.setPid(Math.toIntExact(ProcessHandle.current().pid()));
        ShadowProcess.setUid(Math.toIntExact(new com.sun.security.auth.module.UnixSystem().getUid()));
    }
}
