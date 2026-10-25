package apklayer.smoke;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Base64;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M1 smoke test: genuine AOSP classes (unmodified where possible) running on a stock JVM
 * on bare Linux.
 *   M1a - android.util.Base64 roundtrip (pure Java).
 *   M1b - real AOSP message loop: HandlerThread + Looper + MessageQueue + Handler
 *         (immediate post, sendMessage, delayed post).
 */
public final class Smoke {
    public static void main(String[] args) throws Exception {
        byte[] msg = "hello apklayer".getBytes("UTF-8");
        String enc = Base64.encodeToString(msg, Base64.NO_WRAP);
        boolean b64 = Arrays.equals(msg, Base64.decode(enc, Base64.NO_WRAP));

        final CountDownLatch latch = new CountDownLatch(3);
        final AtomicInteger got = new AtomicInteger();

        HandlerThread ht = new HandlerThread("apklayer-smoke");
        ht.start();
        Handler h = new Handler(ht.getLooper());
        h.post(() -> { got.incrementAndGet(); latch.countDown(); });
        Handler recv = new Handler(ht.getLooper()) {
            @Override public void handleMessage(Message m) {
                if (m.what == 42 && "obj".equals(m.obj)) {
                    got.incrementAndGet();
                    latch.countDown();
                }
            }
        };
        Message m = Message.obtain();
        m.what = 42;
        m.obj = "obj";
        recv.sendMessage(m);
        h.postDelayed(() -> { got.incrementAndGet(); latch.countDown(); }, 60);

        boolean loop = latch.await(5, TimeUnit.SECONDS) && got.get() == 3;
        ht.quitSafely();
        ht.join();

        System.out.println("apklayer M1 smoke: base64_ok=" + b64 + " looper_ok=" + loop
                + " jvm=" + System.getProperty("java.version"));
        if (!(b64 && loop)) System.exit(1);
    }
}
