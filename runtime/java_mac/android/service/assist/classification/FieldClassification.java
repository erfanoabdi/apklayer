package android.service.assist.classification;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * API 34 result of the field-classification pass: one autofillable view plus
 * the hint strings (View.AUTOFILL_HINT_*) the detection service thinks apply
 * to it.
 *
 * A pure immutable value object in AOSP too -- the platform builds it, the
 * service reads it. Nothing about it needs a running detection service, so the
 * whole class is faithful rather than stubbed; an app that constructs one and
 * reads the getters back gets exactly what it put in.
 *
 * Source: frameworks/base/core/java/android/service/assist/classification/FieldClassification.java
 */
public final class FieldClassification implements Parcelable {

    private final AutofillId mAutofillId;
    private final Set<String> mHints;

    public FieldClassification(AutofillId id, Set<String> hints) {
        if (id == null) throw new NullPointerException("id");
        if (hints == null) throw new NullPointerException("hints");
        mAutofillId = id;
        // AOSP returns an unmodifiable view; a caller must not be able to
        // mutate the classification after the fact.
        mHints = Collections.unmodifiableSet(new LinkedHashSet<String>(hints));
    }

    public AutofillId getAutofillId() { return mAutofillId; }

    public Set<String> getHints() { return mHints; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mAutofillId, flags);
        dest.writeStringArray(mHints.toArray(new String[0]));
    }

    @Override public String toString() {
        return "FieldClassification[id=" + mAutofillId + ", hints=" + mHints + "]";
    }

    public static final Creator<FieldClassification> CREATOR =
            new Creator<FieldClassification>() {
        @Override public FieldClassification createFromParcel(Parcel in) {
            AutofillId id = in.readParcelable(AutofillId.class.getClassLoader());
            String[] hints = in.createStringArray();
            Set<String> set = new LinkedHashSet<String>();
            if (hints != null) Collections.addAll(set, hints);
            return new FieldClassification(id, set);
        }
        @Override public FieldClassification[] newArray(int size) {
            return new FieldClassification[size];
        }
    };
}
