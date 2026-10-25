package android.renderscript;

/**
 * Vector of 3 int values (int3 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Int3.java
 */
public class Int3 {

    public int x;
    public int y;
    public int z;

    public Int3() {
    }

    public Int3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
