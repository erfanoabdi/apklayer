package android.app.sdksandbox;
import android.os.IBinder;
/** A handle to an SDK loaded into the SDK Runtime sandbox. API 33/34. Mac.apk
 *  runs no SDK sandbox, so an instance is only ever an opaque interface handle.
 *  Source: frameworks/base/sdksandbox/.../SandboxedSdk.java */
public final class SandboxedSdk {
    private final IBinder mInterface;
    public SandboxedSdk(IBinder sdkInterface) { mInterface = sdkInterface; }
    public IBinder getInterface() { return mInterface; }
}
