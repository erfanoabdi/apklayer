package android.renderscript;

/**
 * Vector of 2 long values (long2 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Long2.java
 */
public class Long2 {

    public long x;
    public long y;

    public Long2() {
    }

    public Long2(long x, long y) {
        this.x = x;
        this.y = y;
    }
}
