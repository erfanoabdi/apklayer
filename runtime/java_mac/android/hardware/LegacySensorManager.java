// android.hardware.LegacySensorManager -- the bridge that lets the ORIGINAL
// (API 1) SensorListener API keep working on top of the modern
// SensorEventListener plumbing. Package-private, exactly as in AOSP: apps
// reach it only through SensorManager.getSensors() /
// registerListener(SensorListener, ...) / unregisterListener(SensorListener).
//
// Mirrors frameworks/base/core/java/android/hardware/LegacySensorManager.java
// (android16-release), including LegacyListener's axis conventions and the
// LmsFilter that smooths the legacy yaw channel.
//
// WHY IT IS IMPLEMENTED FOR REAL rather than stubbed. The legacy API is not a
// thin alias for the modern one -- it differs in three ways an app can see, and
// every one of them would be wrong if these methods just returned false:
//   * the SENSOR value it passes back is a SENSOR_* BITMASK, not a TYPE_*;
//   * the axes are NEGATED relative to the modern convention (AOSP
//     mapSensorDataToWindow), and re-mapped again for a rotated window;
//   * SENSOR_ORIENTATION's yaw is least-mean-squares filtered and predicted
//     forward, while SENSOR_ORIENTATION_RAW is not -- an app that registers
//     both gets two different value streams from ONE underlying sensor.
// A stub would leave an old compass or tilt app silently receiving nothing.
//
// The one deliberate divergence is the window rotation. AOSP subscribes an
// IRotationWatcher to WindowManager and caches the result; there is no window
// service here, so getRotation() reads the same landscape-vs-portrait signal
// that android.view.Display.getRotation() reads (NativeCocoa.phoneWidth vs
// phoneHeight). That keeps the legacy axis mapping consistent with what the
// rest of the framework tells the app the rotation is, which matters more than
// matching AOSP's transport.
package android.hardware;

import android.view.Surface;

import java.util.HashMap;
import java.util.List;

final class LegacySensorManager {

    private final SensorManager mSensorManager;

    private final HashMap<SensorListener, LegacyListener> mLegacyListenersMap =
            new HashMap<SensorListener, LegacyListener>();

    LegacySensorManager(SensorManager sensorManager) {
        mSensorManager = sensorManager;
    }

