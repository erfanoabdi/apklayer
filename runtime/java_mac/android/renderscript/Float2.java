package android.renderscript;

/**
 * Vector of 2 float values (float2 in a RenderScript kernel).
 *
 * AOSP declares this as a plain public-field struct with a no-arg constructor
 * and an all-components constructor, and nothing else. FieldPacker is the only
 * framework code that reads or writes one, so a faithful field-for-field
 * reimplementation is the whole class -- there is no behaviour to get wrong.
 *
 * Source: frameworks/rs/java/android/renderscript/Float2.java
 */
public class Float2 {

    public float x;
    public float y;

    public Float2() {
    }

    public Float2(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
