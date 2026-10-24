package android.view;
/** One keyboard shortcut (label + key + modifiers) for the system shortcuts
 *  helper. API 24. Source: frameworks/base/core/java/android/view/KeyboardShortcutInfo.java */
public final class KeyboardShortcutInfo implements android.os.Parcelable {
    private final CharSequence mLabel;
    private final char mBaseCharacter;
    private final int mKeycode;
    private final int mModifiers;
    public KeyboardShortcutInfo(CharSequence label, int keycode, int modifiers) {
        mLabel = label; mBaseCharacter = Character.MIN_VALUE; mKeycode = keycode; mModifiers = modifiers;
    }
    public KeyboardShortcutInfo(CharSequence label, char baseCharacter, int modifiers) {
        mLabel = label; mBaseCharacter = baseCharacter; mKeycode = KeyEvent.KEYCODE_UNKNOWN; mModifiers = modifiers;
    }
    public CharSequence getLabel() { return mLabel; }
    public char getBaseCharacter() { return mBaseCharacter; }
    public int getKeycode() { return mKeycode; }
    public int getModifiers() { return mModifiers; }

    // ---- Parcelable ----------------------------------------------------
    // AOSP: core/java/android/view/KeyboardShortcutInfo.java (android16-release)
    // writes label, icon, baseCharacter, keycode, modifiers. We have no icon
    // field (AOSP's is @hide and unset for every app-constructed instance), so
    // the slot is simply absent -- see the note in ResolveInfo about why we do
    // not pad. Activity.onProvideKeyboardShortcuts hands a List of these to the
    // system, which is a Parcel crossing on a device and a Bundle round trip
    // here; a non-Parcelable value reads back null out of a Bundle in this
    // runtime, so the whole list vanished.
    private KeyboardShortcutInfo(android.os.Parcel source) {
        mLabel = source.readCharSequence();
        mBaseCharacter = (char) source.readInt();
        mKeycode = source.readInt();
        mModifiers = source.readInt();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeCharSequence(mLabel);
        dest.writeInt(mBaseCharacter);
        dest.writeInt(mKeycode);
        dest.writeInt(mModifiers);
    }

    public static final android.os.Parcelable.Creator<KeyboardShortcutInfo> CREATOR =
            new android.os.Parcelable.Creator<KeyboardShortcutInfo>() {
        @Override public KeyboardShortcutInfo createFromParcel(android.os.Parcel s) {
            return new KeyboardShortcutInfo(s);
        }
        @Override public KeyboardShortcutInfo[] newArray(int size) {
            return new KeyboardShortcutInfo[size];
        }
    };
}
