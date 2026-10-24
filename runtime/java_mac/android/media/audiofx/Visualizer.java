// android.media.audiofx.Visualizer -- pulls waveform/FFT data from the output of
// an audio session. AOSP frameworks/base media/java/android/media/audiofx/Visualizer.java.
//
// Mac.apk's mixer has no per-session tap, so the visualizer returns silence:
// getWaveForm fills the mid-level (0x80) PCM-unsigned silence value, getFft
// fills zeros. A visualizer-driven UI runs its capture loop and draws a flat
// line rather than crashing -- honest for a host with no audio tap. The data-
// capture listener can be registered but never fires (no producer).
package android.media.audiofx;

public class Visualizer {
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_NO_INIT = -3;
    public static final int ERROR_BAD_VALUE = -4;
    public static final int ERROR_INVALID_OPERATION = -5;
    public static final int ERROR_DEAD_OBJECT = -7;

    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int STATE_ENABLED = 2;

    public static final int SCALING_MODE_NORMALIZED = 0;
    public static final int SCALING_MODE_AS_PLAYED = 1;
    public static final int MEASUREMENT_MODE_NONE = 0;
    public static final int MEASUREMENT_MODE_PEAK_RMS = 1;

    private static final int CAPTURE_SIZE = 1024;
    private int mCaptureSize = CAPTURE_SIZE;
    private boolean mEnabled = false;

    public Visualizer(int audioSession) {}

    public int setEnabled(boolean enabled) { mEnabled = enabled; return SUCCESS; }
    public boolean getEnabled() { return mEnabled; }
    public int getSamplingRate() { return 44100000; }  // milliHz, AOSP units
    public static int[] getCaptureSizeRange() { return new int[] { 128, CAPTURE_SIZE }; }
    public static int getMaxCaptureRate() { return 20000; }
    public int setCaptureSize(int size) { mCaptureSize = size; return SUCCESS; }
    public int getCaptureSize() { return mCaptureSize; }
    public int setScalingMode(int mode) { return SUCCESS; }
    public int getScalingMode() { return SCALING_MODE_NORMALIZED; }
    public int setMeasurementMode(int mode) { return SUCCESS; }
    public int getMeasurementMode() { return MEASUREMENT_MODE_NONE; }

    public int getWaveForm(byte[] waveform) {
        if (waveform == null) return ERROR_BAD_VALUE;
        // 8-bit unsigned PCM silence is 0x80 (mid-rail).
        java.util.Arrays.fill(waveform, (byte) 0x80);
        return SUCCESS;
    }
    public int getFft(byte[] fft) {
        if (fft == null) return ERROR_BAD_VALUE;
        java.util.Arrays.fill(fft, (byte) 0);
        return SUCCESS;
    }
    public int getMeasurementPeakRms(MeasurementPeakRms measurement) {
        if (measurement != null) { measurement.mPeak = -9600; measurement.mRms = -9600; }
        return SUCCESS;
    }

    public int setDataCaptureListener(OnDataCaptureListener listener, int rate,
                                      boolean waveform, boolean fft) {
        return SUCCESS;
    }
    public void release() {}

    /** AOSP declares finalize() on this class so a dropped instance still
     *  releases whatever it holds. release() is idempotent here. */
    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }


    public static final class MeasurementPeakRms {
        public int mPeak;
        public int mRms;
        public MeasurementPeakRms() {}
    }
    public interface OnDataCaptureListener {
        void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate);
        void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate);
    }
}
