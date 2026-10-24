package android.renderscript;

/**
 * Vector of 2 double values (double2 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Double2.java
 */
public class Double2 {

    public double x;
    public double y;

    public Double2() {
    }

    public Double2(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
