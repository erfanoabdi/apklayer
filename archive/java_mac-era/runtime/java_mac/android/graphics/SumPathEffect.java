// android.graphics.SumPathEffect -- AOSP PathEffect that applies two
// effects independently and draws BOTH results (the sum). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/SumPathEffect.java
// Stored faithfully (both children retained); our software stroke path
// has no true compositing for path effects, so the accessors let a draw
// site sample whichever child it can map (e.g. a dash child).
package android.graphics;

public class SumPathEffect extends PathEffect {
    private final PathEffect first;
    private final PathEffect second;

    public SumPathEffect(PathEffect first, PathEffect second) {
        this.first = first;
        this.second = second;
    }

    public PathEffect macapkFirst()  { return first; }
    public PathEffect macapkSecond() { return second; }
}
