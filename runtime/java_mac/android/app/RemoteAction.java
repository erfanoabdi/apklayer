// android.app.RemoteAction -- an icon + title + PendingIntent describing
// an action a remote surface (PiP overlay, assistant) can invoke. AOSP
// API-26+.
//
// AOSP ref: frameworks/base/core/java/android/app/RemoteAction.java. A
// simple immutable data holder. Used by PictureInPictureParams.Builder
// .setActions and by some accessibility / assistant flows.
package android.app;

import android.graphics.drawable.Icon;

public final class RemoteAction implements android.os.Parcelable, Cloneable {
    private final Icon mIcon;
    private final CharSequence mTitle;
    private final CharSequence mContentDescription;
    private final PendingIntent mActionIntent;
    private boolean mEnabled = true;
    private boolean mShouldShowIcon = true;

    public RemoteAction(Icon icon, CharSequence title, CharSequence contentDescription, PendingIntent intent) {
        if (icon == null || title == null || contentDescription == null || intent == null) {
            throw new IllegalArgumentException("Expected icon, title, content description and action callback");
        }
        mIcon = icon;
        mTitle = title;
        mContentDescription = contentDescription;
        mActionIntent = intent;
    }

    public Icon getIcon() { return mIcon; }
    public CharSequence getTitle() { return mTitle; }
    public CharSequence getContentDescription() { return mContentDescription; }
    public PendingIntent getActionIntent() { return mActionIntent; }
    public void setEnabled(boolean enabled) { mEnabled = enabled; }
    public boolean isEnabled() { return mEnabled; }
    public void setShouldShowIcon(boolean shouldShowIcon) { mShouldShowIcon = shouldShowIcon; }
    public boolean shouldShowIcon() { return mShouldShowIcon; }

    @Override public RemoteAction clone() {
        RemoteAction r = new RemoteAction(mIcon, mTitle, mContentDescription, mActionIntent);
        r.setEnabled(mEnabled);
        r.setShouldShowIcon(mShouldShowIcon);
        return r;
    }

    /** Read side of writeToParcel. Mirrors AOSP's package-private
     *  RemoteAction(Parcel) (android11-release RemoteAction.java:42-49), which
     *  assigns the fields DIRECTLY and deliberately skips the public
     *  constructor's non-null validation -- a parcel is not an app call site,
     *  and re-running the argument check here would turn a lossy wire field
     *  into a throw. */
    private RemoteAction(android.os.Parcel in) {
        // The icon ABSENT-MARKER (see writeToParcel) must still be CONSUMED or
        // every field after it reads one slot early. There is no Icon behind
        // it, so the flag is read and discarded and mIcon arrives null.
        in.readInt();
        mIcon = null;
        mTitle = in.readCharSequence();
        mContentDescription = in.readCharSequence();
        mActionIntent = in.readInt() != 0 ? PendingIntent.CREATOR.createFromParcel(in) : null;
        mEnabled = in.readBoolean();
        mShouldShowIcon = in.readBoolean();
    }

    @Override public int describeContents() { return 0; }

    // AOSP android11-release core/java/android/app/RemoteAction.java:151-158.
    // Field order there is icon, title, contentDescription, actionIntent,
    // enabled, shouldShowIcon, and the order is load-bearing: a swapped pair
    // still yields a well-formed object, so only a field-by-field round trip
    // can catch it (debug/parcel_verify/ParcelCheck.java, carrier.remoteaction.*).
    //
    // This body matters beyond RemoteAction itself: PictureInPictureParams
    // marshals mUserActions/mCloseAction with writeTypedList/writeTypedObject,
    // so until now every element of an otherwise-real PiP parcel was riding
    // Parcel's empty-writer liveref fallback.
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        // mIcon ABSENT-MARKER. AOSP writes the Icon inline
        // (`mIcon.writeToParcel(out, 0)`), but android.graphics.drawable.Icon
        // does NOT implement Parcelable on this tree -- it has no
        // writeToParcel and no CREATOR -- so there is nothing to call. The
        // marker keeps the slot, one int wide and self-describing, instead of
        // silently dropping the field or throwing.
        // REMOVAL CONDITION: the moment Icon implements Parcelable, replace
        // this pair with dest.writeTypedObject(mIcon, flags) on the write side
        // and in.readTypedObject(Icon.CREATOR) on the read side.
        dest.writeInt(mIcon != null ? 1 : 0);
        // AOSP uses TextUtils.writeToParcel(cs, out, flags); Parcel
        // .writeCharSequence is that exact call (Parcel.java:1119-1121), so
        // the wire format is the same one CHAR_SEQUENCE_CREATOR reads.
        dest.writeCharSequence(mTitle);
        dest.writeCharSequence(mContentDescription);
        // AOSP writes mActionIntent inline with no null marker because the
        // public constructor guarantees it. The marker is here because the
        // Parcel constructor above can leave it null, and an unguarded
        // writeToParcel on such an instance would NPE mid-body -- leaving the
        // destination Parcel half-written.
        dest.writeInt(mActionIntent != null ? 1 : 0);
        if (mActionIntent != null) mActionIntent.writeToParcel(dest, flags);
        dest.writeBoolean(mEnabled);
        dest.writeBoolean(mShouldShowIcon);
    }

    public static final Creator<RemoteAction> CREATOR = new Creator<RemoteAction>() {
        @Override public RemoteAction createFromParcel(android.os.Parcel s) {
            return new RemoteAction(s);
        }
        @Override public RemoteAction[] newArray(int size) { return new RemoteAction[size]; }
    };

    /** AOSP RemoteAction.dump(String prefix, PrintWriter pw)
     *  (RemoteAction.java:170-179): one line of "key=value" pairs in AOSP's
     *  order. NOTE the argument order -- prefix FIRST, then the writer -- which
     *  is the reverse of ApplicationErrorReport.dump(Printer, String). AOSP
     *  dereferences mActionIntent unguarded; a null there is a caller bug we
     *  should not turn into an NPE thrown from the framework. */
    public void dump(String prefix, java.io.PrintWriter pw) {
        if (pw == null) return;
        pw.print(prefix);
        pw.print("title=" + getTitle());
        pw.print(" enabled=" + isEnabled());
        pw.print(" contentDescription=" + getContentDescription());
        pw.print(" icon=" + getIcon());
        pw.print(" action=" + (getActionIntent() != null ? getActionIntent().getIntent() : null));
        pw.print(" shouldShowIcon=" + shouldShowIcon());
        pw.println();
    }
}
