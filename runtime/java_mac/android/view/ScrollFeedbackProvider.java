package android.view;
/** Provides haptic/audio feedback during scrolling (API 34). Mac.apk has no
 *  scroll-haptics surface, so the callbacks are no-ops -- the same as a device
 *  without a haptic actuator. Apps call these per scroll frame; they must not
 *  throw. Source: frameworks/base .../view/ScrollFeedbackProvider.java */
public interface ScrollFeedbackProvider {
    static ScrollFeedbackProvider createProvider(View view) {
        return new ScrollFeedbackProvider() {
            @Override public void onScrollProgress(int inputDeviceId, int source, int axis, int deltaPixels) {}
            @Override public void onScrollLimit(int inputDeviceId, int source, int axis, boolean isStart) {}
            @Override public void onSnapToItem(int inputDeviceId, int source, int axis) {}
        };
    }
    void onScrollProgress(int inputDeviceId, int source, int axis, int deltaPixels);
    void onScrollLimit(int inputDeviceId, int source, int axis, boolean isStart);
    void onSnapToItem(int inputDeviceId, int source, int axis);
}
