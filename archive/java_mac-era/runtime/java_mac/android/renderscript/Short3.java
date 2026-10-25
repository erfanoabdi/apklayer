package android.renderscript;

/**
 * Vector of 3 short values (short3 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Short3.java
 */
public class Short3 {

    public short x;
    public short y;
    public short z;

    public Short3() {
    }

    public Short3(short x, short y, short z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
