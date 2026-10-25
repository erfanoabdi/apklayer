// android.gesture.Prediction -- one recognition result: a learned gesture label
// plus a match score, produced by GestureStore.recognize() / InstanceLearner.
// Mirrors AOSP frameworks/base/core/java/android/gesture/Prediction.java exactly
// (public final String name; public double score; package-private constructor).
package android.gesture;

public class Prediction {
    public final String name;

    public double score;

    Prediction(String label, double predictionScore) {
        name = label;
        score = predictionScore;
    }

    @Override
    public String toString() {
        return name;
    }
}
