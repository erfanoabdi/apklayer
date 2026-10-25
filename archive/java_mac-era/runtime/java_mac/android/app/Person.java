// android.app.Person -- describes a person (sender, contact) attached
// to a notification (MessagingStyle), shortcut, or conversation.
// AOSP API-28+.
//
// AOSP ref: frameworks/base/core/java/android/app/Person.java. A simple
// immutable data holder built via Person.Builder. We mirror the full
// getter/setter surface; the Icon is held as-is (we don't render it).
package android.app;

public final class Person implements android.os.Parcelable {
    private final CharSequence mName;
    private final android.graphics.drawable.Icon mIcon;
    private final String mUri;
    private final String mKey;
    private final boolean mIsBot;
    private final boolean mIsImportant;

    private Person(Builder b) {
        mName = b.mName;
        mIcon = b.mIcon;
        mUri = b.mUri;
        mKey = b.mKey;
        mIsBot = b.mIsBot;
        mIsImportant = b.mIsImportant;
    }

    public CharSequence getName() { return mName; }
    public android.graphics.drawable.Icon getIcon() { return mIcon; }
    public String getUri() { return mUri; }
    public String getKey() { return mKey; }
    public boolean isBot() { return mIsBot; }
    public boolean isImportant() { return mIsImportant; }

    /** AOSP: returns a Builder pre-populated with this Person's fields. */
    public Builder toBuilder() {
        return new Builder()
            .setName(mName).setIcon(mIcon).setUri(mUri)
            .setKey(mKey).setBot(mIsBot).setImportant(mIsImportant);
    }

    @Override public int describeContents() { return 0; }

    // AOSP android11-release core/java/android/app/Person.java:166-178.
    //
    // ⚠ The wire order is name, icon, uri, key, IS_IMPORTANT, IS_BOT -- the
    // last two are the REVERSE of this class's field-declaration order (and of
    // the Builder's). AOSP's read side (Person.java:41-50) matches the writer,
    // so the pairing is correct there; copying the declaration order instead
    // would silently swap two booleans and still produce a well-formed Person.
    // That is exactly the class of defect only a field-by-field round trip can
    // see, so both are asserted separately in ParcelCheck (carrier.person.*).
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeCharSequence(mName);
        // mIcon, inline after a present-flag, exactly as AOSP writes it. This
        // was a bare marker with the Icon dropped until 2026-08-13, because
        // android.graphics.drawable.Icon had no Parcelable implementation when
        // this method was written. It has one now (real CREATOR, real
        // writeToParcel), so the stated removal condition is met and the field
        // is carried for real. A dropped Icon is invisible on the wire -- the
        // Person still round-trips well-formed, just with no avatar.
        if (mIcon != null) {
            dest.writeInt(1);
            mIcon.writeToParcel(dest, 0);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mUri);
        dest.writeString(mKey);
        dest.writeBoolean(mIsImportant);
        dest.writeBoolean(mIsBot);
    }

    public static final Creator<Person> CREATOR = new Creator<Person>() {
        @Override public Person createFromParcel(android.os.Parcel s) {
            Builder b = new Builder();
            b.setName(s.readCharSequence());
            if (s.readInt() != 0) {
                b.setIcon(android.graphics.drawable.Icon.CREATOR.createFromParcel(s));
            }
            b.setUri(s.readString());
            b.setKey(s.readString());
            b.setImportant(s.readBoolean());
            b.setBot(s.readBoolean());
            return b.build();
        }
        @Override public Person[] newArray(int size) { return new Person[size]; }
    };

    public static class Builder {
        private CharSequence mName;
        private android.graphics.drawable.Icon mIcon;
        private String mUri;
        private String mKey;
        private boolean mIsBot;
        private boolean mIsImportant;
        public Builder() {}
        public Builder setName(CharSequence name) { mName = name; return this; }
        public Builder setIcon(android.graphics.drawable.Icon icon) { mIcon = icon; return this; }
        public Builder setUri(String uri) { mUri = uri; return this; }
        public Builder setKey(String key) { mKey = key; return this; }
        public Builder setBot(boolean isBot) { mIsBot = isBot; return this; }
        public Builder setImportant(boolean isImportant) { mIsImportant = isImportant; return this; }
        public Person build() { return new Person(this); }
    }
}
