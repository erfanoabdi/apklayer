package hello.gps;

import android.app.Activity;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Follows the gps provider for 5 fixes (tests/vm/gnss.sh: GeoClue's fixes through the GNSS HAL, and two apps locating at once). hello-gps2 is the
 *  same code as another package. */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String tag = "[gps] " + getPackageName();
        LocationManager lm = getSystemService(LocationManager.class);
        System.out.println(tag + " gps=" + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        CountDownLatch fixes = new CountDownLatch(5);
        HandlerThread thread = new HandlerThread("gps");
        thread.start();
        LocationListener listener = l -> {
            System.out.println(String.format(java.util.Locale.ROOT, "%s fix %.5f %.5f accuracy %.1f", tag, l.getLatitude(), l.getLongitude(), l.getAccuracy()));
            fixes.countDown();
        };
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener, thread.getLooper());
        new Thread(() -> {
            boolean got = false;
            try { got = fixes.await(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            lm.removeUpdates(listener);
            System.out.println(tag + (got ? " PASS 5 fixes" : " FAIL " + (5 - fixes.getCount()) + " fixes"));
        }).start();
    }
}
