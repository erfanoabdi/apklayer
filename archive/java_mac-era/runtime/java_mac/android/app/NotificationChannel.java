// android.app.NotificationChannel -- AOSP API-26+. Notification
// channels group notifications by importance for user customization.
// Mac.apk stub: stores fields, no real notification system.
package android.app;

public class NotificationChannel implements android.os.Parcelable {
    public static final String DEFAULT_CHANNEL_ID = "miscellaneous";
    public static final int USER_LOCKED_PRIORITY     = 0x00000001;
    public static final int USER_LOCKED_VISIBILITY   = 0x00000002;
    public static final int USER_LOCKED_IMPORTANCE   = 0x00000004;
    public static final int USER_LOCKED_LIGHTS       = 0x00000008;
    public static final int USER_LOCKED_VIBRATION    = 0x00000010;
    public static final int USER_LOCKED_SOUND        = 0x00000020;

    private final String id;
    private CharSequence name;
    private int importance;
    private String description;
    private android.net.Uri sound;
    private android.media.AudioAttributes audioAttributes;
    private boolean showBadge = true;
    private boolean canBypassDnd;
    private boolean enableLights;
    private boolean enableVibration;
    private int lightColor;
    private long[] vibrationPattern;
    private int lockscreenVisibility = Notification.VISIBILITY_PUBLIC;
    private String group;

    public NotificationChannel(String id, CharSequence name, int importance) {
        this.id = id; this.name = name; this.importance = importance;
    }

    public String getId() { return id; }
    public CharSequence getName() { return name; }
    public void setName(CharSequence name) { this.name = name; }
    public int getImportance() { return importance; }
    public void setImportance(int importance) { this.importance = importance; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public android.net.Uri getSound() { return sound; }
    public android.media.AudioAttributes getAudioAttributes() { return audioAttributes; }
    public void setSound(android.net.Uri sound, android.media.AudioAttributes audioAttributes) {
        this.sound = sound; this.audioAttributes = audioAttributes;
    }
    public boolean canShowBadge() { return showBadge; }
    public void setShowBadge(boolean show) { this.showBadge = show; }
    public boolean canBypassDnd() { return canBypassDnd; }
    public void setBypassDnd(boolean bypass) { this.canBypassDnd = bypass; }
    public boolean shouldShowLights() { return enableLights; }
    public void enableLights(boolean lights) { this.enableLights = lights; }
    public int getLightColor() { return lightColor; }
    public void setLightColor(int argb) { this.lightColor = argb; }
    public boolean shouldVibrate() { return enableVibration; }
    public void enableVibration(boolean vibration) { this.enableVibration = vibration; }
    public long[] getVibrationPattern() { return vibrationPattern; }
    public void setVibrationPattern(long[] vibrationPattern) { this.vibrationPattern = vibrationPattern; }
    public int getLockscreenVisibility() { return lockscreenVisibility; }
    public void setLockscreenVisibility(int v) { this.lockscreenVisibility = v; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name != null ? name.toString() : null);
        dest.writeInt(importance);
    }
    public static final Creator<NotificationChannel> CREATOR = new Creator<NotificationChannel>() {
        @Override public NotificationChannel createFromParcel(android.os.Parcel s) {
            return new NotificationChannel(s.readString(), s.readString(), s.readInt());
        }
        @Override public NotificationChannel[] newArray(int size) { return new NotificationChannel[size]; }
    };

