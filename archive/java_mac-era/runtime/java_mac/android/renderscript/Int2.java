package android.renderscript;

/**
 * Vector of 2 int values (int2 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Int2.java
 */
public class Int2 {

    public int x;
    public int y;

    public Int2() {
    }

    public Int2(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
