// android.view.InputEvent -- common base for KeyEvent and MotionEvent.
// Unity's UnityPlayer.injectEvent(InputEvent) / nativeInjectEvent read
// getSource() and getDeviceId() off this base to CLASSIFY the event
// (touchscreen vs keyboard vs mouse) before routing it into the engine's
// input system. The previous empty stub -- with MotionEvent/KeyEvent not
// even extending it -- meant injected touches reached UnityPlayer.injectEvent
// (via the SurfaceView's onTouchEvent) but carried no source, so Unity could
// not classify them and silently dropped them (uGUI buttons never fired).
// Mirrors the public AOSP API
// (frameworks/base/core/java/android/view/InputEvent.java).
//
// implements Parcelable at ZERO extra cost here: this class is abstract and
// declares no writeToParcel/describeContents of its own -- Java allows an
// abstract class to declare an interface without providing bodies, deferring
// the obligation to concrete subclasses. Its only two concrete subclasses,
// KeyEvent and MotionEvent, already fully implement Parcelable for real
// (verified with `javap -p` against the built framework jar, not a source
// grep -- see docs/parcelable-gap-workorder_2026-08-13.md section 2, which
// flags a source-grep misattribution elsewhere in this same audit as the
// reason to insist on javap). So declaring it on the base class closes the
// family's type identity without adding a single method anywhere.
package android.view;

public abstract class InputEvent implements android.os.Parcelable {
    public InputEvent() {}

    /** Id of the device that produced the event; <0 for a virtual device. */
    public abstract int getDeviceId();

    /** Source class/bitmask (InputDevice.SOURCE_*). Load-bearing: Unity
     *  routes the event by this value. */
    public abstract int getSource();

    public abstract long getEventTime();

    /** AOSP InputEvent.java:66-68, verbatim and likewise final: the event does
     *  not hold a device, it looks one up by the id it carries. Returning a
     *  flat null (what this used to do) makes every
     *  getDevice().getSources()/getMotionRange() call site NPE, which is how a
     *  gamepad- or stylus-aware app decides it has no input hardware at all.
     *
     *  Note the id contract this depends on: AOSP reserves deviceId 0 for
     *  "did not come from a physical device", so getDevice() on an event that
     *  left its deviceId at the default correctly answers null. Mac.apk's
     *  synthesized touches therefore carry the touchscreen's real id (see
     *  TouchDispatcher.touchEvent) and its keystrokes carry
     *  KeyCharacterMap.VIRTUAL_KEYBOARD. */
    public final InputDevice getDevice() { return InputDevice.getDevice(getDeviceId()); }

    /** AOSP InputEvent.isFromSource: the event's source is partially or
     *  wholly of the given source class. */
    public boolean isFromSource(int source) {
        return (getSource() & source) == source;
    }

    /** AOSP InputEvent.CREATOR: reads the leading PARCEL_TOKEN_* int that
     *  KeyEvent.writeToParcel (token 1) and MotionEvent.writeToParcel (token 2)
     *  already emit in this tree, and dispatches to the concrete creator.
     *
     *  The token is PEEKED rather than consumed -- the position is restored
     *  before delegating -- because each concrete CREATOR consumes its own token
     *  first, exactly as AOSP's do (`in.readInt(); // skip token`). That keeps
     *  the two paths reading the identical byte layout; consuming it here and
     *  calling a body-only constructor would give InputEvent.CREATOR and
     *  KeyEvent.CREATOR two different, silently incompatible formats.
     *
     *  AOSP throws IllegalStateException on an unrecognised token; reproduced. */
    public static final android.os.Parcelable.Creator<InputEvent> CREATOR =
            new android.os.Parcelable.Creator<InputEvent>() {
        @Override public InputEvent createFromParcel(android.os.Parcel in) {
            final int start = in.dataPosition();
            final int token = in.readInt();
            in.setDataPosition(start);
            if (token == 1) return KeyEvent.CREATOR.createFromParcel(in);
            if (token == 2) return MotionEvent.CREATOR.createFromParcel(in);
            throw new IllegalStateException("Unexpected input event type token in parcel.");
        }
        @Override public InputEvent[] newArray(int size) { return new InputEvent[size]; }
    };
}