    // Conversation support (API 30). Mac.apk has no shade, so these are held
    // values an app round-trips.
    private String mConversationId;
    private String mParentChannelId;
    private boolean mImportantConversation;
    public void setConversationId(String parentChannelId, String conversationId) {
        mParentChannelId = parentChannelId; mConversationId = conversationId;
    }
    public String getConversationId() { return mConversationId; }
    public String getParentChannelId() { return mParentChannelId; }
    public boolean isImportantConversation() { return mImportantConversation; }
    // AOSP core/java/android/app/NotificationChannel.java (android13-release)
    // :618-620/:784-786. Real AOSP backs this with a tri-state int (mAllowBubbles:
    // DEFAULT_ALLOW_BUBBLE=-1 / ALLOW_BUBBLE_ON=1 / ALLOW_BUBBLE_OFF=0) plus a
    // @hide int setter/getter -- but the DEFAULT state is observably identical to
    // OFF through the PUBLIC surface (canBubble() == (mAllowBubbles == ON) is
    // false for both -1 and 0), and the int overloads are @hide so no APK
    // compiled against the public SDK can reach them anyway. A plain boolean
    // reproduces the exact public contract. Same defect shape as
    // canShowBadge()/setShowBadge() above: canBubble() was hardcoded `return
    // false` with no backing field and no setAllowBubbles() at all, so any app
    // that called setAllowBubbles(true) then canBubble() (thunderbird, vlc) got
    // a silent no-op instead of true.
    private boolean allowBubbles;
    public boolean canBubble() { return allowBubbles; }
    public void setAllowBubbles(boolean allowBubbles) { this.allowBubbles = allowBubbles; }

    public void setVibrationEffect(android.os.VibrationEffect effect) { }
    public android.os.VibrationEffect getVibrationEffect() { return null; }

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/NotificationChannel.java
    // (android16-release).
    // ================================================================

    /** AOSP's mUserLockedFields: the USER_LOCKED_* bitmask recording which
     *  channel settings the USER has changed by hand, which the system then
     *  refuses to let the app override. Mac.apk has no notification settings UI,
     *  so nothing is ever user-locked -- but the field is what
     *  hasUserSetImportance / hasUserSetSound read, so it is real state rather
     *  than two constants. */
    private int mUserLockedFields;

    /** AOSP hasUserSetImportance(): {@code (mUserLockedFields &
     *  USER_LOCKED_IMPORTANCE) != 0} -- "whether the user has chosen the
     *  importance of this channel, either to affirm the initial selection from
     *  the app, or changed it to be higher or lower." */
    public boolean hasUserSetImportance() {
        return (mUserLockedFields & USER_LOCKED_IMPORTANCE) != 0;
    }

    /** AOSP hasUserSetSound(): {@code (mUserLockedFields & USER_LOCKED_SOUND)
     *  != 0} -- "whether the user has chosen the sound of this channel." */
    public boolean hasUserSetSound() {
        return (mUserLockedFields & USER_LOCKED_SOUND) != 0;
    }

    /** AOSP isBlockable(): {@code return mBlockableSystem;} -- whether this
     *  channel may be blocked. Only meaningful for system apps (a normal app's
     *  channels are always blockable); AOSP's default for the field is false. */
    public boolean isBlockable() { return mBlockableSystem; }

    /** AOSP setBlockable(boolean): the setter for the above. @hide-adjacent in
     *  AOSP's javadoc but part of the public API surface. */
    public void setBlockable(boolean blockable) { mBlockableSystem = blockable; }
    private boolean mBlockableSystem;

    /** AOSP isConversation(): {@code !TextUtils.isEmpty(getConversationId())} --
     *  "Whether or not this channel represents a conversation." */
    public boolean isConversation() {
        String conversationId = getConversationId();
        return conversationId != null && conversationId.length() > 0;
    }

    /** AOSP isDemoted(): {@code return mDemoted;} -- "Whether or not this
     *  channel represents a conversation that is demoted from the conversation
     *  section" (the user moved it back to normal notifications). Only the
     *  system's notification settings can demote a channel, and there is none
     *  here, so no channel is ever demoted. */
    public boolean isDemoted() { return false; }

    // ---- AOSP API-36 constant completion (android.app.NotificationChannel) ----
    // Values extracted verbatim from the AOSP source for this class
    // (android16-release). javac INLINES a compile-time constant into the app at
    // ITS build time, so a shipped APK cannot observe a missing one at runtime --
    // these are here for our own framework code, for anything compiled against
    // THIS jar, and so the surface is complete rather than nearly complete.
    public static final String EDIT_CONVERSATION = "conversation";
    public static final String EDIT_IMPORTANCE = "importance";
    public static final String EDIT_LAUNCHER = "launcher";
    public static final String EDIT_LOCKED_DEVICE = "locked";
    public static final String EDIT_SOUND = "sound";
    public static final String EDIT_VIBRATION = "vibration";
    public static final String EDIT_ZEN = "zen";
}
