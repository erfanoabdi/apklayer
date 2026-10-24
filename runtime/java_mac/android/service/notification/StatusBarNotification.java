package android.service.notification;

import android.app.Notification;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

/**
 * A posted notification as a NotificationListenerService sees it: the
 * Notification itself plus who posted it, when, and the keys the shade groups
 * and de-duplicates by.
 *
 * Mac.apk runs one app and has no shade to enumerate, so we do not manufacture
 * these -- but they are ordinary value objects, apps construct them directly
 * (the 10-argument constructor is public), and AOSP declares the class
 * `implements Parcelable`. That interface was missing here, which meant a
 * StatusBarNotification put in a Bundle or an Intent extra threw
 * ClassCastException rather than being written out.
 *
 * KEY / GROUP KEY. AOSP builds both, and code that de-duplicates a stream of
 * notifications depends on their exact shape:
 *   key      = user.getIdentifier() + "|" + pkg + "|" + id + "|" + tag + "|" + uid
 *   groupKey = user.getIdentifier() + "|" + pkg + "|" + (group or "g:"+sortKey
 *              or "c:"+id+":"+tag), unless an override group key is set, in
 *              which case that wins.
 * frameworks/base/core/java/android/service/notification/StatusBarNotification.java
 */
public class StatusBarNotification implements Parcelable {

    private final String mPkg;
    private final String mOpPkg;
    private final int mId;
    private final String mTag;
    private final int mUid;
    private final int mInitialPid;
    private final long mPostTime;
    private final Notification mNotification;
    private final UserHandle mUser;
    private final String mKey;
    private String mOverrideGroupKey;
    private String mGroupKey;

    /**
     * AOSP's public (deprecated) constructor. The `score` argument has been
     * ignored by the platform since API 21 but is part of the descriptor, so an
     * app compiled against the SDK links to THIS one -- not to the
     * overrideGroupKey variant below, which is @hide.
     * @deprecated matching AOSP.
     */
    @Deprecated
    public StatusBarNotification(String pkg, String opPkg, int id, String tag, int uid,
            int initialPid, int score, Notification notification, UserHandle user,
            long postTime) {
        this(pkg, opPkg, id, tag, uid, initialPid, notification, user, null, postTime);
    }

    /** AOSP's @hide constructor, kept because this tree already used it. */
    public StatusBarNotification(String pkg, String opPkg, int id, String tag,
            int uid, int initialPid, Notification notification,
            UserHandle user, String overrideGroupKey, long postTime) {
        if (pkg == null) throw new NullPointerException();
        mPkg = pkg;
        mOpPkg = opPkg;
        mId = id;
        mTag = tag;
        mUid = uid;
        mInitialPid = initialPid;
        mNotification = notification;
        mUser = user;
        mPostTime = postTime;
        mOverrideGroupKey = overrideGroupKey;
        mKey = key();
        mGroupKey = groupKey();
    }

    public StatusBarNotification(Parcel in) {
        mPkg = in.readString();
        mOpPkg = in.readString();
        mId = in.readInt();
        mTag = in.readInt() != 0 ? in.readString() : null;
        mUid = in.readInt();
        mInitialPid = in.readInt();
        mNotification = in.readInt() != 0
                ? Notification.CREATOR.createFromParcel(in) : null;
        mUser = UserHandle.CREATOR.createFromParcel(in);
        mOverrideGroupKey = in.readInt() != 0 ? in.readString() : null;
        mPostTime = in.readLong();
        mKey = key();
        mGroupKey = groupKey();
    }

    private String key() {
        String sbnKey = getUserId() + "|" + mPkg + "|" + mId + "|" + mTag + "|" + mUid;
        // AOSP appends the notification's own "shortcut key" for a bubble that
        // was posted with a locus id. We have no locus plumbing, so the base
        // form is the whole key -- which is what AOSP produces for every
        // ordinary notification.
        return sbnKey;
    }

