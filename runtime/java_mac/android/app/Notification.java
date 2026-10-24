// android.app.Notification -- system notification descriptor. AOSP
// API-1+ (extensively extended in subsequent APIs). F-Droid uses
// this for foreground-service ongoing notifications + update-ready
// alerts. Stubbed; Mac.apk doesn't show Android notifications, but
// the class must exist + be constructible to satisfy app code.
package android.app;

public class Notification implements android.os.Parcelable {
    public static final int FLAG_ONGOING_EVENT       = 0x00000002;
    public static final int FLAG_INSISTENT           = 0x00000004;
    public static final int FLAG_ONLY_ALERT_ONCE     = 0x00000008;
    public static final int FLAG_AUTO_CANCEL         = 0x00000010;
    public static final int FLAG_NO_CLEAR            = 0x00000020;
    public static final int FLAG_FOREGROUND_SERVICE  = 0x00000040;
    public static final int FLAG_HIGH_PRIORITY       = 0x00000080;
    public static final int FLAG_LOCAL_ONLY          = 0x00000100;
    public static final int FLAG_GROUP_SUMMARY       = 0x00000200;
    public static final int FLAG_BUBBLE              = 0x00001000;

    // Notification "extras" bundle keys (real AOSP values --
    // frameworks/base/core/java/android/app/Notification.java). The Unity
    // Mobile Notifications plugin (`com.unity.mobile.notifications`,
    // bundled in Subway Surfers + many Unity games) reflectively reads
    // these off android.app.Notification at init (its NotificationJni
    // CollectConstants/CollectFields). A single missing field throws
    // NoSuchFieldError, which fails the plugin's init stage and HANGS the
    // game's bootstrapper on the splash (Subway: SYBO Bootstrapper stalls
    // on EXTRA_TITLE). They are plain string keys into the `extras` bundle.
    public static final String EXTRA_TITLE          = "android.title";
    public static final String EXTRA_TITLE_BIG      = "android.title.big";
    public static final String EXTRA_TEXT           = "android.text";
    public static final String EXTRA_SUB_TEXT       = "android.subText";
    public static final String EXTRA_INFO_TEXT      = "android.infoText";
    public static final String EXTRA_SUMMARY_TEXT   = "android.summaryText";
    public static final String EXTRA_BIG_TEXT       = "android.bigText";
    public static final String EXTRA_SHOW_CHRONOMETER = "android.showChronometer";
    public static final String EXTRA_CHRONOMETER_COUNT_DOWN = "android.chronometerCountDown";
    public static final String EXTRA_SHOW_WHEN      = "android.showWhen";
    public static final String EXTRA_PROGRESS       = "android.progress";
    public static final String EXTRA_PROGRESS_MAX   = "android.progressMax";
    public static final String EXTRA_PROGRESS_INDETERMINATE = "android.progressIndeterminate";
    public static final String EXTRA_SMALL_ICON     = "android.icon";
    public static final String EXTRA_LARGE_ICON     = "android.largeIcon";
    public static final String EXTRA_LARGE_ICON_BIG = "android.largeIcon.big";
    public static final String EXTRA_PICTURE        = "android.picture";
    public static final String EXTRA_TEXT_LINES     = "android.textLines";
    public static final String EXTRA_TEMPLATE       = "android.template";
    public static final String EXTRA_PEOPLE         = "android.people";
    public static final String EXTRA_COLORIZED      = "android.colorized";
    // EXTRA_CHANNEL_ID / EXTRA_NOTIFICATION_ID are NOT extras-bundle keys like
    // the rest of this block -- AOSP Notification.java (android13-release)
    // :199/:220 defines them as Intent-extra keys for
    // INTENT_CATEGORY_NOTIFICATION_PREFERENCES (the "open notification
    // settings for this channel/id" intent). We previously guessed values
    // following the EXTRA_TITLE-style "android.xyz" pattern above, which is
    // the wrong namespace; fixed to the real values so reflective readers
    // (and any app building a NOTIFICATION_PREFERENCES intent by hand) see
    // what AOSP actually ships.
    public static final String EXTRA_CHANNEL_ID     = "android.intent.extra.CHANNEL_ID";
    public static final String EXTRA_NOTIFICATION_ID = "android.intent.extra.NOTIFICATION_ID";

    // Group-alert-behavior constants (read by setGroupAlertBehavior callers
    // + the same Unity notifications plugin). AOSP API-26 values.
    public static final int GROUP_ALERT_ALL      = 0;
    public static final int GROUP_ALERT_SUMMARY  = 1;
    public static final int GROUP_ALERT_CHILDREN = 2;

    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_LOW     = -1;
    public static final int PRIORITY_MIN     = -2;
    public static final int PRIORITY_HIGH    = 1;
    public static final int PRIORITY_MAX     = 2;

    public static final int DEFAULT_ALL    = -1;
    public static final int DEFAULT_SOUND  = 1;
    public static final int DEFAULT_VIBRATE= 2;
    public static final int DEFAULT_LIGHTS = 4;

    public static final int VISIBILITY_PUBLIC  = 1;
    public static final int VISIBILITY_PRIVATE = 0;
    public static final int VISIBILITY_SECRET  = -1;

    public static final String CATEGORY_CALL      = "call";
    public static final String CATEGORY_MESSAGE   = "msg";
    public static final String CATEGORY_EMAIL     = "email";
    public static final String CATEGORY_EVENT     = "event";
    public static final String CATEGORY_PROMO     = "promo";
    public static final String CATEGORY_ALARM     = "alarm";
    public static final String CATEGORY_PROGRESS  = "progress";
    public static final String CATEGORY_SOCIAL    = "social";
    public static final String CATEGORY_ERROR     = "err";
    public static final String CATEGORY_TRANSPORT = "transport";
    public static final String CATEGORY_SYSTEM    = "sys";
    public static final String CATEGORY_SERVICE   = "service";
    public static final String CATEGORY_REMINDER  = "reminder";
    public static final String CATEGORY_RECOMMENDATION = "recommendation";
    public static final String CATEGORY_STATUS    = "status";

    public int icon;
    /** AOSP API-1 deprecated-but-still-public fields needed by
     *  androidx.core.app.NotificationCompatBuilder's <init> -- it
     *  reflectively reads each one into the underlying platform
     *  Notification when copying state from a NotificationCompat
     *  .Builder. Absent any of these, NotificationCompat.Builder
     *  .build() throws NoSuchFieldError before producing the
     *  Notification. Hit by F-Droid's RepoUpdateWorker.getForegroundInfo()
     *  (a CoroutineWorker's foreground-info construction); the worker
     *  fails on its very first invocation. We provide the public
     *  fields with reasonable defaults; the values are never read
     *  by Mac.apk since we don't actually display Android
     *  notifications, but the JVM verifier resolves the field
     *  symbols at class link time. */
    public int iconLevel;
    public android.widget.RemoteViews contentView;
    public android.widget.RemoteViews bigContentView;
    public android.widget.RemoteViews headsUpContentView;
    public android.widget.RemoteViews tickerView;
    public android.graphics.Bitmap largeIcon;
    public int visibility;
    public int color;
    public CharSequence tickerText;
    public android.app.PendingIntent contentIntent;
    public android.app.PendingIntent deleteIntent;
    public android.app.PendingIntent fullScreenIntent;
    public android.net.Uri sound;
    public android.media.AudioAttributes audioAttributes;
    public int audioStreamType;
    public long[] vibrate;
    public int ledARGB;
    public int ledOnMS;
    public int ledOffMS;
    public int defaults;
    public int flags;
    public int priority;
    public int number;
    public long when;
    public String category;
    public String group;
    public String sortKey;
    // AOSP core/java/android/app/Notification.java (android13-release) :1625
    // (field, default true) / :3647-3648 (getter). Set via
    // Builder.setAllowSystemGeneratedContextualActions(boolean) (":6515-6517",
    // which on real AOSP writes straight into the live mN instance the
    // Builder wraps -- we follow this file's own Builder.build()-transfers-
    // accumulated-fields convention instead, see build() below).
    public boolean allowSystemGeneratedContextualActions = true;
    public android.os.Bundle extras;
    // Backing fields for the AOSP getters below (read by the Unity
    // notifications plugin's reconstruction path). Defaults are inert.
    public String channelId;
    public int groupAlertBehavior;       // GROUP_ALERT_ALL
    public CharSequence settingsText;
    public long timeoutAfter;
    public int badgeIconType;
    public String shortcutId;
    public android.graphics.drawable.Icon smallIcon;
    public android.graphics.drawable.Icon largeIconObj;
    /** AOSP public field: the action buttons attached to the
     *  notification. Populated by Builder.build() from addAction. */
    public Action[] actions;

    /** AOSP API-21+ default audio attributes for notifications.
     *  F-Droid's NotificationManager init reads this static field
     *  during channel setup. */
    public static final android.media.AudioAttributes AUDIO_ATTRIBUTES_DEFAULT =
        new android.media.AudioAttributes.Builder()
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .build();

    public Notification() {}
    public Notification(int icon, CharSequence tickerText, long when) {
        this.icon = icon; this.tickerText = tickerText; this.when = when;
    }

    // AOSP public getters. The Unity Mobile Notifications plugin
    // (NotificationBuilderJni) reads back a built Notification's
    // properties via these JNI methods when it reconstructs a scheduled
    // notification; a missing getter throws NoSuchMethodError and fails
    // the plugin's init stage (Subway: getGroup() was the next blocker
    // after EXTRA_TITLE). The values mirror the public fields; Mac.apk
    // does not display Android notifications, so they are reconstruction
    // bookkeeping only.
    public String getGroup()              { return group; }
    public String getSortKey()            { return sortKey; }
    public String getChannelId()          { return channelId; }
    public int    getGroupAlertBehavior() { return groupAlertBehavior; }
    public CharSequence getSettingsText() { return settingsText; }
    public long   getTimeoutAfter()       { return timeoutAfter; }
    public int    getBadgeIconType()      { return badgeIconType; }
    public String getShortcutId()         { return shortcutId; }
    public android.graphics.drawable.Icon getSmallIcon() { return smallIcon; }
    public android.graphics.drawable.Icon getLargeIcon() { return largeIconObj; }
    public android.os.Bundle getExtras()  { return extras != null ? extras : new android.os.Bundle(); }

