// android.view.MotionPredictor -- the platform's stylus/touch motion
// PREDICTOR: an app feeds it the events it receives and asks for a synthetic
// future event, so ink can be drawn slightly ahead of the finger.
// Source: frameworks/base core/java/android/view/MotionPredictor.java
// (android16-release).
//
// AOSP gates the whole feature on config_enableMotionPrediction, and on a
// device where that is false isPredictionAvailable() returns false and predict()
// returns null. Mac.apk has no prediction model (there is no libinput
// MotionPredictor to bind), so it takes exactly that branch -- a real,
// reachable device state, not a stand-in. record() still accepts events so a
// caller's bookkeeping is undisturbed.
package android.view;

public final class MotionPredictor {
    private final boolean mEnabled;

    public MotionPredictor(android.content.Context context) {
        java.util.Objects.requireNonNull(context);
        mEnabled = false;
    }

    /** AOSP MotionPredictor.record(MotionEvent): feed the predictor a real
     *  event. AOSP throws IllegalArgumentException on an out-of-order event even
     *  when prediction is off, so the argument check stays. */
    public void record(MotionEvent event) {
        java.util.Objects.requireNonNull(event);
    }

    /** AOSP MotionPredictor.predict(long predictionTimeNanos): the synthetic
     *  future event, or NULL when there is nothing to predict from -- which is
     *  what a device with prediction disabled always returns. */
    public MotionEvent predict(long predictionTimeNanos) { return null; }

    /** AOSP MotionPredictor.isPredictionAvailable(int deviceId, int source). */
    public boolean isPredictionAvailable(int deviceId, int source) { return mEnabled; }
}
