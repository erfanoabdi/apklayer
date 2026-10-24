// android.graphics.LightingColorFilter -- AOSP-shape ColorFilter that
// multiplies the source color by colorMultiply (scaled by 1/255 per
// channel) then adds colorAdd, clamping each RGB channel to [0,255].
// Alpha is left untouched (real Android ignores the alpha bytes of both
// operands). We store the two ints and expose the AOSP getters/setters so
// widget tint chains that construct one (and read its values back) succeed;
// the actual compositing, when used, lives in our Drawable.draw paths.
//
// API surface mirrors frameworks/base graphics/java/android/graphics/
// LightingColorFilter.java: ctor(int,int) plus getColorMultiply /
// getColorAdd (added API 21) and setColorMultiply / setColorAdd (API 29).
package android.graphics;

public class LightingColorFilter extends ColorFilter {
    private int mMul;
    private int mAdd;

    public LightingColorFilter(int colorMultiply, int colorAdd) {
        mMul = colorMultiply;
        mAdd = colorAdd;
    }

    public int getColorMultiply() { return mMul; }
    public int getColorAdd()      { return mAdd; }

    // API 29: AOSP allows mutation and re-syncs the native instance. We
    // just update the stored value so subsequent getters reflect it.
    public void setColorMultiply(int colorMultiply) { mMul = colorMultiply; }
    public void setColorAdd(int colorAdd)           { mAdd = colorAdd; }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final LightingColorFilter other = (LightingColorFilter) object;
        return mMul == other.mMul && mAdd == other.mAdd;
    }

    @Override
    public int hashCode() {
        return 31 * mMul + mAdd;
    }
}
