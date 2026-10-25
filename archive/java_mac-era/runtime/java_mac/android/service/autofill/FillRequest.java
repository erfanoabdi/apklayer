package android.service.autofill;

import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the platform hands AutofillService.onFillRequest: the activity contexts
 * to fill, the request flags, the service's own client state from last time,
 * and the autofill hints the focused fields declared.
 *
 * ⚠ INCOMPLETE: getInlineSuggestionsRequest() returns
 * android.view.inputmethod.InlineSuggestionsRequest, which does not exist in
 * this tree, so the method is MISSING and reported as a residual gap rather
 * than faked. Everything else is present.
 *
 * Mac.apk never issues a fill request. See the package note on AutofillService.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   FLAG_MANUAL_REQUEST = 1 (0x1)
 *   FLAG_COMPATIBILITY_MODE_REQUEST = 2 (0x2)
 *   FLAG_SUPPORTS_FILL_DIALOG = 64 (0x40)   -- deprecated in AOSP
 *
 * Source: frameworks/base/core/java/android/service/autofill/FillRequest.java
 */
public final class FillRequest implements Parcelable {

    public static final int FLAG_MANUAL_REQUEST = 0x1;
    public static final int FLAG_COMPATIBILITY_MODE_REQUEST = 0x2;
    /** @deprecated in AOSP; the fill dialog is now always supported. */
    @Deprecated public static final int FLAG_SUPPORTS_FILL_DIALOG = 0x40;

    private final int mId;
    private final List<FillContext> mContexts;
    private final List<String> mHints;
    private final Bundle mClientState;
    private final int mFlags;
    private final IntentSender mDelayedFillIntentSender;

    FillRequest(int id, List<FillContext> contexts, List<String> hints, Bundle clientState,
            int flags, IntentSender delayedFillIntentSender) {
        mId = id;
        mContexts = contexts == null ? Collections.<FillContext>emptyList() : contexts;
        mHints = hints == null ? Collections.<String>emptyList() : hints;
        mClientState = clientState;
        mFlags = flags;
        mDelayedFillIntentSender = delayedFillIntentSender;
    }

    public int getId() { return mId; }

    public int getFlags() { return mFlags; }

    public List<FillContext> getFillContexts() { return mContexts; }

    /** API 33. Never null in AOSP -- an empty list when no hints apply. */
    public List<String> getHints() { return mHints; }

    public Bundle getClientState() { return mClientState; }

    /** API 33. Non-null only when the response set FLAG_DELAY_FILL. */
    public IntentSender getDelayedFillIntentSender() { return mDelayedFillIntentSender; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeTypedList(mContexts);
        parcel.writeStringList(mHints);
        parcel.writeBundle(mClientState);
        parcel.writeInt(mFlags);
        parcel.writeTypedObject(mDelayedFillIntentSender, flags);
    }

    @Override public String toString() {
        return "FillRequest[id=" + mId + ", flags=" + mFlags + "]";
    }

    public static final Creator<FillRequest> CREATOR = new Creator<FillRequest>() {
        @Override public FillRequest createFromParcel(Parcel parcel) {
            final int id = parcel.readInt();
            final List<FillContext> contexts =
                    parcel.createTypedArrayList(FillContext.CREATOR);
            final List<String> hints = new ArrayList<String>();
            parcel.readStringList(hints);
            final Bundle clientState = parcel.readBundle();
            final int flags = parcel.readInt();
            final IntentSender sender = parcel.readTypedObject(IntentSender.CREATOR);
            return new FillRequest(id, contexts, hints, clientState, flags, sender);
        }
        @Override public FillRequest[] newArray(int size) { return new FillRequest[size]; }
    };
}
