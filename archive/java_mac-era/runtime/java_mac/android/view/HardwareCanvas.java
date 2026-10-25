package android.view;
import android.graphics.Canvas;
/** Legacy hardware-accelerated Canvas base (pre-RenderNode). Retained only as a
 *  type; Mac.apk draws every Canvas through the same software/Skia-equivalent
 *  path, so no special hardware behavior is added here.
 *  Source: frameworks/base (historical) android.view.HardwareCanvas */
public abstract class HardwareCanvas extends Canvas {
    public HardwareCanvas() { super(); }
    @Override public boolean isHardwareAccelerated() { return true; }
}