    private String groupKey() {
        if (mOverrideGroupKey != null) {
            return getUserId() + "|" + mPkg + "|" + "g:" + mOverrideGroupKey;
        }
        final String group = mNotification == null ? null : mNotification.getGroup();
        final String sortKey = mNotification == null ? null : mNotification.getSortKey();
        if (group == null && sortKey == null) {
            // AOSP: an ungrouped notification is its own group.
            return getUserId() + "|" + mPkg + "|" + "c:" + mId + ":" + mTag;
        }
        return getUserId() + "|" + mPkg + "|"
                + (group == null ? "p:" + (mNotification == null ? 0
                        : mNotification.priority) : "g:" + group);
    }

    public String getPackageName() { return mPkg; }

    /** API 26. The package that actually posted, which differs from
     *  getPackageName() only for a notification posted on another app's behalf. */
    public String getOpPkg() { return mOpPkg; }

    public int getId() { return mId; }

    public String getTag() { return mTag; }

    /** API 29. */
    public int getUid() { return mUid; }

    public long getPostTime() { return mPostTime; }

    public Notification getNotification() { return mNotification; }

    /** API 21. */
    public UserHandle getUser() { return mUser; }

    /** @deprecated at API 21 in favour of getUser(). */
    @Deprecated
    public int getUserId() {
        return mUser == null ? UserHandle.myUserId() : mUser.getIdentifier();
    }

    public String getKey() { return mKey; }

    public String getGroupKey() { return mGroupKey; }

    /** API 24. Null unless the ranker re-grouped the notification. */
    public String getOverrideGroupKey() { return mOverrideGroupKey; }

    /** ⚠ ASSIGNS state and RECOMPUTES the group key, exactly as AOSP does. A
     *  setter that only stored the field would leave getGroupKey() stale. */
    public void setOverrideGroupKey(String overrideGroupKey) {
        mOverrideGroupKey = overrideGroupKey;
        mGroupKey = groupKey();
    }

    /** AOSP: clearable unless FLAG_ONGOING_EVENT or FLAG_NO_CLEAR is set. */
    public boolean isClearable() {
        return mNotification != null
                && ((mNotification.flags & Notification.FLAG_ONGOING_EVENT) == 0)
                && ((mNotification.flags & Notification.FLAG_NO_CLEAR) == 0);
    }

    public boolean isOngoing() {
        return mNotification != null
                && (mNotification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
    }

    /** AOSP: true when the notification carries FLAG_GROUP_SUMMARY or a group. */
    public boolean isGroup() {
        if (mOverrideGroupKey != null || isAppGroup()) return true;
        return false;
    }

    /** API 24. True when the APP itself grouped the notification (as opposed to
     *  the ranker overriding the group). */
    public boolean isAppGroup() {
        if (mNotification == null) return false;
        if (mNotification.getGroup() != null || mNotification.getSortKey() != null) {
            return true;
        }
        return false;
    }

    @Override public StatusBarNotification clone() {
        return new StatusBarNotification(mPkg, mOpPkg, mId, mTag, mUid, mInitialPid,
                mNotification == null ? null : mNotification.clone(), mUser,
                mOverrideGroupKey, mPostTime);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(mPkg);
        out.writeString(mOpPkg);
        out.writeInt(mId);
        if (mTag != null) { out.writeInt(1); out.writeString(mTag); } else { out.writeInt(0); }
        out.writeInt(mUid);
        out.writeInt(mInitialPid);
        if (mNotification != null) {
            out.writeInt(1);
            mNotification.writeToParcel(out, flags);
        } else {
            out.writeInt(0);
        }
        UserHandle.writeToParcel(mUser, out);
        if (mOverrideGroupKey != null) {
            out.writeInt(1);
            out.writeString(mOverrideGroupKey);
        } else {
            out.writeInt(0);
        }
        out.writeLong(mPostTime);
    }

    @Override public String toString() {
        return "StatusBarNotification(pkg=" + mPkg + " id=" + mId + " tag=" + mTag
                + " key=" + mKey + ": " + mNotification + ")";
    }

    public static final Creator<StatusBarNotification> CREATOR =
            new Creator<StatusBarNotification>() {
        @Override public StatusBarNotification createFromParcel(Parcel source) {
            return new StatusBarNotification(source);
        }
        @Override public StatusBarNotification[] newArray(int size) {
            return new StatusBarNotification[size];
        }
    };
}