    // ================================================================
    // AOSP API-36 surface completion (android.app.Notification).
    // AOSP ref: frameworks/base/core/java/android/app/Notification.java
    // (android16-release). These are QUERIES over state this class already
    // holds, so they are implemented for real rather than stubbed -- a
    // notification-inspecting app (and androidx's NotificationCompat) reads them
    // off a Notification it built itself, which works exactly as on a device.
    // ================================================================

    /** AOSP EXTRA_MESSAGES / EXTRA_HISTORIC_MESSAGES / EXTRA_BACKGROUND_IMAGE_URI /
     *  EXTRA_SHORT_CRITICAL_TEXT: the extras keys the queries below read. */
    public static final String EXTRA_MESSAGES = "android.messages";
    public static final String EXTRA_HISTORIC_MESSAGES = "android.messages.historic";
    public static final String EXTRA_BACKGROUND_IMAGE_URI = "android.backgroundImageUri";
    public static final String EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText";

    /** AOSP Notification.clone(): "@return a shallow copy of this notification"
     *  -- AOSP's body is {@code Notification that = new Notification();
     *  cloneInto(that, true); return that;}, where cloneInto with heavy=true
     *  deep-copies the RemoteViews, the large-icon Bitmap and the extras Bundle
     *  and shares everything else. Mirrored field-for-field over the fields this
     *  class declares. Note this is a PUBLIC override of Object.clone() with a
     *  covariant return, as AOSP declares it, so an app can call it without
     *  Cloneable and without a cast. */
    @Override
    public Notification clone() {
        Notification that = new Notification();
        that.when = this.when;
        that.icon = this.icon;
        that.iconLevel = this.iconLevel;
        that.number = this.number;
        that.contentIntent = this.contentIntent;
        that.deleteIntent = this.deleteIntent;
        that.fullScreenIntent = this.fullScreenIntent;
        that.tickerText = this.tickerText;
        // AOSP's heavy clone calls RemoteViews.clone() on each of these four;
        // android.widget.RemoteViews in this tree exposes no clone(), so they
        // are shared -- which is AOSP's own light-clone behaviour.
        that.tickerView = this.tickerView;
        that.contentView = this.contentView;
        that.bigContentView = this.bigContentView;
        that.headsUpContentView = this.headsUpContentView;
        that.largeIcon = this.largeIcon;
        that.smallIcon = this.smallIcon;
        that.largeIconObj = this.largeIconObj;
        that.sound = this.sound;
        that.audioStreamType = this.audioStreamType;
        that.audioAttributes = this.audioAttributes;
        that.vibrate = (this.vibrate != null) ? this.vibrate.clone() : null;
        that.ledARGB = this.ledARGB;
        that.ledOnMS = this.ledOnMS;
        that.ledOffMS = this.ledOffMS;
        that.defaults = this.defaults;
        that.flags = this.flags;
        that.priority = this.priority;
        that.category = this.category;
        that.group = this.group;
        that.sortKey = this.sortKey;
        that.visibility = this.visibility;
        that.color = this.color;
        that.channelId = this.channelId;
        that.groupAlertBehavior = this.groupAlertBehavior;
        that.settingsText = this.settingsText;
        that.timeoutAfter = this.timeoutAfter;
        that.badgeIconType = this.badgeIconType;
        that.shortcutId = this.shortcutId;
        that.allowSystemGeneratedContextualActions = this.allowSystemGeneratedContextualActions;
        // The extras Bundle is COPIED (AOSP's heavy clone does the same), so
        // the two notifications do not share mutable state.
        that.extras = (this.extras != null) ? new android.os.Bundle(this.extras) : null;
        if (this.actions != null) {
            that.actions = new Action[this.actions.length];
            for (int i = 0; i < this.actions.length; i++) {
                that.actions[i] = (this.actions[i] != null) ? this.actions[i].clone() : null;
            }
        }
        return that;
    }

    /** AOSP findRemoteInputActionPair(boolean requiresFreeform): "Finds and
     *  returns a remote input and its corresponding action... @return the pair,
     *  or null if none." Body mirrored, including the detail that the LAST
     *  qualifying RemoteInput on an action wins. */
    public android.util.Pair<RemoteInput, Action> findRemoteInputActionPair(
            boolean requiresFreeform) {
        if (actions == null) return null;
        for (Action action : actions) {
            if (action == null || action.getRemoteInputs() == null) continue;
            RemoteInput resultRemoteInput = null;
            for (RemoteInput remoteInput : action.getRemoteInputs()) {
                if (remoteInput == null) continue;
                if (remoteInput.getAllowFreeFormInput() || !requiresFreeform) {
                    resultRemoteInput = remoteInput;
                }
            }
            if (resultRemoteInput != null) {
                return android.util.Pair.create(resultRemoteInput, action);
            }
        }
        return null;
    }

    /** AOSP getContextualActions(): "@return the actions that are contextual
     *  out of the actions in this notification" -- never null. */
    public java.util.List<Action> getContextualActions() {
        java.util.List<Action> contextualActions = new java.util.ArrayList<Action>();
        if (actions == null) return contextualActions;
        for (Action action : actions) {
            if (action != null && action.isContextual()) contextualActions.add(action);
        }
        return contextualActions;
    }

    /** AOSP getShortCriticalText() (API 36): the short always-visible text of a
     *  rich ongoing notification, read from EXTRA_SHORT_CRITICAL_TEXT; null when
     *  the builder set none. */
    public String getShortCriticalText() {
        return extras != null ? extras.getString(EXTRA_SHORT_CRITICAL_TEXT) : null;
    }

