package apklayer.host;

import android.media.MediaCodec;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** apklayer patch: real Java MediaCodec and device JNI; defer only native library setup. */
@Implements(value = MediaCodec.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeMediaCodec {
    @RealObject private MediaCodec real;
    @Implementation protected static void __staticInitializer__() { /* registered by runtime shim */ }
    @Implementation protected void native_setup(String name, boolean type, boolean encoder, int pid, int uid) {
        NativeMedia.ensureInitialized();
        ReflectionHelpers.callInstanceMethod(real,
                Shadow.directNativeMethodName(MediaCodec.class.getName(), "native_setup"),
                ClassParameter.from(String.class, name), ClassParameter.from(boolean.class, type),
                ClassParameter.from(boolean.class, encoder), ClassParameter.from(int.class, pid),
                ClassParameter.from(int.class, uid));
    }

    private static final boolean PERF = Boolean.getBoolean("apklayer.perf");
    private long renders, windowStart = System.nanoTime();

    /** -Dapklayer.perf=true: a line per 60 rendered frames with the wall time and how far ahead of / behind the system clock the render time is. */
    @Implementation protected void releaseOutputBuffer(int index, boolean render, boolean updatePTS, long timeNs) {
        if (PERF && render && ++renders % 60 == 0) {
            long now = System.nanoTime();
            System.out.println("[apklayer] codec render: 60 frames in " + (now - windowStart) / 1_000_000 + " ms (render time " + (timeNs - now) / 1_000_000 + " ms ahead)");
            windowStart = now;
        }
        ReflectionHelpers.callInstanceMethod(real, Shadow.directNativeMethodName(MediaCodec.class.getName(), "releaseOutputBuffer"),
                ClassParameter.from(int.class, index), ClassParameter.from(boolean.class, render),
                ClassParameter.from(boolean.class, updatePTS), ClassParameter.from(long.class, timeNs));
    }
}
