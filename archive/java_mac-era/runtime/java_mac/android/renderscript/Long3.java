package android.renderscript;

/**
 * Vector of 3 long values (long3 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Long3.java
 */
public class Long3 {

    public long x;
    public long y;
    public long z;

    public Long3() {
    }

    public Long3(long x, long y, long z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
