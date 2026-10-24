package android.renderscript;

/**
 * Vector of 2 byte values (byte2 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Byte2.java
 */
public class Byte2 {

    public byte x;
    public byte y;

    public Byte2() {
    }

    public Byte2(byte x, byte y) {
        this.x = x;
        this.y = y;
    }
}
