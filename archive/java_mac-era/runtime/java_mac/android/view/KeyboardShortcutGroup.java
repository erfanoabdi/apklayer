package android.view;
import java.util.ArrayList;
import java.util.List;
/** A named group of KeyboardShortcutInfos for the system shortcuts helper.
 *  API 24. Source: frameworks/base/core/java/android/view/KeyboardShortcutGroup.java */
public final class KeyboardShortcutGroup implements android.os.Parcelable {
    private final CharSequence mLabel;
    private final List<KeyboardShortcutInfo> mItems;
    public KeyboardShortcutGroup(CharSequence label, List<KeyboardShortcutInfo> items) {
        mLabel = label; mItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
    public KeyboardShortcutGroup(CharSequence label) { this(label, null); }
    public CharSequence getLabel() { return mLabel; }
    public List<KeyboardShortcutInfo> getItems() { return mItems; }
    public void addItem(KeyboardShortcutInfo item) { mItems.add(item); }

    // ---- Parcelable ----------------------------------------------------
    // AOSP: core/java/android/view/KeyboardShortcutGroup.java (android16-release)
    // writes label, the typed item list, the @hide mSystemGroup flag and the
    // @hide package name. Only the first two are modelled here, so only those
    // two are written.
    private KeyboardShortcutGroup(android.os.Parcel source) {
        mLabel = source.readCharSequence();
        mItems = source.createTypedArrayList(KeyboardShortcutInfo.CREATOR);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeCharSequence(mLabel);
        dest.writeTypedList(mItems);
    }

    public static final android.os.Parcelable.Creator<KeyboardShortcutGroup> CREATOR =
            new android.os.Parcelable.Creator<KeyboardShortcutGroup>() {
        @Override public KeyboardShortcutGroup createFromParcel(android.os.Parcel s) {
            return new KeyboardShortcutGroup(s);
        }
        @Override public KeyboardShortcutGroup[] newArray(int size) {
            return new KeyboardShortcutGroup[size];
        }
    };
}