    /** AOSP hasImage(): "@return true if the notification has image". Body
     *  mirrored: for a MessagingStyle notification, any message whose data is an
     *  image/* Uri counts and nothing else does; otherwise a large icon or a
     *  background-image extra counts. */
    public boolean hasImage() {
        if (extras != null && extras.containsKey(EXTRA_MESSAGES)) {
            android.os.Parcelable[] messages = extras.getParcelableArray(EXTRA_MESSAGES);
            if (messages != null && messages.length > 0) {
                for (MessagingStyle.Message m
                        : MessagingStyle.Message.getMessagesFromBundleArray(messages)) {
                    if (m.getDataUri() != null && m.getDataMimeType() != null
                            && m.getDataMimeType().startsWith("image/")) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (largeIconObj != null || largeIcon != null) return true;
        if (extras != null && extras.containsKey(EXTRA_BACKGROUND_IMAGE_URI)) return true;
        return false;
    }

    /** AOSP Notification.Extender: "Extender interface for use with
     *  Builder#extend. Extenders may be used to add metadata or change options
     *  on a notification builder." */
    public interface Extender {
        Builder extend(Builder builder);
    }


    @Override public int describeContents() { return 0; }

    // ------------------------------------------------------------------
    // Marshalling. AOSP android11-release
    // core/java/android/app/Notification.java:2678-2846 (writeToParcelImpl)
    // and :2244+ (readFromParcelImpl). A Notification crosses a parcel on
    // every post on a real device, so this is the highest-traffic writer in
    // the class -- and until it existed, Parcel's empty-writer fallback kept
    // the LIVE object instead, which is the aliasing bug the real-marshalling
    // rewrite removed everywhere else.
    //
    // The field ORDER below is AOSP's, not this class's declaration order. A
    // transposed pair still yields a well-formed Notification, so it can only
    // be caught by a field-by-field round trip -- see ParcelCheck's
    // carrier.notification.* rows.
    //
    // Three deliberate deviations from AOSP's body, each marked at its site:
    //
    //   1. AOSP writes mWhitelistToken (a Binder) and creationTime. Neither
    //      field is declared on this tree, so neither is written. Adding a
    //      placeholder slot for a field we do not have would be inventing wire
    //      format, so the slots are simply absent.
    //   2. Every Icon slot is an ABSENT-MARKER (a present-flag with no
    //      payload) because android.graphics.drawable.Icon does not implement
    //      Parcelable on this tree.
    //   3. AOSP does not write the deprecated `icon`/`iconLevel`-style int
    //      resource ids for small/large icons -- it recovers `icon` from
    //      mSmallIcon.getResId(). With the Icon unable to travel, that route
    //      is gone, so `icon` is written explicitly in the small-icon slot and
    //      the Bitmap `largeIcon` in the large-icon slot. Both ARE fields this
    //      tree declares, and carrying them is what preserves the observable
    //      AOSP behaviour (icon survives the trip) using the parts we have.
    //
    // AOSP uses writeString8 for category/group/sortKey/channelId/shortcutId;
    // that is a @hide UTF-8 variant with no separate reader here, and
    // writeString is the same value with a different encoding, so the pair
    // stays symmetric.
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        // AOSP's leading "parcelled version" word. Kept even though nothing
        // branches on it: it is the first slot of the format, and a reader
        // that did not consume it would desync every field after it.
        dest.writeInt(1);
        dest.writeLong(when);
        // mSmallIcon ABSENT-MARKER + the deprecated `icon` resource id.
        // REMOVAL CONDITION for every Icon marker in this file: when Icon
        // implements Parcelable, replace the flag with
        // dest.writeTypedObject(<icon>, flags) / readTypedObject(Icon.CREATOR)
        // and drop the explicit int carried alongside it (AOSP recovers it
        // from the Icon).
        dest.writeInt(smallIcon != null ? 1 : 0);
        dest.writeInt(icon);
        dest.writeInt(number);
        dest.writeInt(contentIntent != null ? 1 : 0);
        if (contentIntent != null) contentIntent.writeToParcel(dest, 0);
        dest.writeInt(deleteIntent != null ? 1 : 0);
        if (deleteIntent != null) deleteIntent.writeToParcel(dest, 0);
        dest.writeInt(tickerText != null ? 1 : 0);
        if (tickerText != null) dest.writeCharSequence(tickerText);
        dest.writeInt(tickerView != null ? 1 : 0);
        if (tickerView != null) tickerView.writeToParcel(dest, 0);
        dest.writeInt(contentView != null ? 1 : 0);
        if (contentView != null) contentView.writeToParcel(dest, 0);
        // mLargeIcon ABSENT-MARKER (Icon) + the Bitmap AOSP folds into it.
        // Bitmap's own writeToParcel keeps a live reference rather than
        // flattening pixels (see Bitmap.java); that is this tree's existing
        // Bitmap contract, not something this writer chooses.
        dest.writeInt(largeIconObj != null ? 1 : 0);
        dest.writeTypedObject(largeIcon, flags);
        dest.writeInt(defaults);
        dest.writeInt(this.flags);
        dest.writeInt(sound != null ? 1 : 0);
        if (sound != null) sound.writeToParcel(dest, 0);
        dest.writeInt(audioStreamType);
        dest.writeInt(audioAttributes != null ? 1 : 0);
        if (audioAttributes != null) audioAttributes.writeToParcel(dest, 0);
        dest.writeLongArray(vibrate);
        dest.writeInt(ledARGB);
        dest.writeInt(ledOnMS);
        dest.writeInt(ledOffMS);
        dest.writeInt(iconLevel);
        dest.writeInt(fullScreenIntent != null ? 1 : 0);
        if (fullScreenIntent != null) fullScreenIntent.writeToParcel(dest, 0);
        dest.writeInt(priority);
        dest.writeString(category);
        dest.writeString(group);
        dest.writeString(sortKey);
        dest.writeBundle(extras);
        dest.writeTypedArray(actions, 0);
        dest.writeInt(bigContentView != null ? 1 : 0);
        if (bigContentView != null) bigContentView.writeToParcel(dest, 0);
        dest.writeInt(headsUpContentView != null ? 1 : 0);
        if (headsUpContentView != null) headsUpContentView.writeToParcel(dest, 0);
        dest.writeInt(visibility);
        // Recursive by design: AOSP writes the public version inline the same
        // way. Notifications nest at most one level (a public version has no
        // public version of its own), so there is no unbounded recursion.
        dest.writeInt(publicVersion != null ? 1 : 0);
        if (publicVersion != null) publicVersion.writeToParcel(dest, 0);
        dest.writeInt(color);
        dest.writeInt(channelId != null ? 1 : 0);
        if (channelId != null) dest.writeString(channelId);
        dest.writeLong(timeoutAfter);
        dest.writeInt(shortcutId != null ? 1 : 0);
        if (shortcutId != null) dest.writeString(shortcutId);
        dest.writeInt(mLocusId != null ? 1 : 0);
        if (mLocusId != null) mLocusId.writeToParcel(dest, 0);
        dest.writeInt(badgeIconType);
        dest.writeInt(settingsText != null ? 1 : 0);
        if (settingsText != null) dest.writeCharSequence(settingsText);
        dest.writeInt(groupAlertBehavior);
        dest.writeInt(mBubbleMetadata != null ? 1 : 0);
        if (mBubbleMetadata != null) mBubbleMetadata.writeToParcel(dest, 0);
        dest.writeBoolean(allowSystemGeneratedContextualActions);
    }

    /** Read side of writeToParcel; mirrors AOSP's public Notification(Parcel)
     *  (Notification.java:2236-2240). Every slot the writer produced is
     *  consumed in the same order, including the markers whose payload this
     *  tree cannot carry -- skipping one would shift every field after it. */
    public Notification(android.os.Parcel parcel) {
        parcel.readInt();                       // format version word
        when = parcel.readLong();
        parcel.readInt();                       // smallIcon ABSENT-MARKER
        icon = parcel.readInt();
        number = parcel.readInt();
        if (parcel.readInt() != 0) contentIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        if (parcel.readInt() != 0) deleteIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        if (parcel.readInt() != 0) tickerText = parcel.readCharSequence();
        if (parcel.readInt() != 0) tickerView = android.widget.RemoteViews.CREATOR.createFromParcel(parcel);
        if (parcel.readInt() != 0) contentView = android.widget.RemoteViews.CREATOR.createFromParcel(parcel);
        parcel.readInt();                       // largeIconObj ABSENT-MARKER
        largeIcon = parcel.readTypedObject(android.graphics.Bitmap.CREATOR);
        defaults = parcel.readInt();
        this.flags = parcel.readInt();
        if (parcel.readInt() != 0) sound = android.net.Uri.CREATOR.createFromParcel(parcel);
        audioStreamType = parcel.readInt();
        if (parcel.readInt() != 0) {
            audioAttributes = android.media.AudioAttributes.CREATOR.createFromParcel(parcel);
        }
        vibrate = parcel.createLongArray();
        ledARGB = parcel.readInt();
        ledOnMS = parcel.readInt();
        ledOffMS = parcel.readInt();
        iconLevel = parcel.readInt();
        if (parcel.readInt() != 0) fullScreenIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        priority = parcel.readInt();
        category = parcel.readString();
        group = parcel.readString();
        sortKey = parcel.readString();
        extras = parcel.readBundle();
        actions = parcel.createTypedArray(Action.CREATOR);
        if (parcel.readInt() != 0) bigContentView = android.widget.RemoteViews.CREATOR.createFromParcel(parcel);
        if (parcel.readInt() != 0) headsUpContentView = android.widget.RemoteViews.CREATOR.createFromParcel(parcel);
        visibility = parcel.readInt();
        if (parcel.readInt() != 0) publicVersion = new Notification(parcel);
        color = parcel.readInt();
        if (parcel.readInt() != 0) channelId = parcel.readString();
        timeoutAfter = parcel.readLong();
        if (parcel.readInt() != 0) shortcutId = parcel.readString();
        if (parcel.readInt() != 0) mLocusId = android.content.LocusId.CREATOR.createFromParcel(parcel);
        badgeIconType = parcel.readInt();
        if (parcel.readInt() != 0) settingsText = parcel.readCharSequence();
        groupAlertBehavior = parcel.readInt();
        if (parcel.readInt() != 0) mBubbleMetadata = BubbleMetadata.CREATOR.createFromParcel(parcel);
        allowSystemGeneratedContextualActions = parcel.readBoolean();
    }

    public static final Creator<Notification> CREATOR = new Creator<Notification>() {
        @Override public Notification createFromParcel(android.os.Parcel s) { return new Notification(s); }
        @Override public Notification[] newArray(int size) { return new Notification[size]; }
    };

    public static class Builder {
        private final android.content.Context context;
        // v1.0: Builder now RETAINS state and build() populates the
        // returned Notification (was lossy -- every setter discarded and
        // build() returned a blank object). AOSP's Builder accumulates
        // into the extras bundle + the notification fields; we mirror
        // both so a read-back of build() (the Unity plugin, androidx
        // NotificationCompat) sees the title/text/icon/intents that were
        // set, and the standard EXTRA_* keys round-trip.
        private final android.os.Bundle mExtras = new android.os.Bundle();
        private CharSequence mContentTitle, mContentText, mSubText, mContentInfo, mTicker, mSettingsText;
        private int mSmallIcon, mSmallIconLevel;
        private android.graphics.drawable.Icon mSmallIconObj, mLargeIconObj;
        private android.graphics.Bitmap mLargeBitmap;
        private android.app.PendingIntent mContentIntent, mDeleteIntent, mFullScreenIntent;
        private boolean mFullScreenHighPriority;
        private int mPriority = PRIORITY_DEFAULT;
        private String mCategory, mChannelId, mGroup, mSortKey, mShortcutId;
        private int mVisibility, mColor, mDefaults, mNumber, mBadgeIconType, mGroupAlertBehavior;
        private boolean mColorized;
        private long mWhen = System.currentTimeMillis();
        private boolean mShowWhen = true, mUsesChronometer, mChronometerCountDown;
        private boolean mAutoCancel, mOngoing, mOnlyAlertOnce, mGroupSummary, mLocalOnly;
        private int mProgressMax, mProgress; private boolean mProgressIndeterminate;
        private Style mStyle;
        private long[] mVibrate; private android.net.Uri mSound;
        private int mLedArgb, mLedOnMs, mLedOffMs; private long mTimeoutAfter;
        private final java.util.ArrayList<Action> mActions = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> mPeople = new java.util.ArrayList<>();

        public Builder(android.content.Context c) { this.context = c; }
        public Builder(android.content.Context c, String channelId) { this.context = c; this.mChannelId = channelId; }
        public Builder setContentTitle(CharSequence title)   { mContentTitle = title; return this; }
        public Builder setActions(Action... actions) { mActions.clear(); if (actions != null) for (Action a : actions) mActions.add(a); return this; }
        public Builder setContentText(CharSequence text)     { mContentText = text; return this; }
        public Builder setSmallIcon(int icon)                { mSmallIcon = icon; return this; }
        public Builder setSmallIcon(int icon, int level)     { mSmallIcon = icon; mSmallIconLevel = level; return this; }
        public Builder setSmallIcon(android.graphics.drawable.Icon icon) { mSmallIconObj = icon; return this; }
        public Builder setLargeIcon(android.graphics.Bitmap b) { mLargeBitmap = b; return this; }
        public Builder setLargeIcon(android.graphics.drawable.Icon icon) { mLargeIconObj = icon; return this; }
        public Builder setContentInfo(CharSequence info)     { mContentInfo = info; return this; }
        private BubbleMetadata mBubbleMetadata;
        public Builder setBubbleMetadata(BubbleMetadata bubble) { mBubbleMetadata = bubble; return this; }
        public Builder setLocusId(android.content.LocusId locusId) { return this; }
        public static Builder recoverBuilder(android.content.Context context, Notification n) { return new Builder(context, n != null ? n.channelId : null); }
        public Builder setShortCriticalText(String shortCriticalText) { return this; }
        public Builder setTicker(CharSequence ticker)        { mTicker = ticker; return this; }
        public Builder setTicker(CharSequence ticker, android.widget.RemoteViews views) { mTicker = ticker; return this; }
        public Builder setContentIntent(android.app.PendingIntent i) { mContentIntent = i; return this; }
        public Builder setDeleteIntent(android.app.PendingIntent i)  { mDeleteIntent = i; return this; }
        public Builder setFullScreenIntent(android.app.PendingIntent i, boolean highPriority) { mFullScreenIntent = i; mFullScreenHighPriority = highPriority; return this; }
        public Builder setAutoCancel(boolean cancel)         { mAutoCancel = cancel; return this; }
        public Builder setOngoing(boolean ongoing)           { mOngoing = ongoing; return this; }
        public Builder setOnlyAlertOnce(boolean alert)       { mOnlyAlertOnce = alert; return this; }
        public Builder setPriority(int priority)             { mPriority = priority; return this; }
        public Builder setCategory(String category)          { mCategory = category; return this; }
        public Builder setVisibility(int visibility)         { mVisibility = visibility; return this; }
        public Builder setColor(int argb)                    { mColor = argb; return this; }
        public Builder setColorized(boolean colorized)       { mColorized = colorized; return this; }
        public Builder setDefaults(int defaults)             { mDefaults = defaults; return this; }
        public Builder setWhen(long when)                    { mWhen = when; return this; }
        public Builder setShowWhen(boolean show)             { mShowWhen = show; return this; }
        public Builder setUsesChronometer(boolean uses)      { mUsesChronometer = uses; return this; }
        public Builder setChronometerCountDown(boolean countDown) { mChronometerCountDown = countDown; return this; }
        public Builder setNumber(int number)                 { mNumber = number; return this; }
        public Builder setProgress(int max, int progress, boolean indeterminate) { mProgressMax = max; mProgress = progress; mProgressIndeterminate = indeterminate; return this; }
        public Builder setStyle(Style style)                 { mStyle = style; if (style != null) style.mBuilder = this; return this; }
        public Builder addAction(int icon, CharSequence title, android.app.PendingIntent i) { mActions.add(new Action(icon, title, i)); return this; }
        public Builder addAction(Action action)              { if (action != null) mActions.add(action); return this; }
        public Builder setVibrate(long[] pattern)            { mVibrate = pattern; return this; }
        public Builder setSound(android.net.Uri sound)       { mSound = sound; return this; }
        public Builder setSound(android.net.Uri sound, int streamType) { mSound = sound; return this; }
        public Builder setSound(android.net.Uri sound, android.media.AudioAttributes attrs) { mSound = sound; return this; }
        public Builder setLights(int argb, int onMs, int offMs) { mLedArgb = argb; mLedOnMs = onMs; mLedOffMs = offMs; return this; }
        public Builder setChannelId(String channelId)        { mChannelId = channelId; return this; }
        public Builder setGroup(String groupKey)             { mGroup = groupKey; return this; }
        public Builder setGroupSummary(boolean isGroupSummary) { mGroupSummary = isGroupSummary; return this; }
        public Builder setGroupAlertBehavior(int behavior)   { mGroupAlertBehavior = behavior; return this; }
        public Builder setSortKey(String sortKey)            { mSortKey = sortKey; return this; }
        public Builder setLocalOnly(boolean localOnly)       { mLocalOnly = localOnly; return this; }
        public Builder setSubText(CharSequence text)         { mSubText = text; return this; }
        public Builder setSettingsText(CharSequence text)    { mSettingsText = text; return this; }
        public Builder setRemoteInputHistory(CharSequence[] text) { return this; }
        public Builder setShortcutId(String shortcutId)      { mShortcutId = shortcutId; return this; }
        public Builder setBadgeIconType(int icon)            { mBadgeIconType = icon; return this; }
        public Builder setTimeoutAfter(long durationMs)      { mTimeoutAfter = durationMs; return this; }
        public Builder setExtras(android.os.Bundle extras)   { mExtras.clear(); if (extras != null) mExtras.putAll(extras); return this; }
        public Builder addExtras(android.os.Bundle extras)   { if (extras != null) mExtras.putAll(extras); return this; }
        public android.os.Bundle getExtras()                 { return mExtras; }
        public Builder setPublicVersion(Notification n)      { return this; }
        public Builder addPerson(String uri)                 { if (uri != null) mPeople.add(uri); return this; }
        public Builder addPerson(Person person)              { return this; }
        public Builder setFlag(int mask, boolean value)      {
            if (mask == FLAG_AUTO_CANCEL) mAutoCancel = value;
            else if (mask == FLAG_ONGOING_EVENT) mOngoing = value;
            else if (mask == FLAG_ONLY_ALERT_ONCE) mOnlyAlertOnce = value;
            return this;
        }
        // AOSP :6515-6517. Previously a no-op with no backing field, so the
        // built Notification's getAllowSystemGeneratedContextualActions()
        // (hardcoded true) never reflected an app calling this with false.
        private boolean mAllowSystemGeneratedContextualActions = true;
        public Builder setAllowSystemGeneratedContextualActions(boolean allow) {
            mAllowSystemGeneratedContextualActions = allow; return this;
        }
        public Builder setForegroundServiceBehavior(int behavior) { return this; }
        public Style getStyle() { return mStyle; }
        // RemoteViews custom-content variants. Pre-API-24 setContent +
        // API-24+ setCustom{Content,BigContent,HeadsUpContent}View.
        // androidx.core.app.NotificationCompatBuilder copies through
        // setContent first (pre-API-24 path) and then through the
        // setCustom* variants (post-API-24); both code paths must
        // resolve at class-link time. The values aren't used since
        // Mac.apk doesn't render system notifications -- the methods
        // just need to exist.
        private android.widget.RemoteViews mContentView, mBigContentView, mHeadsUpContentView, mTickerView;
        public Builder setContent(android.widget.RemoteViews views) { mContentView = views; return this; }
        public Builder setCustomContentView(android.widget.RemoteViews views) { mContentView = views; return this; }
        public Builder setCustomBigContentView(android.widget.RemoteViews views) { mBigContentView = views; return this; }
        public Builder setCustomHeadsUpContentView(android.widget.RemoteViews views) { mHeadsUpContentView = views; return this; }

        /** Populate a real Notification from the accumulated state.
         *  AOSP build() writes the content title/text into the extras
         *  bundle under EXTRA_TITLE/EXTRA_TEXT and sets the public
         *  fields; we do both so reflective read-back works. */
        public android.widget.RemoteViews createBigContentView() { return null; }
        public android.widget.RemoteViews createContentView() { return null; }
        public android.widget.RemoteViews createHeadsUpContentView() { return null; }
        public Notification build() {
            Notification n = new Notification();
            n.mBubbleMetadata = mBubbleMetadata;
            n.icon = mSmallIcon;
            n.iconLevel = mSmallIconLevel;
            n.smallIcon = mSmallIconObj;
            n.largeIcon = mLargeBitmap;
            n.largeIconObj = mLargeIconObj;
            n.tickerText = mTicker;
            n.contentIntent = mContentIntent;
            n.deleteIntent = mDeleteIntent;
            n.fullScreenIntent = mFullScreenIntent;
            n.contentView = mContentView;
            n.bigContentView = mBigContentView;
            n.headsUpContentView = mHeadsUpContentView;
            n.priority = mPriority;
            n.category = mCategory;
            n.channelId = mChannelId;
            n.group = mGroup;
            n.sortKey = mSortKey;
            n.shortcutId = mShortcutId;
            n.visibility = mVisibility;
            n.color = mColor;
            n.defaults = mDefaults;
            n.number = mNumber;
            n.when = mWhen;
            n.badgeIconType = mBadgeIconType;
            n.groupAlertBehavior = mGroupAlertBehavior;
            n.settingsText = mSettingsText;
            n.timeoutAfter = mTimeoutAfter;
            n.vibrate = mVibrate;
            n.sound = mSound;
            n.ledARGB = mLedArgb;
            n.ledOnMS = mLedOnMs;
            n.ledOffMS = mLedOffMs;
            n.actions = mActions.toArray(new Action[0]);
            n.allowSystemGeneratedContextualActions = mAllowSystemGeneratedContextualActions;
            // Aggregate the boolean flags into the bitmask field.
            int flags = 0;
            if (mAutoCancel)    flags |= FLAG_AUTO_CANCEL;
            if (mOngoing)       flags |= FLAG_ONGOING_EVENT;
            if (mOnlyAlertOnce) flags |= FLAG_ONLY_ALERT_ONCE;
            if (mLocalOnly)     flags |= FLAG_LOCAL_ONLY;
            if (mGroupSummary)  flags |= FLAG_GROUP_SUMMARY;
            n.flags = flags;
            // Mirror content into the extras bundle under the AOSP keys.
            android.os.Bundle ex = new android.os.Bundle(mExtras);
            if (mContentTitle != null) ex.putCharSequence(EXTRA_TITLE, mContentTitle);
            if (mContentText  != null) ex.putCharSequence(EXTRA_TEXT, mContentText);
            if (mSubText      != null) ex.putCharSequence(EXTRA_SUB_TEXT, mSubText);
            if (mContentInfo  != null) ex.putCharSequence(EXTRA_INFO_TEXT, mContentInfo);
            if (mSmallIcon != 0)       ex.putInt(EXTRA_SMALL_ICON, mSmallIcon);
            if (mChannelId != null)    ex.putString(EXTRA_CHANNEL_ID, mChannelId);
            ex.putInt(EXTRA_PROGRESS, mProgress);
            ex.putInt(EXTRA_PROGRESS_MAX, mProgressMax);
            ex.putBoolean(EXTRA_PROGRESS_INDETERMINATE, mProgressIndeterminate);
            ex.putBoolean(EXTRA_SHOW_WHEN, mShowWhen);
            ex.putBoolean(EXTRA_SHOW_CHRONOMETER, mUsesChronometer);
            ex.putBoolean(EXTRA_CHRONOMETER_COUNT_DOWN, mChronometerCountDown);
            ex.putBoolean(EXTRA_COLORIZED, mColorized);
            if (!mPeople.isEmpty()) ex.putStringArray(EXTRA_PEOPLE, mPeople.toArray(new String[0]));
            n.extras = ex;
            return n;
        }
        public Notification getNotification() { return build(); }

        /** AOSP Builder.extend(Extender extender): {@code return
         *  extender.extend(this);} verbatim -- "Apply an extender to this
         *  notification builder." This is the entry point WearableExtender /
         *  CarExtender / TvExtender and every app-written extender go through;
         *  without it an app that calls .extend(...) mid-chain takes a
         *  NoSuchMethodError. */
        public Builder extend(Extender extender) {
            return extender != null ? extender.extend(this) : this;
        }
    }

    /** AOSP: a Style applies an expanded layout to a Notification.
     *  Apps either pass the Builder to the Style ctor or call
     *  Builder.setStyle(style). We retain the back-reference so
     *  setBuilder/build work, but we don't render the expanded layout. */
    public static abstract class Style {
        protected Builder mBuilder;
        protected CharSequence mBigContentTitle;
        protected CharSequence mSummaryText;
        protected boolean mSummaryTextSet;
        public void setBuilder(Builder builder) { mBuilder = builder; }
        public Notification build() { return mBuilder != null ? mBuilder.build() : new Notification(); }

        // ---- AOSP API-36 surface completion ----
        // These four are PROTECTED in AOSP: a custom Style subclass (which apps
        // do write, and which androidx's NotificationCompat.Style mirrors) both
        // calls internalSetBigContentTitle/internalSetSummaryText from its own
        // public setters and overrides checkBuilder. Missing them, such a
        // subclass does not compile against this framework at all.

        /** AOSP Style.internalSetBigContentTitle(CharSequence): "Overrides
         *  ContentTitle in the big form of the template. This defaults to the
         *  value passed to setContentTitle()." */
        protected void internalSetBigContentTitle(CharSequence title) {
            mBigContentTitle = title;
        }

        /** AOSP Style.internalSetSummaryText(CharSequence): "Set the first line
         *  of text after the detail section in the big form of the template."
         *  AOSP also latches mSummaryTextSet, which is how the template tells
         *  "unset" from "set to null". */
        protected void internalSetSummaryText(CharSequence cs) {
            mSummaryText = cs;
            mSummaryTextSet = true;
        }

        /** AOSP Style.checkBuilder(): {@code if (mBuilder == null) throw new
         *  IllegalArgumentException("Style requires a valid Builder object");}
         *  verbatim -- the guard a Style runs before touching its builder. */
        protected void checkBuilder() {
            if (mBuilder == null) {
                throw new IllegalArgumentException("Style requires a valid Builder object");
            }
        }

        /** AOSP Style.getStandardView(int layoutId): builds the platform's
         *  standard RemoteViews for this style. Mac.apk renders no notification
         *  at all -- there is no shade to inflate a RemoteViews into (see the
         *  class comment) -- so there is no standard view to hand back, and AOSP
         *  itself returns whatever the builder produced, which is nothing here.
         *  Present because a custom Style subclass calls super.getStandardView()
         *  and would not compile without it. */
        protected android.widget.RemoteViews getStandardView(int layoutId) { return null; }
    }
    /** Notification.ProgressStyle (API 36): a segmented progress bar (e.g. a
     *  rideshare / delivery tracker). Mac.apk has no notification shade, so the
     *  style builds an ordinary Notification; the segment/point holders exist so
     *  an app that composes one compiles and round-trips.
     *  Source: frameworks/base/core/java/android/app/Notification.java */
    public static final class ProgressStyle extends Style {
        // Every setter below used to discard its argument, which made the
        // API-36 getters (added in this pass) unimplementable and meant an app
        // could not read back the progress bar it had just described. AOSP
        // stores all of it; so does this now.
        private int mProgress;
        private boolean mProgressIndeterminate;
        private boolean mStyledByProgress = true;
        private android.graphics.drawable.Icon mProgressTrackerIcon;
        private android.graphics.drawable.Icon mProgressStartIcon;
        private android.graphics.drawable.Icon mProgressEndIcon;
        private final java.util.List<Segment> mSegments = new java.util.ArrayList<Segment>();
        private final java.util.List<Point> mPoints = new java.util.ArrayList<Point>();

        public ProgressStyle() {}
        public ProgressStyle(Builder builder) { setBuilder(builder); }
        public ProgressStyle setProgress(int progress) { mProgress = progress; return this; }
        public ProgressStyle setProgressIndeterminate(boolean indeterminate) {
            mProgressIndeterminate = indeterminate;
            return this;
        }
        public ProgressStyle setProgressTrackerIcon(android.graphics.drawable.Icon icon) {
            mProgressTrackerIcon = icon;
            return this;
        }
        public ProgressStyle setProgressStartIcon(android.graphics.drawable.Icon icon) {
            mProgressStartIcon = icon;
            return this;
        }
        public ProgressStyle setProgressEndIcon(android.graphics.drawable.Icon icon) {
            mProgressEndIcon = icon;
            return this;
        }
        public ProgressStyle setStyledByProgress(boolean styledByProgress) {
            mStyledByProgress = styledByProgress;
            return this;
        }
        public ProgressStyle addProgressSegment(Segment segment) {
            if (segment != null) mSegments.add(segment);
            return this;
        }
        public ProgressStyle setProgressSegments(java.util.List<Segment> segments) {
            mSegments.clear();
            if (segments != null) mSegments.addAll(segments);
            return this;
        }
        public ProgressStyle addProgressPoint(Point point) {
            if (point != null) mPoints.add(point);
            return this;
        }
        public ProgressStyle setProgressPoints(java.util.List<Point> points) {
            mPoints.clear();
            if (points != null) mPoints.addAll(points);
            return this;
        }

        // ---- AOSP API-36 getters ----

        /** AOSP getProgress(): the current progress along the bar. */
        public int getProgress() { return mProgress; }

        /** AOSP getProgressMax(): "the total length of the progress bar", which
         *  AOSP computes as the SUM of the segment lengths -- it is not a
         *  separately-settable value on this style, which is why there is no
         *  setProgressMax. */
        public int getProgressMax() {
            int total = 0;
            for (Segment segment : mSegments) {
                if (segment != null) total += segment.getLength();
            }
            return total;
        }

        /** AOSP isProgressIndeterminate(). */
        public boolean isProgressIndeterminate() { return mProgressIndeterminate; }
        /** AOSP isStyledByProgress(): whether the notification's colour follows
         *  the segment the tracker is in. AOSP's default is true. */
        public boolean isStyledByProgress() { return mStyledByProgress; }
        /** AOSP getProgressTrackerIcon(): the icon riding the bar, or null. */
        public android.graphics.drawable.Icon getProgressTrackerIcon() {
            return mProgressTrackerIcon;
        }
        /** AOSP getProgressStartIcon(): the icon at the start of the bar. */
        public android.graphics.drawable.Icon getProgressStartIcon() { return mProgressStartIcon; }
        /** AOSP getProgressEndIcon(): the icon at the end of the bar. */
        public android.graphics.drawable.Icon getProgressEndIcon() { return mProgressEndIcon; }
        /** AOSP getProgressSegments(): never null. */
        public java.util.List<Segment> getProgressSegments() {
            return new java.util.ArrayList<Segment>(mSegments);
        }
        /** AOSP getProgressPoints(): never null. */
        public java.util.List<Point> getProgressPoints() {
            return new java.util.ArrayList<Point>(mPoints);
        }

        /** A length of the progress bar with its own length + color. */
        public static final class Segment {
            private final int mLength;
            private int mId = 0;
            private int mColor = 0;
            public Segment(int length) { mLength = length; }
            public int getLength() { return mLength; }
            public Segment setId(int id) { mId = id; return this; }
            public int getId() { return mId; }
            public Segment setColor(int color) { mColor = color; return this; }
            public int getColor() { return mColor; }
        }

        /** A point marker along the progress bar. */
        public static final class Point {
            private final int mPosition;
            private int mId = 0;
            private int mColor = 0;
            public Point(int position) { mPosition = position; }
            public int getPosition() { return mPosition; }
            public Point setId(int id) { mId = id; return this; }
            public int getId() { return mId; }
            public Point setColor(int color) { mColor = color; return this; }
            public int getColor() { return mColor; }
        }
    }
    public static class BigTextStyle extends Style {
        public BigTextStyle() {}
        public BigTextStyle(Builder builder) { setBuilder(builder); }
        public BigTextStyle bigText(CharSequence text) { return this; }
        public BigTextStyle setBigContentTitle(CharSequence title) { return this; }
        public BigTextStyle setSummaryText(CharSequence summary) { return this; }
    }
    public static class BigPictureStyle extends Style {
        public BigPictureStyle() {}
        public BigPictureStyle(Builder builder) { setBuilder(builder); }
        public BigPictureStyle bigPicture(android.graphics.Bitmap b) { return this; }
        public BigPictureStyle bigPicture(android.graphics.drawable.Icon icon) { return this; }
        public BigPictureStyle bigLargeIcon(android.graphics.Bitmap b) { return this; }
        public BigPictureStyle bigLargeIcon(android.graphics.drawable.Icon icon) { return this; }
        public BigPictureStyle setBigContentTitle(CharSequence title) { return this; }
        public BigPictureStyle setSummaryText(CharSequence summary) { return this; }
        public BigPictureStyle setContentDescription(CharSequence d) { return this; }
        public BigPictureStyle showBigPictureWhenCollapsed(boolean show) { return this; }
    }
    public static class InboxStyle extends Style {
        public InboxStyle() {}
        public InboxStyle(Builder builder) { setBuilder(builder); }
        public InboxStyle addLine(CharSequence cs) { return this; }
        public InboxStyle setBigContentTitle(CharSequence title) { return this; }
        public InboxStyle setSummaryText(CharSequence summary) { return this; }
    }
    public static class MessagingStyle extends Style {

    // ---- AOSP API-36 constant completion (android.app.Notification.MessagingStyle) ----
    // Values extracted verbatim from the AOSP source for this class
    // (android16-release). javac INLINES a compile-time constant into the app at
    // ITS build time, so a shipped APK cannot observe a missing one at runtime --
    // these are here for our own framework code, for anything compiled against
    // THIS jar, and so the surface is complete rather than nearly complete.
    public static final int MAXIMUM_RETAINED_MESSAGES = 25;

        private CharSequence mUserDisplayName, mConversationTitle;
        private final java.util.ArrayList<Message> mMessages = new java.util.ArrayList<>();
        private Person mUser;
        public MessagingStyle(CharSequence userDisplayName) { mUserDisplayName = userDisplayName; }
        public MessagingStyle(Person user) { mUser = user; }
        public CharSequence getUserDisplayName() { return mUserDisplayName; }
        public Person getUser() { return mUser; }
        public MessagingStyle setConversationTitle(CharSequence conversationTitle) { mConversationTitle = conversationTitle; return this; }
        public CharSequence getConversationTitle() { return mConversationTitle; }
        // AOSP core/java/android/app/Notification.java (android13-release)
        // :8091-8119. Previously the setter was a no-op and the getter
        // hardcoded false with no backing field, so setGroupConversation(true)
        // followed by isGroupConversation() silently read back false.
        // DEVIATION, deliberate: AOSP's real isGroupConversation() has a legacy
        // branch for apps targeting SDK < P (":8110-8115" -- a non-null
        // conversation title implies group conversation) that we do not mirror;
        // that path only matters for pre-2018-targetSdk apps, effectively none
        // of the corpus. From P onward (the only path implemented here) it is
        // exactly "return what setGroupConversation() set."
        private boolean mIsGroupConversation;
        public MessagingStyle setGroupConversation(boolean isGroup) { mIsGroupConversation = isGroup; return this; }
        public boolean isGroupConversation() { return mIsGroupConversation; }
        public MessagingStyle addMessage(CharSequence text, long timestamp, Person sender) { mMessages.add(new Message(text, timestamp, sender)); return this; }
        public MessagingStyle addMessage(CharSequence text, long timestamp, CharSequence sender) { mMessages.add(new Message(text, timestamp, sender)); return this; }
        public MessagingStyle addMessage(Message message) { if (message != null) mMessages.add(message); return this; }
        /** AOSP addHistoricMessage(Message): "Adds a Message for display at the
         *  top of the Notification... intended to provide historical context."
         *  Was a no-op with no storage, so getHistoricMessages (added below)
         *  could not exist and an app's history silently vanished. */
        public MessagingStyle addHistoricMessage(Message message) {
            if (message != null) mHistoricMessages.add(message);
            return this;
        }
        private final java.util.ArrayList<Message> mHistoricMessages =
                new java.util.ArrayList<Message>();
        /** AOSP getHistoricMessages(): "@return A list of messages that this
         *  notification intends to display as historic" -- never null. */
        public java.util.List<Message> getHistoricMessages() { return mHistoricMessages; }
        public java.util.List<Message> getMessages() { return mMessages; }

        /** AOSP Notification.MessagingStyle.Message. */
        public static final class Message {
            private final CharSequence mText;
            private final long mTimestamp;
            private final Person mSenderPerson;
            private final CharSequence mSender;
            private String mDataMimeType;
            private android.net.Uri mDataUri;
            public Message(CharSequence text, long timestamp, Person sender) {
                mText = text; mTimestamp = timestamp; mSenderPerson = sender; mSender = sender != null ? sender.getName() : null;
            }
            public Message(CharSequence text, long timestamp, CharSequence sender) {
                mText = text; mTimestamp = timestamp; mSender = sender; mSenderPerson = null;
            }
            public CharSequence getText() { return mText; }
            public long getTimestamp() { return mTimestamp; }
            public Person getSenderPerson() { return mSenderPerson; }
            public CharSequence getSender() { return mSender; }
            public Message setData(String dataMimeType, android.net.Uri dataUri) { mDataMimeType = dataMimeType; mDataUri = dataUri; return this; }
            public String getDataMimeType() { return mDataMimeType; }
            public android.net.Uri getDataUri() { return mDataUri; }

            // ---- AOSP API-36 surface completion ----

            /** AOSP Message.getExtras(): "@return The extras Bundle for this
             *  message" -- always non-null, created eagerly by AOSP so a caller
             *  can putAll into it (getMessageFromBundle below relies on that). */
            public android.os.Bundle getExtras() { return mExtras; }
            private final android.os.Bundle mExtras = new android.os.Bundle();

            // AOSP's Bundle keys for a serialized Message
            // (Notification.java:9947-9953). Load-bearing values, not names:
            // a notification listener and androidx's NotificationCompat both
            // read this exact bundle shape out of EXTRA_MESSAGES.
            private static final String KEY_TEXT = "text";
            private static final String KEY_TIMESTAMP = "time";
            private static final String KEY_SENDER = "sender";
            private static final String KEY_SENDER_PERSON = "sender_person";
            private static final String KEY_DATA_MIME_TYPE = "type";
            private static final String KEY_DATA_URI = "uri";
            private static final String KEY_EXTRAS_BUNDLE = "extras";

            /** AOSP getMessagesFromBundleArray(Parcelable[]): "Returns a list of
             *  messages read from the given bundle list, e.g. EXTRA_MESSAGES or
             *  EXTRA_HISTORIC_MESSAGES." Never null; entries that are not
             *  Bundles, or that lack text/timestamp, are skipped -- AOSP's own
             *  filtering. */
            public static java.util.List<Message> getMessagesFromBundleArray(
                    android.os.Parcelable[] bundles) {
                java.util.List<Message> messages = new java.util.ArrayList<Message>();
                if (bundles == null) return messages;
                for (int i = 0; i < bundles.length; i++) {
                    if (bundles[i] instanceof android.os.Bundle) {
                        Message message = getMessageFromBundle((android.os.Bundle) bundles[i]);
                        if (message != null) messages.add(message);
                    }
                }
                return messages;
            }

            /** AOSP getMessageFromBundle(Bundle) -- @hide there, kept package-
             *  private here because getMessagesFromBundleArray above is its only
             *  caller. Mirrors AOSP including the legacy-sender fallback: an app
             *  using the old compat library writes only KEY_SENDER (a name), not
             *  KEY_SENDER_PERSON, so a Person is synthesised from it. */
            static Message getMessageFromBundle(android.os.Bundle bundle) {
                try {
                    if (bundle == null || !bundle.containsKey(KEY_TEXT)
                            || !bundle.containsKey(KEY_TIMESTAMP)) {
                        return null;
                    }
                    Person senderPerson = bundle.getParcelable(KEY_SENDER_PERSON);
                    if (senderPerson == null) {
                        CharSequence senderName = bundle.getCharSequence(KEY_SENDER);
                        if (senderName != null) {
                            senderPerson = new Person.Builder().setName(senderName).build();
                        }
                    }
                    Message message = new Message(bundle.getCharSequence(KEY_TEXT),
                            bundle.getLong(KEY_TIMESTAMP), senderPerson);
                    if (bundle.containsKey(KEY_DATA_MIME_TYPE)
                            && bundle.containsKey(KEY_DATA_URI)) {
                        message.setData(bundle.getString(KEY_DATA_MIME_TYPE),
                                (android.net.Uri) bundle.getParcelable(KEY_DATA_URI));
                    }
                    if (bundle.containsKey(KEY_EXTRAS_BUNDLE)) {
                        android.os.Bundle extra = bundle.getBundle(KEY_EXTRAS_BUNDLE);
                        if (extra != null) message.getExtras().putAll(extra);
                    }
                    return message;
                } catch (ClassCastException e) {
                    return null;
                }
            }
        }
    }
    public static class MediaStyle extends Style {
        public MediaStyle setMediaSession(android.media.session.MediaSession.Token token) { return this; }
        public MediaStyle setRemotePlaybackInfo(CharSequence deviceName, int iconResource, android.app.PendingIntent chipIntent) { return this; }
        public MediaStyle() {}
        public MediaStyle(Builder builder) { setBuilder(builder); }
        public MediaStyle setShowActionsInCompactView(int... actions) { return this; }
        // NOTE: AOSP also declares setMediaSession(android.media.session
        // .MediaSession.Token). That type lives in the media subsystem
        // (not yet present in runtime/java); omitting the overload here
        // avoids a cross-package dependency. Apps that style media
        // notifications still resolve setShowActionsInCompactView; only
        // the session-token linkage is absent (we don't render media
        // controls anyway).
    }
    /** AOSP API-24+ custom-view decorating styles. No-op (we don't
     *  render the chrome) but must exist so apps can instantiate. */
    public static class DecoratedCustomViewStyle extends Style {
        public DecoratedCustomViewStyle() {}
        public DecoratedCustomViewStyle(Builder builder) { setBuilder(builder); }
    }
    public static class DecoratedMediaCustomViewStyle extends MediaStyle {
        public DecoratedMediaCustomViewStyle() {}
        public DecoratedMediaCustomViewStyle(Builder builder) { setBuilder(builder); }
    }
    /** AOSP API-31+ call-style notification. */
    public static class CallStyle extends Style {
        public CallStyle setVerificationIcon(android.graphics.drawable.Icon verificationIcon) { return this; }
        public static final int CALL_TYPE_UNKNOWN = 0;
        public static final int CALL_TYPE_INCOMING = 1;
        public static final int CALL_TYPE_ONGOING = 2;
        public static final int CALL_TYPE_SCREENING = 3;
        public static CallStyle forIncomingCall(Person person, PendingIntent declineIntent, PendingIntent answerIntent) { return new CallStyle(); }
        public static CallStyle forOngoingCall(Person person, PendingIntent hangupIntent) { return new CallStyle(); }
        public static CallStyle forScreeningCall(Person person, PendingIntent hangupIntent, PendingIntent answerIntent) { return new CallStyle(); }
        public CallStyle setIsVideo(boolean isVideo) { return this; }
        public CallStyle setVerificationText(CharSequence text) { return this; }
        public CallStyle setAnswerButtonColorHint(int color) { return this; }
        public CallStyle setDeclineButtonColorHint(int color) { return this; }
    }

    /** AOSP Notification.Action -- a button on the notification (icon +
     *  label + PendingIntent + optional RemoteInputs). Building Action
     *  objects directly (Action.Builder) is common in messaging apps. */
    public static class Action implements android.os.Parcelable {
        public static final int SEMANTIC_ACTION_NONE = 0;
        public static final int SEMANTIC_ACTION_REPLY = 1;
        public static final int SEMANTIC_ACTION_MARK_AS_READ = 2;
        public static final int SEMANTIC_ACTION_MARK_AS_UNREAD = 3;
        public static final int SEMANTIC_ACTION_DELETE = 4;
        public static final int SEMANTIC_ACTION_ARCHIVE = 5;
        public static final int SEMANTIC_ACTION_MUTE = 6;
        public static final int SEMANTIC_ACTION_UNMUTE = 7;
        public static final int SEMANTIC_ACTION_THUMBS_UP = 8;
        public static final int SEMANTIC_ACTION_THUMBS_DOWN = 9;
        public static final int SEMANTIC_ACTION_CALL = 10;

        public int icon;
        public CharSequence title;
        public android.app.PendingIntent actionIntent;
        public android.os.Bundle extras = new android.os.Bundle();
        private android.graphics.drawable.Icon mIcon;
        private RemoteInput[] mRemoteInputs;
        private boolean mAllowGeneratedReplies;
        private int mSemanticAction;
        private boolean mContextual, mAuthRequired;

        public Action(int icon, CharSequence title, android.app.PendingIntent intent) {
            this.icon = icon; this.title = title; this.actionIntent = intent;
        }
        public android.graphics.drawable.Icon getIcon() { return mIcon; }
        public CharSequence getTitle() { return title; }
        public android.app.PendingIntent getActionIntent() { return actionIntent; }
        public android.os.Bundle getExtras() { return extras; }
        public RemoteInput[] getRemoteInputs() { return mRemoteInputs; }
        public boolean getAllowGeneratedReplies() { return mAllowGeneratedReplies; }
        public int getSemanticAction() { return mSemanticAction; }
        public boolean isContextual() { return mContextual; }
        public boolean isAuthenticationRequired() { return mAuthRequired; }

        // ---- AOSP API-36 surface completion ----

        /** AOSP Action.clone(): "@return a shallow copy of this action" -- a
         *  PUBLIC override of Object.clone() with a covariant return, so an app
         *  can call it without Cloneable. AOSP's body constructs a new Action
         *  from the same icon/title/intent/extras/remoteInputs, copying the
         *  extras Bundle so the two do not share mutable state. */
        @Override
        public Action clone() {
            Action that = new Action(this.icon, this.title, this.actionIntent);
            that.mIcon = this.mIcon;
            that.extras = (this.extras != null)
                    ? new android.os.Bundle(this.extras) : new android.os.Bundle();
            that.mRemoteInputs = (this.mRemoteInputs != null)
                    ? this.mRemoteInputs.clone() : null;
            that.mDataOnlyRemoteInputs = (this.mDataOnlyRemoteInputs != null)
                    ? this.mDataOnlyRemoteInputs.clone() : null;
            that.mAllowGeneratedReplies = this.mAllowGeneratedReplies;
            that.mSemanticAction = this.mSemanticAction;
            that.mContextual = this.mContextual;
            that.mAuthRequired = this.mAuthRequired;
            return that;
        }

        /** AOSP Action.getDataOnlyRemoteInputs(): "Get the list of inputs to be
         *  collected from the user that ONLY accept data when this action is
         *  sent. These remote inputs are guaranteed to return true on a call to
         *  RemoteInput#isDataOnly. Returns null if there are no data-only remote
         *  inputs. This method exists so that legacy RemoteInput collectors that
         *  pre-date the addition of non-textual RemoteInputs do not access these
         *  remote inputs." AOSP reads them from the action's extras under
         *  EXTRA_DATA_ONLY_INPUTS; Action.Builder in this tree does not split
         *  the two sets (getRemoteInputs above returns everything the builder
         *  was given), so nothing populates the data-only set and null -- AOSP's
         *  own no-data-only-inputs answer -- is correct rather than a stub. */
        public RemoteInput[] getDataOnlyRemoteInputs() { return mDataOnlyRemoteInputs; }
        private RemoteInput[] mDataOnlyRemoteInputs;

        @Override public int describeContents() { return 0; }

        // AOSP android11-release Notification.java:1853-1873, plus the
        // mAuthenticationRequired slot android13-release appends after
        // mIsContextual (this tree declares that field, so it is carried in
        // AOSP 13's position rather than invented into the middle of the
        // AOSP 11 order). Actions ride Notification's writeTypedArray, so an
        // empty body here meant every button on a parcelled notification was a
        // liveref even once the notification itself marshalled.
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            // mIcon ABSENT-MARKER + the deprecated `icon` resource id AOSP
            // recovers from mIcon.getResId(). See the Icon note on
            // Notification.writeToParcel for the removal condition.
            dest.writeInt(mIcon != null ? 1 : 0);
            dest.writeInt(icon);
            // Unguarded, as AOSP has it: TextUtils' format encodes a null
            // CharSequence as a null string and reads it back as null.
            dest.writeCharSequence(title);
            dest.writeInt(actionIntent != null ? 1 : 0);
            if (actionIntent != null) actionIntent.writeToParcel(dest, flags);
            dest.writeBundle(extras);
            // mRemoteInputs ABSENT-MARKER. AOSP writes the array with
            // writeTypedArray, but android.app.RemoteInput does not implement
            // Parcelable on this tree at all -- it has no writeToParcel and no
            // CREATOR, so writeTypedArray would not even compile against it.
            // The COUNT is written (AOSP's own -1-for-null convention) so the
            // slot keeps its shape and a reader can tell "there were N" from
            // "there were none".
            // REMOVAL CONDITION: when RemoteInput implements Parcelable,
            // replace this with dest.writeTypedArray(mRemoteInputs, flags) and
            // in.createTypedArray(RemoteInput.CREATOR).
            dest.writeInt(mRemoteInputs != null ? mRemoteInputs.length : -1);
            dest.writeInt(mAllowGeneratedReplies ? 1 : 0);
            dest.writeInt(mSemanticAction);
            dest.writeInt(mContextual ? 1 : 0);
            dest.writeInt(mAuthRequired ? 1 : 0);
        }

        /** Read side; mirrors AOSP's private Action(Parcel)
         *  (Notification.java:1515-1532). */
        private Action(android.os.Parcel in) {
            in.readInt();                       // mIcon ABSENT-MARKER
            icon = in.readInt();
            title = in.readCharSequence();
            if (in.readInt() != 0) actionIntent = PendingIntent.CREATOR.createFromParcel(in);
            extras = in.readBundle();
            // The count is consumed to keep the cursor aligned. The elements
            // cannot be rebuilt (see the ABSENT-MARKER note above), and null
            // is the right absent value rather than an array of nulls: AOSP's
            // getRemoteInputs() legitimately answers null when an action has
            // none, so every consumer already null-checks it.
            in.readInt();
            mRemoteInputs = null;
            mAllowGeneratedReplies = in.readInt() == 1;
            mSemanticAction = in.readInt();
            mContextual = in.readInt() == 1;
            mAuthRequired = in.readInt() == 1;
        }

        public static final Creator<Action> CREATOR = new Creator<Action>() {
            @Override public Action createFromParcel(android.os.Parcel s) { return new Action(s); }
            @Override public Action[] newArray(int size) { return new Action[size]; }
        };

        public static final class Builder {
            private int mIconRes;
            private android.graphics.drawable.Icon mIcon;
            private CharSequence mTitle;
            private android.app.PendingIntent mIntent;
            private android.os.Bundle mExtras = new android.os.Bundle();
            private final java.util.ArrayList<RemoteInput> mRemoteInputs = new java.util.ArrayList<>();
            private boolean mAllowGeneratedReplies = true;
            private int mSemanticAction;
            private boolean mContextual, mAuthRequired;
            public Builder(int icon, CharSequence title, android.app.PendingIntent intent) {
                mIconRes = icon; mTitle = title; mIntent = intent;
            }
            public Builder(android.graphics.drawable.Icon icon, CharSequence title, android.app.PendingIntent intent) {
                mIcon = icon; mTitle = title; mIntent = intent;
            }
            public Builder(Action action) {
                if (action != null) { mIconRes = action.icon; mTitle = action.title; mIntent = action.actionIntent; mIcon = action.mIcon; }
            }
            public Builder addRemoteInput(RemoteInput remoteInput) { if (remoteInput != null) mRemoteInputs.add(remoteInput); return this; }
            public Builder addExtras(android.os.Bundle extras) { if (extras != null) mExtras.putAll(extras); return this; }
            public android.os.Bundle getExtras() { return mExtras; }
            public Builder setAllowGeneratedReplies(boolean allowGeneratedReplies) { mAllowGeneratedReplies = allowGeneratedReplies; return this; }
            public Builder setSemanticAction(int semanticAction) { mSemanticAction = semanticAction; return this; }
            public Builder setContextual(boolean isContextual) { mContextual = isContextual; return this; }
            public Builder setAuthenticationRequired(boolean authenticationRequired) { mAuthRequired = authenticationRequired; return this; }
            public Builder setStyleHint(int styleHint) { return this; }
            public Builder setEmphasisHint(int emphasisHint) { return this; }
            public Builder extend(Object extender) { return this; }
            public Action build() {
                Action a = new Action(mIconRes, mTitle, mIntent);
                a.mIcon = mIcon;
                a.extras = mExtras;
                a.mRemoteInputs = mRemoteInputs.isEmpty() ? null : mRemoteInputs.toArray(new RemoteInput[0]);
                a.mAllowGeneratedReplies = mAllowGeneratedReplies;
                a.mSemanticAction = mSemanticAction;
                a.mContextual = mContextual;
                a.mAuthRequired = mAuthRequired;
                return a;
            }
        }
    }

    private BubbleMetadata mBubbleMetadata;
    /** AOSP Notification.getBubbleMetadata (API 29): the bubble metadata set on
     *  the builder, or null. */
    public BubbleMetadata getBubbleMetadata() { return mBubbleMetadata; }
    public boolean getAllowSystemGeneratedContextualActions() { return allowSystemGeneratedContextualActions; }

    /** AOSP API-29 bubble metadata. Stubbed; we don't display
     *  bubbles. NotificationCompatBuilder reads/writes this via the
     *  builder method we exposed above. */
    public static class BubbleMetadata implements android.os.Parcelable {
        // AOSP packs the two booleans below into a single mFlags int
        // (Notification.java:8831 / :8854) and that int is what crosses the
        // parcel. This tree keeps them as separate booleans, so the flags slot
        // is composed on the way out and decomposed on the way in, which keeps
        // the wire format AOSP's without changing the class's field shape.
        private static final int FLAG_AUTO_EXPAND_BUBBLE   = 0x00000001;
        private static final int FLAG_SUPPRESS_NOTIFICATION = 0x00000002;

        private android.app.PendingIntent mIntent;
        private android.app.PendingIntent mDeleteIntent;
        private android.graphics.drawable.Icon mIcon;
        private int mDesiredHeight;
        private int mDesiredHeightResId;
        private String mShortcutId;
        private boolean mAutoExpand;
        private boolean mSuppressNotification;
        private BubbleMetadata() {}

        @Override public int describeContents() { return 0; }

        // AOSP android11-release Notification.java:9018-9039. Order:
        // pendingIntent, icon, desiredHeight, flags, deleteIntent,
        // desiredHeightResId, shortcutId -- note deleteIntent comes AFTER the
        // flags int, not next to the other intent.
        @Override public void writeToParcel(android.os.Parcel out, int flags) {
            out.writeInt(mIntent != null ? 1 : 0);
            if (mIntent != null) mIntent.writeToParcel(out, 0);
            // mIcon ABSENT-MARKER; see Notification.writeToParcel for why and
            // for the removal condition.
            out.writeInt(mIcon != null ? 1 : 0);
            out.writeInt(mDesiredHeight);
            out.writeInt((mAutoExpand ? FLAG_AUTO_EXPAND_BUBBLE : 0)
                       | (mSuppressNotification ? FLAG_SUPPRESS_NOTIFICATION : 0)
                       | (mSuppressableBubble ? FLAG_SUPPRESSABLE_BUBBLE : 0));
            out.writeInt(mDeleteIntent != null ? 1 : 0);
            if (mDeleteIntent != null) mDeleteIntent.writeToParcel(out, 0);
            out.writeInt(mDesiredHeightResId);
            // AOSP guards on TextUtils.isEmpty, so an empty-string shortcut id
            // reads back null on Android too. Mirrored rather than "fixed".
            out.writeInt(mShortcutId == null || mShortcutId.isEmpty() ? 0 : 1);
            if (mShortcutId != null && !mShortcutId.isEmpty()) out.writeString(mShortcutId);
        }

        /** Read side; mirrors AOSP's private BubbleMetadata(Parcel)
         *  (Notification.java:8866-8882). */
        private BubbleMetadata(android.os.Parcel in) {
            if (in.readInt() != 0) mIntent = PendingIntent.CREATOR.createFromParcel(in);
            in.readInt();                       // mIcon ABSENT-MARKER
            mDesiredHeight = in.readInt();
            int f = in.readInt();
            mAutoExpand = (f & FLAG_AUTO_EXPAND_BUBBLE) != 0;
            mSuppressNotification = (f & FLAG_SUPPRESS_NOTIFICATION) != 0;
            mSuppressableBubble = (f & FLAG_SUPPRESSABLE_BUBBLE) != 0;
            if (in.readInt() != 0) mDeleteIntent = PendingIntent.CREATOR.createFromParcel(in);
            mDesiredHeightResId = in.readInt();
            if (in.readInt() != 0) mShortcutId = in.readString();
        }

        public static final Creator<BubbleMetadata> CREATOR = new Creator<BubbleMetadata>() {
            @Override public BubbleMetadata createFromParcel(android.os.Parcel s) {
                return new BubbleMetadata(s);
            }
            @Override public BubbleMetadata[] newArray(int size) { return new BubbleMetadata[size]; }
        };
        public android.app.PendingIntent getIntent() { return mIntent; }
        public android.app.PendingIntent getDeleteIntent() { return mDeleteIntent; }
        public android.graphics.drawable.Icon getIcon() { return mIcon; }
        public int getDesiredHeight() { return mDesiredHeight; }
        public int getDesiredHeightResId() { return mDesiredHeightResId; }
        public String getShortcutId() { return mShortcutId; }
        public boolean getAutoExpandBubble() { return mAutoExpand; }
        public boolean isNotificationSuppressed() { return mSuppressNotification; }

        // ---- AOSP API-36 surface completion ----
        // AOSP's FLAG_SUPPRESSABLE_BUBBLE (0x00000004) marks a bubble the system
        // is ALLOWED to hide while the app is in the foreground;
        // isBubbleSuppressed() then reports whether it currently is. Mac.apk
        // renders no bubbles at all (NotificationManager.areBubblesEnabled is
        // false), so nothing is ever suppressed -- but the app-set FLAG must
        // round-trip through the Builder.
        private static final int FLAG_SUPPRESSABLE_BUBBLE = 0x00000004;
        private boolean mSuppressableBubble;

        /** AOSP isBubbleSuppressable(): "@return whether this bubble should be
         *  suppressed when the same content is visible outside of the bubble." */
        public boolean isBubbleSuppressable() { return mSuppressableBubble; }

        /** AOSP isBubbleSuppressed(): "@return whether this bubble is currently
         *  suppressed" -- set by the SYSTEM, not the app, and false unless the
         *  bubble is actually on screen with its content shown elsewhere. No
         *  bubble is ever on screen here. */
        public boolean isBubbleSuppressed() { return false; }
        public static class Builder {
            private final BubbleMetadata m = new BubbleMetadata();
            public Builder() {}
            public Builder(String shortcutId) { m.mShortcutId = shortcutId; }
            public Builder(android.app.PendingIntent intent, android.graphics.drawable.Icon icon) {
                m.mIntent = intent; m.mIcon = icon;
            }
            public Builder setIntent(android.app.PendingIntent intent) { m.mIntent = intent; return this; }
            public Builder setDeleteIntent(android.app.PendingIntent intent) { m.mDeleteIntent = intent; return this; }
            public Builder setIcon(android.graphics.drawable.Icon icon) { m.mIcon = icon; return this; }
            public Builder setDesiredHeight(int height) { m.mDesiredHeight = height; m.mDesiredHeightResId = 0; return this; }
            public Builder setDesiredHeightResId(int resId) { m.mDesiredHeightResId = resId; m.mDesiredHeight = 0; return this; }
            public Builder setAutoExpandBubble(boolean autoExpand) { m.mAutoExpand = autoExpand; return this; }
            public Builder setSuppressNotification(boolean suppress) { m.mSuppressNotification = suppress; return this; }
            /** AOSP BubbleMetadata.Builder.setSuppressableBubble(boolean):
             *  "Indicates whether the bubble should be visually suppressed from
             *  the bubble stack if the user is viewing the same content outside
             *  of the bubble." */
            public Builder setSuppressableBubble(boolean suppressBubble) {
                m.mSuppressableBubble = suppressBubble;
                return this;
            }
            public BubbleMetadata build() { return m; }
        }
    }

    private android.content.LocusId mLocusId;
    public android.content.LocusId getLocusId() { return mLocusId; }
    public boolean hasPromotableCharacteristics() { return false; }
    public void setLatestEventInfo(android.content.Context context, CharSequence contentTitle,
            CharSequence contentText, android.app.PendingIntent contentIntent) { }

    public Notification publicVersion;

    // ---- AOSP API-36 constant completion (android.app.Notification) ----
    // Values extracted verbatim from the AOSP source for this class
    // (android16-release). javac INLINES a compile-time constant into the app at
    // ITS build time, so a shipped APK cannot observe a missing one at runtime --
    // these are here for our own framework code, for anything compiled against
    // THIS jar, and so the surface is complete rather than nearly complete.
    public static final int BADGE_ICON_LARGE = 2;
    public static final int BADGE_ICON_NONE = 0;
    public static final int BADGE_ICON_SMALL = 1;
    public static final String CATEGORY_LOCATION_SHARING = "location_sharing";
    public static final String CATEGORY_MISSED_CALL = "missed_call";
    public static final String CATEGORY_NAVIGATION = "navigation";
    public static final String CATEGORY_STOPWATCH = "stopwatch";
    public static final String CATEGORY_VOICEMAIL = "voicemail";
    public static final String CATEGORY_WORKOUT = "workout";
    public static final int COLOR_DEFAULT = 0;
    public static final String EXTRA_ANSWER_COLOR = "android.answerColor";
    public static final String EXTRA_ANSWER_INTENT = "android.answerIntent";
    public static final String EXTRA_AUDIO_CONTENTS_URI = "android.audioContents";
    public static final String EXTRA_CALL_IS_VIDEO = "android.callIsVideo";
    public static final String EXTRA_CALL_PERSON = "android.callPerson";
    public static final String EXTRA_CALL_TYPE = "android.callType";
    public static final String EXTRA_CHANNEL_GROUP_ID = "android.intent.extra.CHANNEL_GROUP_ID";
    public static final String EXTRA_COMPACT_ACTIONS = "android.compactActions";
    public static final String EXTRA_CONVERSATION_TITLE = "android.conversationTitle";
    public static final String EXTRA_DECLINE_COLOR = "android.declineColor";
    public static final String EXTRA_DECLINE_INTENT = "android.declineIntent";
    public static final String EXTRA_HANG_UP_INTENT = "android.hangUpIntent";
    public static final String EXTRA_IS_GROUP_CONVERSATION = "android.isGroupConversation";
    public static final String EXTRA_MEDIA_SESSION = "android.mediaSession";
    public static final String EXTRA_MESSAGING_PERSON = "android.messagingUser";
    public static final String EXTRA_NOTIFICATION_TAG = "android.intent.extra.NOTIFICATION_TAG";
    public static final String EXTRA_PEOPLE_LIST = "android.people.list";
    public static final String EXTRA_PICTURE_CONTENT_DESCRIPTION = "android.pictureContentDescription";
    public static final String EXTRA_PICTURE_ICON = "android.pictureIcon";
    public static final String EXTRA_REMOTE_INPUT_DRAFT = "android.remoteInputDraft";
    public static final String EXTRA_REMOTE_INPUT_HISTORY = "android.remoteInputHistory";
    public static final String EXTRA_SELF_DISPLAY_NAME = "android.selfDisplayName";
    public static final String EXTRA_SHOW_BIG_PICTURE_WHEN_COLLAPSED = "android.showBigPictureWhenCollapsed";
    public static final String EXTRA_VERIFICATION_ICON = "android.verificationIcon";
    public static final String EXTRA_VERIFICATION_TEXT = "android.verificationText";
    public static final int FLAG_PROMOTED_ONGOING = 0x00040000;
    public static final int FLAG_SHOW_LIGHTS = 0x00000001;
    public static final String INTENT_CATEGORY_NOTIFICATION_PREFERENCES = "android.intent.category.NOTIFICATION_PREFERENCES";
    public static final int STREAM_DEFAULT = -1;

    /** AOSP Notification.FOREGROUND_SERVICE_*: how promptly a foreground
     *  service's notification must appear (Notification.java:173/184/195,
     *  android16-release). Set via Builder.setForegroundServiceBehavior. */
    public static final int FOREGROUND_SERVICE_DEFAULT = 0;
    public static final int FOREGROUND_SERVICE_IMMEDIATE = 1;
    public static final int FOREGROUND_SERVICE_DEFERRED = 2;
}
