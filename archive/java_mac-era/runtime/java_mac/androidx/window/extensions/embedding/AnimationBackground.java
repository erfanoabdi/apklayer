package androidx.window.extensions.embedding;

/** Background shown behind an embedding animation. */
public abstract class AnimationBackground {
    public static final AnimationBackground ANIMATION_BACKGROUND_DEFAULT = new ColorBackground(0);
    public static ColorBackground createColorBackground(int color) { return new ColorBackground(color); }
    public static final class ColorBackground extends AnimationBackground {
        private final int mColor;
        ColorBackground(int color) { mColor = color; }
        public int getColor() { return mColor; }
    }
}
