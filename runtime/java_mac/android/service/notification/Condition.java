package android.service.notification;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * The current state of an AutomaticZenRule, as reported by a
 * ConditionProviderService or by an app calling
 * NotificationManager.setAutomaticZenRuleState().
 *
 * This is a plain immutable value object in AOSP too -- the platform reads the
 * fields, it does not call back into the object. Everything below is therefore
 * FAITHFUL rather than stubbed, including the argument validation, the 1000-
 * character trimming, and the two static helpers; those are the parts an app
 * actually observes, and getting them wrong would be worse than not having the
 * class.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt
 * (signature format 2.0):
 *   SCHEME = "condition"
 *   STATE_FALSE=0, STATE_TRUE=1, STATE_UNKNOWN=2, STATE_ERROR=3
 *   SOURCE_UNKNOWN=0, SOURCE_USER_ACTION=1, SOURCE_SCHEDULE=2, SOURCE_CONTEXT=3
 *   FLAG_RELEVANT_NOW=1 (0x1), FLAG_RELEVANT_ALWAYS=2 (0x2)
 * Note STATE_UNKNOWN is 2 and STATE_ERROR is 3 -- NOT the other way round, and
 * not -1.
 *
 * Source: frameworks/base/core/java/android/service/notification/Condition.java
 */
public final class Condition implements Parcelable {

    public static final String SCHEME = "condition";

    public static final int STATE_FALSE = 0;
    public static final int STATE_TRUE = 1;
    public static final int STATE_UNKNOWN = 2;
    public static final int STATE_ERROR = 3;

    public static final int SOURCE_UNKNOWN = 0;
    public static final int SOURCE_USER_ACTION = 1;
    public static final int SOURCE_SCHEDULE = 2;
    public static final int SOURCE_CONTEXT = 3;

    public static final int FLAG_RELEVANT_NOW = 0x1;
    public static final int FLAG_RELEVANT_ALWAYS = 0x2;

    /** AOSP's own cap on every string field. frameworks/base/.../Condition.java:121 */
    public static final int MAX_STRING_LENGTH = 1000;

    public final Uri id;
    public final String summary;
    public final String line1;
    public final String line2;
    public final int icon;
    public final int state;
    public final int source;
    public final int flags;

    public Condition(Uri id, String summary, int state) {
        this(id, summary, "", "", -1, state, SOURCE_UNKNOWN, FLAG_RELEVANT_ALWAYS);
    }

    /** API 35. */
    public Condition(Uri id, String summary, int state, int source) {
        this(id, summary, "", "", -1, state, source, FLAG_RELEVANT_ALWAYS);
    }

    public Condition(Uri id, String summary, String line1, String line2, int icon,
            int state, int flags) {
        this(id, summary, line1, line2, icon, state, SOURCE_UNKNOWN, flags);
    }

    /** API 35. The canonical constructor every other one delegates to. */
    public Condition(Uri id, String summary, String line1, String line2, int icon,
            int state, int source, int flags) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (summary == null) throw new IllegalArgumentException("summary is required");
        if (!isValidState(state)) {
            throw new IllegalArgumentException("state is invalid: " + state);
        }
        if (source < SOURCE_UNKNOWN || source > SOURCE_CONTEXT) {
            throw new IllegalArgumentException(
                    "Condition source must be one of SOURCE_UNKNOWN, SOURCE_USER_ACTION, "
                            + "SOURCE_SCHEDULE, or SOURCE_CONTEXT");
        }
        this.id = id;
        this.summary = trim(summary);
        this.line1 = trim(line1);
        this.line2 = trim(line2);
        this.icon = icon;
        this.state = state;
        this.source = source;
        this.flags = flags;
    }

    public Condition(Parcel source) {
        this(source.<Uri>readParcelable(Condition.class.getClassLoader()),
                source.readString(),
                source.readString(),
                source.readString(),
                source.readInt(),
                source.readInt(),
                source.readInt(),
                source.readInt());
    }

    private static boolean isValidState(int state) {
        return state >= STATE_FALSE && state <= STATE_ERROR;
    }

    private static String trim(String input) {
        if (input != null && input.length() > MAX_STRING_LENGTH) {
            return input.substring(0, MAX_STRING_LENGTH);
        }
        return input;
    }

    public Condition copy() {
        Parcel parcel = Parcel.obtain();
        try {
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new Condition(parcel);
        } finally {
            parcel.recycle();
        }
    }

    /** AOSP builds "condition://<callerPackage>" and leaves the path to the
     *  caller. */
    public static Uri.Builder newId(Context context) {
        return new Uri.Builder().scheme(SCHEME).authority(context.getPackageName());
    }

    public static boolean isValidId(Uri id, String pkg) {
        return id != null && SCHEME.equals(id.getScheme()) && pkg.equals(id.getAuthority());
    }

    /** ⚠ AOSP THROWS on an invalid state rather than returning a fallback
     *  string; an app that relies on the exception must still see it. */
    public static String stateToString(int state) {
        if (state == STATE_FALSE) return "STATE_FALSE";
        if (state == STATE_TRUE) return "STATE_TRUE";
        if (state == STATE_UNKNOWN) return "STATE_UNKNOWN";
        if (state == STATE_ERROR) return "STATE_ERROR";
        throw new IllegalArgumentException("state is invalid: " + state);
    }

    public static String relevanceToString(int flags) {
        final boolean now = (flags & FLAG_RELEVANT_NOW) != 0;
        final boolean always = (flags & FLAG_RELEVANT_ALWAYS) != 0;
        if (!now && !always) return "NONE";
        if (now && always) return "NOW, ALWAYS";
        return now ? "NOW" : "ALWAYS";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(id, 0);
        dest.writeString(summary);
        dest.writeString(line1);
        dest.writeString(line2);
        dest.writeInt(icon);
        dest.writeInt(state);
        dest.writeInt(source);
        dest.writeInt(this.flags);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Condition)) return false;
        Condition other = (Condition) o;
        return java.util.Objects.equals(other.id, id)
                && java.util.Objects.equals(other.summary, summary)
                && java.util.Objects.equals(other.line1, line1)
                && java.util.Objects.equals(other.line2, line2)
                && other.icon == icon
                && other.state == state
                && other.source == source
                && other.flags == flags;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(id, summary, line1, line2,
                Integer.valueOf(icon), Integer.valueOf(state),
                Integer.valueOf(source), Integer.valueOf(flags));
    }

    @Override public String toString() {
        return "Condition[id=" + id + ",summary=" + summary + ",line1=" + line1
                + ",line2=" + line2 + ",icon=" + icon
                + ",state=" + stateToString(state)
                + ",source=" + source
                + ",flags=" + flags + "]";
    }

    public static final Creator<Condition> CREATOR = new Creator<Condition>() {
        @Override public Condition createFromParcel(Parcel source) {
            return new Condition(source);
        }
        @Override public Condition[] newArray(int size) { return new Condition[size]; }
    };
}
