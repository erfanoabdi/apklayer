package android.content.pm;

/** android.content.pm.Capability (API 33) -- the NAME of a shortcut capability,
 *  e.g. "actions.intent.SEND_MESSAGE". Paired with CapabilityParams in
 *  ShortcutInfo.Builder.addCapabilityBinding to tell the assistant which
 *  built-in intent a shortcut fulfils.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/Capability.java
 *  (android16-release): one final String plus Parcelable. */
public final class Capability implements android.os.Parcelable {
    private final String mName;

    private Capability(String name) { mName = name; }

    /** AOSP: "the name of the capability." */
    public String getName() { return mName; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mName);
    }

    public static final android.os.Parcelable.Creator<Capability> CREATOR =
            new android.os.Parcelable.Creator<Capability>() {
        @Override public Capability createFromParcel(android.os.Parcel in) {
            return new Capability(in.readString());
        }
        @Override public Capability[] newArray(int size) { return new Capability[size]; }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Capability)) return false;
        return java.util.Objects.equals(mName, ((Capability) o).mName);
    }
    @Override public int hashCode() { return java.util.Objects.hashCode(mName); }

    /** AOSP Capability.Builder -- rejects an empty name, as upstream does. */
    public static final class Builder {
        private final String mName;
        public Builder(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Capability name cannot be empty");
            }
            mName = name;
        }
        public Capability build() { return new Capability(mName); }
    }
}
