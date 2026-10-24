package android.renderscript;

/**
 * Vector of 4 byte values (byte4 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Byte4.java
 */
public class Byte4 {

    public byte x;
    public byte y;
    public byte z;
    public byte w;

    public Byte4() {
    }

    public Byte4(byte x, byte y, byte z, byte w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
}
