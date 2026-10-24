package androidx.window.extensions.embedding;

public final class ActivityStackAttributes {
    private final android.graphics.Rect mRelativeBounds;
    private final WindowAttributes mWindowAttributes;
    private ActivityStackAttributes(Builder b) { mRelativeBounds = b.mRelativeBounds; mWindowAttributes = b.mWindowAttributes; }
    public android.graphics.Rect getRelativeBounds() { return mRelativeBounds; }
    public WindowAttributes getWindowAttributes() { return mWindowAttributes; }
    public static final class Builder {
        private android.graphics.Rect mRelativeBounds = new android.graphics.Rect();
        private WindowAttributes mWindowAttributes = new WindowAttributes(WindowAttributes.DIM_AREA_ON_TASK);
        public Builder() {}
        public Builder setRelativeBounds(android.graphics.Rect relativeBounds) { mRelativeBounds = relativeBounds; return this; }
        public Builder setWindowAttributes(WindowAttributes windowAttributes) { mWindowAttributes = windowAttributes; return this; }
        public ActivityStackAttributes build() { return new ActivityStackAttributes(this); }
    }
}
