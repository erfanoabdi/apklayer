package android.renderscript;

/**
 * Vector of 3 double values (double3 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Double3.java
 */
public class Double3 {

    public double x;
    public double y;
    public double z;

    public Double3() {
    }

    public Double3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
