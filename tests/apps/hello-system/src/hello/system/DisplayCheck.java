package hello.system;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

/**
 * The display as detection code reads it: a desktop window is the device's whole screen in its natural orientation, so the rotation is 0
 * and the configuration's orientation follows the real size and the display mode's physical size is that size (Device Info HW prints it as the resolution).
 * The compositor is in the service manager under the name Android registers it with ("SurfaceFlinger", as DroidGuard looks it up) and answers.
 */
final class DisplayCheck implements Check {
    @Override public String run(Context context) throws Exception {
        android.os.IBinder flinger = (android.os.IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "SurfaceFlinger");
        Check.require(flinger != null && flinger.pingBinder(), "no \"SurfaceFlinger\" service: " + flinger);
        Check.require("android.ui.ISurfaceComposer".equals(flinger.getInterfaceDescriptor()), "\"SurfaceFlinger\" is " + flinger.getInterfaceDescriptor());
        // apklayer's own services and HAL instances stay out of apps' reach (Android's SELinux refuses apps HAL names)
        for (String hidden : new String[] {"apklayer.instance", "apklayer.accounts", "android.hardware.graphics.allocator.IAllocator/default"})
            Check.require(Class.forName("android.os.ServiceManager").getMethod("checkService", String.class).invoke(null, hidden) == null, "an app got \"" + hidden + "\"");
        Display display = ((android.hardware.display.DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics real = new DisplayMetrics();
        display.getRealMetrics(real);
        int orientation = context.getResources().getConfiguration().orientation;
        Display.Mode mode = display.getMode();
        android.graphics.Point size = new android.graphics.Point();
        display.getRealSize(size);
        android.graphics.Rect max = context.getSystemService(android.view.WindowManager.class).getMaximumWindowMetrics().getBounds();
        String seen = real.widthPixels + "x" + real.heightPixels + " realSize=" + size.x + "x" + size.y + " maxWindow=" + max.width() + "x" + max.height() + " rotation=" + display.getRotation() + " orientation=" + orientation + " mode=" + mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight();
        Check.require(display.getRotation() == Surface.ROTATION_0, "rotated display: " + seen);
        Check.require(orientation == (real.heightPixels >= real.widthPixels ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE), "orientation differs from the size: " + seen);
        // a mode's physical size is the panel's, in the natural orientation (rotation 0: the real size)
        Check.require(mode.getPhysicalWidth() == real.widthPixels && mode.getPhysicalHeight() == real.heightPixels, "mode differs from the size: " + seen);
        // the screen, however it is asked for (Display.getRealSize and the maximum window metrics: WindowConfiguration's max bounds)
        Check.require(size.x == real.widthPixels && size.y == real.heightPixels && max.width() == real.widthPixels && max.height() == real.heightPixels, "screen sizes differ: " + seen);
        return seen;
    }
}
