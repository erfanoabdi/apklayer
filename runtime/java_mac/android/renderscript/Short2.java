package android.renderscript;

/**
 * Vector of 2 short values (short2 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Short2.java
 */
public class Short2 {

    public short x;
    public short y;

    public Short2() {
    }

    public Short2(short x, short y) {
        this.x = x;
        this.y = y;
    }
}
