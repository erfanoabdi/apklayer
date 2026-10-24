package android.app;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
// android.app.NotificationChannelGroup (API 26+). A named container for a set of
// NotificationChannels. Apps create groups at startup; Mac.apk records them so
// the get/list round-trip is faithful even though we render no system notifs.
public final class NotificationChannelGroup implements android.os.Parcelable {
    private final String id;
    private CharSequence name;
    private String description;
    private boolean blocked;
    private final List<NotificationChannel> channels = new ArrayList<>();
    public NotificationChannelGroup(String id, CharSequence name) { this.id = id; this.name = name; }
    public String getId()                 { return id; }
    public CharSequence getName()         { return name; }
    public String getDescription()        { return description; }
    public void setDescription(String d)  { this.description = d; }
    public boolean isBlocked()            { return blocked; }
    public List<NotificationChannel> getChannels() { return Collections.unmodifiableList(channels); }
    void addChannel(NotificationChannel c) { if (c != null) channels.add(c); }
    @Override public int describeContents() { return 0; }

    // AOSP android11-release core/java/android/app/NotificationChannelGroup
    // .java:115-132. Order: id, name, description, channels, blocked. The id
    // and description use a BYTE present-marker (not an int) and the name is a
    // bare CharSequence with none, so the three cannot be written uniformly.
    //
    // Omitted because this tree has no such field: mUserLockedFields (a @hide
    // int AOSP writes last).
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeByte((byte) (id != null ? 1 : 0));
        if (id != null) dest.writeString(id);
        dest.writeCharSequence(name);
        dest.writeByte((byte) (description != null ? 1 : 0));
        if (description != null) dest.writeString(description);
        dest.writeParcelableList(channels, flags);
        dest.writeBoolean(blocked);
    }

    public static final Creator<NotificationChannelGroup> CREATOR = new Creator<NotificationChannelGroup>() {
        @Override public NotificationChannelGroup createFromParcel(android.os.Parcel s) {
            String gid = s.readByte() != 0 ? s.readString() : null;
            CharSequence gname = s.readCharSequence();
            NotificationChannelGroup g = new NotificationChannelGroup(gid, gname);
            if (s.readByte() != 0) g.description = s.readString();
            // AOSP reads into the existing list the same way (readParcelableList
            // resizes the caller's list in place); `channels` is final, so this
            // is also the only route available.
            s.readParcelableList(g.channels, NotificationChannel.class.getClassLoader());
            g.blocked = s.readBoolean();
            return g;
        }
        @Override public NotificationChannelGroup[] newArray(int size) { return new NotificationChannelGroup[size]; }
    };

    /** AOSP NotificationChannelGroup.clone(): "@return a shallow copy of this
     *  group" -- a PUBLIC override of Object.clone() with a covariant return, so
     *  an app can call it without Cloneable. AOSP copies the id/name/description
     *  and the channel LIST (the NotificationChannel objects themselves are
     *  shared, matching AOSP's cloned = new NotificationChannelGroup(getId(),
     *  getName()); cloned.setDescription(...); cloned.setBlocked(...);
     *  cloned.setChannels(getChannels())). */
    @Override
    public NotificationChannelGroup clone() {
        NotificationChannelGroup cloned = new NotificationChannelGroup(getId(), getName());
        cloned.setDescription(getDescription());
        cloned.blocked = this.blocked;
        cloned.channels.addAll(this.channels);
        return cloned;
    }
}
