// android.view.SurfaceControlInputReceiver -- the callback a caller registers
// with WindowManager.register*SurfaceControlInputReceiver to receive input
// events targeted at an embedded SurfaceControl. Mac.apk routes all input
// through the single Cocoa window (there are no embedded input-receiving
// SurfaceControls), so nothing dispatches to one -- the type exists because
// the WindowManager signatures name it.
// Source: frameworks/base core/java/android/view/SurfaceControlInputReceiver.java
package android.view;

public interface SurfaceControlInputReceiver {
    boolean onInputEvent(InputEvent event);
}
