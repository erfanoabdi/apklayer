package hello.hardware;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** The hardware providers through Android's APIs (tests/vm/providers.sh compares each line with the instance's fake backends): a vibration
 *  pattern, a GPS fix, accelerometer events at a rate. */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            boolean ok = true;
            try { ok &= vibrator(); } catch (Throwable failure) { failure.printStackTrace(System.out); ok = false; }
            try { ok &= location(); } catch (Throwable failure) { failure.printStackTrace(System.out); ok = false; }
            try { sensors(); } catch (Throwable failure) { failure.printStackTrace(System.out); }
            System.out.println(ok ? "[hw] PASS" : "[hw] FAIL");
        }).start();
    }

    private boolean vibrator() throws InterruptedException {
        Vibrator vibrator = getSystemService(Vibrator.class);
        VibratorManager manager = getSystemService(VibratorManager.class);
        System.out.println("[hw] vibrator has=" + vibrator.hasVibrator() + " ids=" + manager.getVibratorIds().length + " amplitude=" + vibrator.hasAmplitudeControl());
        if (!vibrator.hasVibrator()) return false;
        vibrator.vibrate(VibrationEffect.createWaveform(new long[] {0, 100, 50, 200}, new int[] {0, 255, 0, 128}, -1));
        Thread.sleep(500);  // the pattern is 350 ms
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        Thread.sleep(300);
        System.out.println("[hw] vibrator pattern and click played");
        return true;
    }

    private boolean location() throws InterruptedException {
        LocationManager lm = getSystemService(LocationManager.class);
        System.out.println("[hw] location enabled=" + lm.isLocationEnabled() + " gps=" + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false;
        CountDownLatch fixed = new CountDownLatch(1);
        AtomicReference<Location> fix = new AtomicReference<>();
        HandlerThread thread = new HandlerThread("gps");
        thread.start();
        LocationListener listener = location -> { fix.set(location); fixed.countDown(); };
        new Handler(thread.getLooper()).post(() -> lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener, thread.getLooper()));
        boolean got = fixed.await(15, TimeUnit.SECONDS);
        lm.removeUpdates(listener);
        thread.quitSafely();
        Location l = fix.get();
        System.out.println(got ? String.format(java.util.Locale.ROOT, "[hw] gps fix %.5f %.5f accuracy %.1f", l.getLatitude(), l.getLongitude(), l.getAccuracy()) : "[hw] gps no fix");
        return got;
    }

    private void sensors() throws InterruptedException {
        SensorManager sm = getSystemService(SensorManager.class);
        Sensor accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        System.out.println("[hw] sensors " + sm.getSensorList(Sensor.TYPE_ALL).size() + " accelerometer=" + (accel == null ? null : accel.getName()));
        if (accel == null) return;
        AtomicInteger events = new AtomicInteger();
        AtomicReference<float[]> last = new AtomicReference<>();
        HandlerThread thread = new HandlerThread("sensors");
        thread.start();
        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) { events.incrementAndGet(); last.set(event.values.clone()); }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sm.registerListener(listener, accel, 20000, new Handler(thread.getLooper()));  // 50 Hz
        Thread.sleep(2000);
        sm.unregisterListener(listener);
        thread.quitSafely();
        float[] v = last.get();
        System.out.println(String.format(java.util.Locale.ROOT, "[hw] accelerometer %d events in 2 s, last %s", events.get(),
                v == null ? "none" : String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", v[0], v[1], v[2])));
    }
}
