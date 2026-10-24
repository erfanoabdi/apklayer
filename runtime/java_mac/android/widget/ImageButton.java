package android.widget;
import android.content.Context;

/** AOSP `Widget.ImageButton` supplies defaults an ImageView does NOT have --
 *  most visibly `scaleType=center` (draw the drawable at its intrinsic size,
 *  centred, never scaled up):
 *
 *    core/res/res/values/styles.xml (android13-release), Widget.ImageButton:
 *      &lt;item name="focusable"&gt;true&lt;/item&gt;
 *      &lt;item name="clickable"&gt;true&lt;/item&gt;
 *      &lt;item name="scaleType"&gt;center&lt;/item&gt;
 *      &lt;item name="background"&gt;@drawable/btn_default&lt;/item&gt;
 *
 *  We were a 3-line ImageView subclass, inheriting ImageView's FIT_CENTER, so
 *  every ImageButton UPSCALED its icon to fill the button. Measured victim:
 *  OpenCalculator's key glyphs (^, backspace, the decimal dot, the chevron)
 *  rendered roughly 3x too large against the emulator, because its keys are
 *  ImageButtons carrying `app:srcCompat` vectors
 *  (docs/canvas-ab-analyst-report_2026-08-02.md item 8).
 *
 *  Set in the constructor rather than via a style so it survives regardless of
 *  which ctor the app uses; an explicit `android:scaleType` in XML still wins,
 *  because LayoutInflater.applyAttributes runs after construction. */
public class ImageButton extends ImageView {
    public ImageButton(Context c) { super(c); initImageButtonDefaults(); }
    public ImageButton(Context c, android.util.AttributeSet a) {
        super(c, a); initImageButtonDefaults();
    }
    public ImageButton(Context c, android.util.AttributeSet a, int s) {
        super(c, a, s); initImageButtonDefaults();
    }
    public ImageButton(Context c, android.util.AttributeSet a, int s, int r) {
        super(c, a, s, r); initImageButtonDefaults();
    }
    private void initImageButtonDefaults() {
        setScaleType(ScaleType.CENTER);
        setFocusable(true);
        setClickable(true);
    }
}
