package apklayer.smoke;

import android.util.Base64;

/** M1 smoke test: genuine AOSP classes (unmodified) running on a stock JVM on bare Linux. */
public final class Smoke {
    public static void main(String[] args) throws Exception {
        byte[] msg = "hello apklayer".getBytes("UTF-8");
        String enc = Base64.encodeToString(msg, Base64.NO_WRAP);
        byte[] back = Base64.decode(enc, Base64.NO_WRAP);
        boolean ok = java.util.Arrays.equals(msg, back);
        System.out.println("apklayer M1 smoke: base64=" + enc + " roundtrip_ok=" + ok
                + " jvm=" + System.getProperty("java.version"));
        if (!ok) System.exit(1);
    }
}