    /** AOSP walks the full sensor list and ORs in a legacy bit per recognised
     *  type. Only three types have a legacy bit at all, so a device with a
     *  gyroscope and no magnetometer reports fewer bits than it has sensors --
     *  that is the API, not a shortfall here. */
    int getSensors() {
        int result = 0;
        final List<Sensor> fullList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor i : fullList) {
            switch (i.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    result |= SensorManager.SENSOR_ACCELEROMETER;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    result |= SensorManager.SENSOR_MAGNETIC_FIELD;
                    break;
                case Sensor.TYPE_ORIENTATION:
                    result |= SensorManager.SENSOR_ORIENTATION
                            | SensorManager.SENSOR_ORIENTATION_RAW;
                    break;
            }
        }
        return result;
    }

    boolean registerListener(SensorListener listener, int sensors, int rate) {
        if (listener == null) {
            return false;
        }
        boolean result = false;
        result = registerLegacyListener(SensorManager.SENSOR_ACCELEROMETER,
                Sensor.TYPE_ACCELEROMETER, listener, sensors, rate) || result;
        result = registerLegacyListener(SensorManager.SENSOR_MAGNETIC_FIELD,
                Sensor.TYPE_MAGNETIC_FIELD, listener, sensors, rate) || result;
        result = registerLegacyListener(SensorManager.SENSOR_ORIENTATION_RAW,
                Sensor.TYPE_ORIENTATION, listener, sensors, rate) || result;
        result = registerLegacyListener(SensorManager.SENSOR_ORIENTATION,
                Sensor.TYPE_ORIENTATION, listener, sensors, rate) || result;
        result = registerLegacyListener(SensorManager.SENSOR_TEMPERATURE,
                Sensor.TYPE_TEMPERATURE, listener, sensors, rate) || result;
        return result;
    }

    private boolean registerLegacyListener(int legacyType, int type,
            SensorListener listener, int sensors, int rate) {
        boolean result = false;
        if ((sensors & legacyType) != 0) {
            Sensor sensor = mSensorManager.getDefaultSensor(type);
            if (sensor != null) {
                synchronized (mLegacyListenersMap) {
                    LegacyListener legacyListener = mLegacyListenersMap.get(listener);
                    if (legacyListener == null) {
                        legacyListener = new LegacyListener(listener);
                        mLegacyListenersMap.put(listener, legacyListener);
                    }
                    if (legacyListener.registerSensor(legacyType)) {
                        result = mSensorManager.registerListener(legacyListener, sensor, rate);
                    } else {
                        result = true; // sensor already enabled
                    }
                }
            }
        }
        return result;
    }

    void unregisterListener(SensorListener listener, int sensors) {
        if (listener == null) {
            return;
        }
        unregisterLegacyListener(SensorManager.SENSOR_ACCELEROMETER,
                Sensor.TYPE_ACCELEROMETER, listener, sensors);
        unregisterLegacyListener(SensorManager.SENSOR_MAGNETIC_FIELD,
                Sensor.TYPE_MAGNETIC_FIELD, listener, sensors);
        unregisterLegacyListener(SensorManager.SENSOR_ORIENTATION_RAW,
                Sensor.TYPE_ORIENTATION, listener, sensors);
        unregisterLegacyListener(SensorManager.SENSOR_ORIENTATION,
                Sensor.TYPE_ORIENTATION, listener, sensors);
        unregisterLegacyListener(SensorManager.SENSOR_TEMPERATURE,
                Sensor.TYPE_TEMPERATURE, listener, sensors);
    }

    private void unregisterLegacyListener(int legacyType, int type,
            SensorListener listener, int sensors) {
        if ((sensors & legacyType) != 0) {
            Sensor sensor = mSensorManager.getDefaultSensor(type);
            if (sensor != null) {
                synchronized (mLegacyListenersMap) {
                    LegacyListener legacyListener = mLegacyListenersMap.get(listener);
                    if (legacyListener != null) {
                        if (legacyListener.unregisterSensor(legacyType)) {
                            mSensorManager.unregisterListener(legacyListener, sensor);
                            if (!legacyListener.hasSensors()) {
                                mLegacyListenersMap.remove(listener);
                            }
                        }
                    }
                }
            }
        }
    }

    /** See the header: AOSP's IRotationWatcher has no analogue, so this reads
     *  the same signal android.view.Display.getRotation() reads. */
    static int getRotation() {
        try {
            // v1.0.1637: the committed display, the same source
            // Display.getRotation() reads, so the two cannot disagree.
            return (com.macapk.loader.DisplayConfig.realWidthPx()
                        > com.macapk.loader.DisplayConfig.realHeightPx())
                    ? Surface.ROTATION_90 : Surface.ROTATION_0;
        } catch (Throwable t) {
            // Headless / native not registered: AOSP's own fallback when it
            // cannot reach the window service is ROTATION_0.
            return Surface.ROTATION_0;
        }
    }

    private static final class LegacyListener implements SensorEventListener {
        private float[] mValues = new float[6];
        private SensorListener mTarget;
        private int mSensors;
        private final LmsFilter mYawfilter = new LmsFilter();

        LegacyListener(SensorListener target) {
            mTarget = target;
            mSensors = 0;
        }

        boolean registerSensor(int legacyType) {
            if ((mSensors & legacyType) != 0) {
                return false;
            }
            boolean alreadyHasOrientationSensor = hasOrientationSensor(mSensors);
            mSensors |= legacyType;
            if (alreadyHasOrientationSensor && hasOrientationSensor(legacyType)) {
                return false; // don't need to re-register the orientation sensor
            }
            return true;
        }

        boolean unregisterSensor(int legacyType) {
            if ((mSensors & legacyType) == 0) {
                return false;
            }
            mSensors &= ~legacyType;
            if (hasOrientationSensor(legacyType) && hasOrientationSensor(mSensors)) {
                return false; // can't unregister the orientation sensor just yet
            }
            return true;
        }

        boolean hasSensors() {
            return mSensors != 0;
        }

        private static boolean hasOrientationSensor(int sensors) {
            return (sensors & (SensorManager.SENSOR_ORIENTATION
                    | SensorManager.SENSOR_ORIENTATION_RAW)) != 0;
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
            try {
                mTarget.onAccuracyChanged(getLegacySensorType(sensor.getType()), accuracy);
            } catch (AbstractMethodError e) {
                // AOSP: old app that doesn't implement this method; ignore.
            }
        }

        @Override public void onSensorChanged(SensorEvent event) {
            final float[] v = mValues;
            v[0] = event.values[0];
            v[1] = event.values[1];
            v[2] = event.values[2];
            int type = event.sensor.getType();
            int legacyType = getLegacySensorType(type);
            mapSensorDataToWindow(legacyType, v, LegacySensorManager.getRotation());
            if (type == Sensor.TYPE_ORIENTATION) {
                if ((mSensors & SensorManager.SENSOR_ORIENTATION_RAW) != 0) {
                    mTarget.onSensorChanged(SensorManager.SENSOR_ORIENTATION_RAW, v);
                }
                if ((mSensors & SensorManager.SENSOR_ORIENTATION) != 0) {
                    v[0] = mYawfilter.filter(event.timestamp, v[0]);
                    mTarget.onSensorChanged(SensorManager.SENSOR_ORIENTATION, v);
                }
            } else {
                mTarget.onSensorChanged(legacyType, v);
            }
        }

        /** AOSP fills SIX values: [0..2] are the window-mapped axes and [3..5]
         *  are the UNROTATED ones, which is why the array is 6 long. Legacy
         *  apps read both halves. */
        private void mapSensorDataToWindow(int sensor, float[] values, int orientation) {
            float x = values[0];
            float y = values[1];
            float z = values[2];

            switch (sensor) {
                case SensorManager.SENSOR_ORIENTATION:
                case SensorManager.SENSOR_ORIENTATION_RAW:
                    z = -z;
                    break;
                case SensorManager.SENSOR_ACCELEROMETER:
                    x = -x;
                    y = -y;
                    z = -z;
                    break;
                case SensorManager.SENSOR_MAGNETIC_FIELD:
                    x = -x;
                    y = -y;
                    break;
            }
            values[0] = x;
            values[1] = y;
            values[2] = z;
            values[3] = x;
            values[4] = y;
            values[5] = z;

            if ((orientation & Surface.ROTATION_90) != 0) {
                // handles 90 and 270 rotation
                switch (sensor) {
                    case SensorManager.SENSOR_ACCELEROMETER:
                    case SensorManager.SENSOR_MAGNETIC_FIELD:
                        values[0] = -y;
                        values[1] = x;
                        values[2] = z;
                        break;
                    case SensorManager.SENSOR_ORIENTATION:
                    case SensorManager.SENSOR_ORIENTATION_RAW:
                        values[0] = x + ((x < 270) ? 90 : -270);
                        values[1] = z;
                        values[2] = y;
                        break;
                }
            }
            if ((orientation & Surface.ROTATION_180) != 0) {
                x = values[0];
                y = values[1];
                z = values[2];
                // handles 180 (flip) and 270 (flip + 90) rotation
                switch (sensor) {
                    case SensorManager.SENSOR_ACCELEROMETER:
                    case SensorManager.SENSOR_MAGNETIC_FIELD:
                        values[0] = -x;
                        values[1] = -y;
                        values[2] = z;
                        break;
                    case SensorManager.SENSOR_ORIENTATION:
                    case SensorManager.SENSOR_ORIENTATION_RAW:
                        values[0] = (x >= 180) ? (x - 180) : (x + 180);
                        values[1] = -y;
                        values[2] = -z;
                        break;
                }
            }
        }

        private static int getLegacySensorType(int type) {
            switch (type) {
                case Sensor.TYPE_ACCELEROMETER:
                    return SensorManager.SENSOR_ACCELEROMETER;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    return SensorManager.SENSOR_MAGNETIC_FIELD;
                case Sensor.TYPE_ORIENTATION:
                    return SensorManager.SENSOR_ORIENTATION_RAW;
                case Sensor.TYPE_TEMPERATURE:
                    return SensorManager.SENSOR_TEMPERATURE;
            }
            return 0;
        }
    }

    /** AOSP's least-mean-squares yaw smoother + forward predictor. Verbatim:
     *  it is the reason SENSOR_ORIENTATION and SENSOR_ORIENTATION_RAW differ,
     *  and its exact output shape (wrapping at 360, predicting 80ms ahead) is
     *  what a legacy compass app was tuned against. */
    private static final class LmsFilter {
        private static final int SENSORS_RATE_MS = 20;
        private static final int COUNT = 12;
        private static final float PREDICTION_RATIO = 1.0f / 3.0f;
        private static final float PREDICTION_TIME =
                (SENSORS_RATE_MS * COUNT / 1000.0f) * PREDICTION_RATIO;
        private float[] mV = new float[COUNT * 2];
        private long[] mT = new long[COUNT * 2];
        private int mIndex;

        LmsFilter() {
            mIndex = COUNT;
        }

        float filter(long time, float in) {
            float v = in;
            final float ns = 1.0f / 1000000000.0f;
            float v1 = mV[mIndex];
            if ((v - v1) > 180) {
                v -= 360;
            } else if ((v1 - v) > 180) {
                v += 360;
            }
            mIndex++;
            if (mIndex >= COUNT * 2) {
                mIndex = COUNT;
            }
            mV[mIndex] = v;
            mT[mIndex] = time;
            mV[mIndex - COUNT] = v;
            mT[mIndex - COUNT] = time;

            float A, B, C, D, E;
            float a, b;
            int i;

            A = B = C = D = E = 0;
            for (i = 0; i < COUNT - 1; i++) {
                final int j = mIndex - 1 - i;
                final float Z = mV[j];
                final float T = (mT[j] / 2 + mT[j + 1] / 2 - time) * ns;
                float dT = (mT[j] - mT[j + 1]) * ns;
                dT *= dT;
                A += Z * dT;
                B += T * (T * dT);
                C += (T * dT);
                D += Z * (T * dT);
                E += dT;
            }
            b = (A * B + C * D) / (E * B + C * C);
            a = (E * b - A) / C;
            float f = b + PREDICTION_TIME * a;

            // Normalize
            f *= (1.0f / 360.0f);
            if (((f >= 0) ? f : -f) >= 0.5f) {
                f = f - (float) Math.ceil(f + 0.5f) + 1.0f;
            }
            if (f < 0) {
                f += 1.0f;
            }
            f *= 360.0f;
            return f;
        }
    }
}
