// android.view.SearchEvent -- carries which input device triggered the search
// key, handed to Activity/Window.Callback.onSearchRequested(SearchEvent).
// Source: frameworks/base core/java/android/view/SearchEvent.java (android16-release).
package android.view;

public class SearchEvent {
    private final InputDevice mInputDevice;

    /** @hide -- Mac.apk keeps the no-arg form the tree already handed out; AOSP
     *  declares only the InputDevice constructor. */
    public SearchEvent() { mInputDevice = null; }

    /** AOSP SearchEvent(InputDevice). */
    public SearchEvent(InputDevice inputDevice) { mInputDevice = inputDevice; }

    /** AOSP SearchEvent.getInputDevice(): the device that issued the search, or
     *  null when the search was not started by a device (a soft button). */
    public InputDevice getInputDevice() { return mInputDevice; }
}
