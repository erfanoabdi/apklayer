package android.graphics;

// AOSP base class for mask filters (e.g. BlurMaskFilter). The public API
// surface is just an empty constructor; all real behavior is native on
// Android. We keep it as the shared supertype so BlurMaskFilter extends it
// and Paint can hold a MaskFilter field. Our Canvas is software (AWT-backed),
// so subclasses approximate the effect at the Graphics2D layer.
public class MaskFilter {
    public MaskFilter() {}
}
