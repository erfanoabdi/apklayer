package androidx.window.sidecar;
/** The reflective entry point androidx.window's SidecarCompat loads. Returns a
 *  flat-device SidecarInterface. */
public final class SidecarProvider {
    // ⚠ v1.0.939: BOTH METHODS ARE STATIC. They were instance methods, and that
    // made this entire shim dead code.
    //
    // AndroidX's real androidx.window.sidecar.SidecarProvider stub declares
    //      public static SidecarInterface getSidecarImpl(Context)
    //      public static String getApiVersion()
    // so SidecarCompat compiles them as invokestatic (and its reflective
    // validator additionally checks Modifier.isStatic). Against an INSTANCE
    // method that is a NoSuchMethodError -- which SidecarCompat catches and
    // turns into "no sidecar available". So we shipped a complete, working
    // SidecarInterface below and androidx never once reached it, silently, with
    // no log line. The class existing was not enough; the signature is the
    // contract.
    //
    // Found by cross-checking the reference AVD's own library list: it declares
    // androidx.window.sidecar, we claim to provide it, and the claim did not
    // survive reading the signature androidx actually calls.
    public SidecarProvider() {}
    public static String getApiVersion() { return "1.0.0"; }
    public static SidecarInterface getSidecarImpl(android.content.Context context) {
        return new SidecarInterface() {
            @Override public void setSidecarCallback(SidecarCallback callback) { }
            @Override public SidecarWindowLayoutInfo getWindowLayoutInfo(android.os.IBinder windowToken) {
                return new SidecarWindowLayoutInfo();
            }
            @Override public void onWindowLayoutChangeListenerAdded(android.os.IBinder windowToken) { }
            @Override public void onWindowLayoutChangeListenerRemoved(android.os.IBinder windowToken) { }
            @Override public SidecarDeviceState getDeviceState() { return new SidecarDeviceState(); }
            @Override public void onDeviceStateListenersChanged(boolean isEmpty) { }
        };
    }
}
