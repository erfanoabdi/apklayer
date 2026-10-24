package android.renderscript;

/**
 * Vector of 3 byte values (byte3 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Byte3.java
 */
public class Byte3 {

    public byte x;
    public byte y;
    public byte z;

    public Byte3() {
    }

    public Byte3(byte x, byte y, byte z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
