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
        if (Boolean.getBoolean("hw.compose")) compose(vibrator);  // -Dhw.compose=true: tests/vm/providers.sh (the host backends' tests read only the waveform and click)
        System.out.println("[hw] vibrator pattern and click played");
        return true;
    }

    private void compose(Vibrator vibrator) throws InterruptedException {
        // a composition of two primitives (the HAL composes: the fake backend gets CLICK at 0.5, then THUD at 1.0), then haptic feedback (CONFIRM: a click, as the example HAL has only CLICK and TICK)
        boolean primitives = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_THUD);
        System.out.println("[hw] vibrator primitives supported=" + primitives);
        if (primitives) vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 50).compose());
        Thread.sleep(600);
        // SystemVibrator.performHapticFeedback (hidden: ViewRootImpl's path; Robolectric's ShadowView answers View.performHapticFeedback(int) itself, so an app's View never gets here)
        try {
            Vibrator.class.getMethod("performHapticFeedback", int.class, int.class, String.class, int.class, int.class).invoke(vibrator, android.view.HapticFeedbackConstants.CONFIRM, 0, "hello", 0, 0);
        } catch (ReflectiveOperationException failure) { failure.printStackTrace(System.out); }
        Thread.sleep(400);
        System.out.println("[hw] vibrator haptic feedback sent");
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
        for (String f : new String[] {"accelerometer", "light", "proximity", "compass", "gyroscope"})  // declared from the backend's sensors (sensor_features)
            System.out.println("[hw] feature sensor." + f + "=" + getPackageManager().hasSystemFeature("android.hardware.sensor." + f));
        for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {  // every sensor's range and a value (the host backends' mapping: tests/vm/providers-host.sh)
            System.out.println("[hw] listening type=" + s.getType() + " name=" + s.getName());
            float[] v = listen(sm, s, 1000);
            System.out.println(String.format(java.util.Locale.ROOT, "[hw] sensor type=%d name=%s max=%.2f mindelay=%d value=%s", s.getType(), s.getName(),
                s.getMaximumRange(), s.getMinDelay(), v == null ? "none" : String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", v[0], v.length > 1 ? v[1] : 0f, v.length > 2 ? v[2] : 0f)));
        }
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

    /** the last event of a sensor within ms (an on-change sensor sends its current value when registered) */
    private static float[] listen(SensorManager sm, Sensor sensor, long ms) throws InterruptedException {
        AtomicReference<float[]> last = new AtomicReference<>();
        HandlerThread thread = new HandlerThread("sensor");
        thread.start();
        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) { last.set(event.values.clone()); }
            @Override public void onAccuracyChanged(Sensor s, int accuracy) {}
        };
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, new Handler(thread.getLooper()));
        Thread.sleep(ms);
        sm.unregisterListener(listener);
        thread.quitSafely();
        return last.get();
    }
}
