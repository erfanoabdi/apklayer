package apklayer.smoke;

import java.lang.reflect.Method;

/**
 * M2e frame smoke: drive the NativeCocoa host seam (via reflection; the class may be
 * package-private) with the Linux JNI bridge loaded, present one Java-drawn ARGB frame,
 * pump the loop ~2s. Exit 0 iff init + present succeeded.
 *
 * Usage: java -Dapklayer.jni=<libapklayer-jni.so> -cp out_mac:. apklayer.smoke.FrameSmoke
 */
public final class FrameSmoke {
    public static void main(String[] args) throws Exception {
        String jni = System.getProperty("apklayer.jni");
        if (jni != null) System.load(jni);

        Class<?> nc = Class.forName("com.macapk.loader.NativeCocoa");
        Method init = nc.getMethod("init");
        Method width = nc.getMethod("phoneWidth");
        Method height = nc.getMethod("phoneHeight");
        Method present = nc.getMethod("presentBitmap", int[].class, int.class, int.class);
        Method pump = nc.getMethod("runLoop");

        boolean ok = (Boolean) init.invoke(null);
        if (!ok) {
            System.out.println("FRAME init=FAIL");
            System.exit(1);
        }
        int w = (Integer) width.invoke(null);
        int h = (Integer) height.invoke(null);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 255 - x * 200 / Math.max(1, w);
                int g = 60 + y * 160 / Math.max(1, h);
                px[y * w + x] = 0xff000000 | (r << 16) | (g << 8) | 0x80;
            }
        }
        present.invoke(null, px, w, h);
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 2000) {
            pump.invoke(null);
            Thread.sleep(16);
        }
        System.out.println("FRAME init=ok size=" + w + "x" + h + " presented=1");
    }
}
