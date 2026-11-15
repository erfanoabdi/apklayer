package hello.heap;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import java.util.ArrayList;

/** Heap limits: what Runtime and ActivityManager report, then 16 MB arrays are held until the VM refuses (OutOfMemoryError) or the target (-Dhello.heapmb, default 700) is held. Prints "[heap] ..." lines, "[heap] END". */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Thread(() -> {
            Runtime r = Runtime.getRuntime();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            System.out.println("[heap] maxMemory " + (r.maxMemory() >> 20) + " MB, memoryClass " + am.getMemoryClass() + ", largeMemoryClass " + am.getLargeMemoryClass());
            long target = Long.getLong("hello.heapmb", 700);
            ArrayList<byte[]> held = new ArrayList<>();
            long mb = 0;
            try {
                while (mb < target) { byte[] a = new byte[16 << 20]; for (int i = 0; i < a.length; i += 4096) a[i] = 1; held.add(a); mb += 16; }
                System.out.println("[heap] PASS held " + mb + " MB, total " + (r.totalMemory() >> 20) + " MB");
            } catch (OutOfMemoryError e) { System.out.println("[heap] OOM after " + mb + " MB, total " + (r.totalMemory() >> 20) + " MB"); }
            System.out.println("[heap] END " + held.size());
            System.out.flush();
            finish();
        }).start();
    }
}
