package androidx.window.extensions.layout;
import android.content.Context;
import java.util.Collections;
/** Reports a window's display features. Mac.apk is flat, so every window has an
 *  empty WindowLayoutInfo (no fold) and the listener fires once with it.
 *  Interface with inert default methods, so WindowExtensions returns an
 *  anonymous impl and androidx.window's reflective validation still sees an
 *  interface. Source: androidx.window.extensions.layout.WindowLayoutComponent */
public interface WindowLayoutComponent {
    default void addWindowLayoutInfoListener(android.app.Activity activity,
            java.util.function.Consumer<WindowLayoutInfo> consumer) {
        if (consumer != null) consumer.accept(new WindowLayoutInfo(Collections.emptyList()));
    }
    default void removeWindowLayoutInfoListener(java.util.function.Consumer<WindowLayoutInfo> consumer) { }
    default void addWindowLayoutInfoListener(Context context,
            androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer) {
        if (consumer != null) consumer.accept(new WindowLayoutInfo(Collections.emptyList()));
    }
    default void removeWindowLayoutInfoListener(
            androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer) { }
    default WindowLayoutInfo getCurrentWindowLayoutInfo(Context context) {
        return new WindowLayoutInfo(Collections.emptyList());
    }
    default SupportedWindowFeatures getSupportedWindowFeatures() {
        return new SupportedWindowFeatures.Builder(Collections.emptyList()).build();
    }
}
