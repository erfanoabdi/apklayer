package androidx.window.extensions.area;
import androidx.window.extensions.core.util.function.Consumer;
/** Rear-display window-area control. Mac.apk has no rear display, so status
 *  listeners report UNSUPPORTED and session starts are rejected. */
public interface WindowAreaComponent {
    int STATUS_UNSUPPORTED = 0;
    int STATUS_UNAVAILABLE = 1;
    int STATUS_AVAILABLE = 2;
    int STATUS_ACTIVE = 3;
    int SESSION_STATE_INACTIVE = 0;
    int SESSION_STATE_ACTIVE = 1;
    int SESSION_STATE_CONTENT_VISIBLE = 2;

    default void addRearDisplayStatusListener(Consumer<Integer> consumer) {
        if (consumer != null) consumer.accept(STATUS_UNSUPPORTED);
    }
    default void removeRearDisplayStatusListener(Consumer<Integer> consumer) { }
    default void startRearDisplaySession(android.app.Activity activity, Consumer<Integer> consumer) {
        if (consumer != null) consumer.accept(SESSION_STATE_INACTIVE);
    }
    default void endRearDisplaySession() { }
    default android.util.DisplayMetrics getRearDisplayMetrics() { return null; }
    default void addRearDisplayPresentationStatusListener(Consumer<ExtensionWindowAreaStatus> consumer) { }
    default void removeRearDisplayPresentationStatusListener(Consumer<ExtensionWindowAreaStatus> consumer) { }
    default void startRearDisplayPresentationSession(android.app.Activity activity, Consumer<Integer> consumer) {
        if (consumer != null) consumer.accept(SESSION_STATE_INACTIVE);
    }
    default void endRearDisplayPresentationSession() { }
    default ExtensionWindowAreaPresentation getRearDisplayPresentation() { return null; }
}
