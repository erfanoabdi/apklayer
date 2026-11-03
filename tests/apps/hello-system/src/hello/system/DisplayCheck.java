package hello.system;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

/**
 * The display as detection code reads it: a desktop window is the device's whole screen in its natural orientation, so the rotation is 0
 * and the configuration's orientation follows the real size and the display mode's physical size is that size (Device Info HW prints it as the resolution).
 */
final class DisplayCheck implements Check {
    @Override public String run(Context context) throws Exception {
        Display display = ((android.hardware.display.DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics real = new DisplayMetrics();
        display.getRealMetrics(real);
        int orientation = context.getResources().getConfiguration().orientation;
        Display.Mode mode = display.getMode();
        String seen = real.widthPixels + "x" + real.heightPixels + " rotation=" + display.getRotation() + " orientation=" + orientation + " mode=" + mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight();
        Check.require(display.getRotation() == Surface.ROTATION_0, "rotated display: " + seen);
        Check.require(orientation == (real.heightPixels >= real.widthPixels ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE), "orientation differs from the size: " + seen);
        // a mode's physical size is the panel's, in the natural orientation (rotation 0: the real size)
        Check.require(mode.getPhysicalWidth() == real.widthPixels && mode.getPhysicalHeight() == real.heightPixels, "mode differs from the size: " + seen);
        return seen;
    }
}
