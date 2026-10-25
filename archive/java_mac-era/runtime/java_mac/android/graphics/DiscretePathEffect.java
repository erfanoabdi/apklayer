// android.graphics.DiscretePathEffect -- AOSP PathEffect that chops the
// path into segments of segmentLength and randomly perturbs each segment
// endpoint by up to +/- deviation (a hand-drawn / sketchy look). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/DiscretePathEffect.java
// Stored faithfully; our software stroke path does not perturb geometry,
// so the line draws straight. The accessors expose the params for any
// future path-build-time application.
package android.graphics;

public class DiscretePathEffect extends PathEffect {
    private final float segmentLength;
    private final float deviation;

    public DiscretePathEffect(float segmentLength, float deviation) {
        this.segmentLength = segmentLength;
        this.deviation = deviation;
    }

    public float macapkSegmentLength() { return segmentLength; }
    public float macapkDeviation()     { return deviation; }
}
