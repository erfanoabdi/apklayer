package hello.identity;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Runtime identity probes for the gap matrix (docs/runtime-identity-matrix.md): Java, native inspection and ART internals, one line each.
 * Same APK on Android (Waydroid), apklayer ARM64 and apklayer x86_64 (the arm64 library translated); tests/identity/collect.sh gathers them. */
public class Main extends Activity {
    static native String proc();
    static native String elf();
    static native String sys();
    static native String art(java.lang.reflect.Method a, java.lang.reflect.Method b, java.lang.reflect.Field f1, java.lang.reflect.Field f2,
                             long artA, long artB, int modifiersA);
    static native String jniThread();
    private String hidden() { return "no"; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Out.put("meta.abi", android.os.Build.SUPPORTED_ABIS[0] + " " + System.getProperty("os.arch"));
        Out.put("meta.sdk", android.os.Build.VERSION.SDK_INT + " " + android.os.Build.FINGERPRINT);
        JavaProbes.run(Main.class);
        ArtProbes.run();
        try {
            System.loadLibrary("hello");
            Out.putAll("native.proc", proc());
            Out.putAll("native.elf", elf());
            Out.putAll("native.sys", sys());
            long a = -1, b = -1;
            try { a = ArtProbes.artMethod(ArtProbes.Adjacent.class.getDeclaredMethod("a")); b = ArtProbes.artMethod(ArtProbes.Adjacent.class.getDeclaredMethod("b")); }
            catch (Throwable ignored) {}
            java.lang.reflect.Method ma = ArtProbes.Adjacent.class.getDeclaredMethod("a"), mb = ArtProbes.Adjacent.class.getDeclaredMethod("b");
            Out.putAll("native.art", art(ma, mb, ArtProbes.Adjacent.class.getDeclaredField("first"), ArtProbes.Adjacent.class.getDeclaredField("second"),
                    a, b, ma.getModifiers()));
            Out.putAll("native.jnithread", jniThread());
        } catch (Throwable t) {
            Out.put("native.error", t.toString());
        }
        Out.write(getFilesDir());
        System.out.println("[identity] END");
        TextView v = new TextView(this);
        v.setText("identity probes written");
        setContentView(v);
    }
}
