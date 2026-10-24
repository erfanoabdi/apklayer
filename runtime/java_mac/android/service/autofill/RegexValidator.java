package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

import java.util.regex.Pattern;

/**
 * A Validator that passes when one field's value matches a regex.
 *
 * Pure value object. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/RegexValidator.java
 */
public final class RegexValidator implements Parcelable, Validator {

    private final AutofillId mId;
    private final Pattern mRegex;

    public RegexValidator(AutofillId id, Pattern regex) {
        if (id == null) throw new NullPointerException("id");
        if (regex == null) throw new NullPointerException("regex");
        mId = id;
        mRegex = regex;
    }

    AutofillId getAutofillId() { return mId; }

    Pattern getRegex() { return mRegex; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mId, flags);
        parcel.writeSerializable(mRegex);
    }

    @Override public String toString() { return "RegexValidator[" + mId + "]"; }

    public static final Creator<RegexValidator> CREATOR = new Creator<RegexValidator>() {
        @Override public RegexValidator createFromParcel(Parcel parcel) {
            AutofillId id = parcel.readTypedObject(AutofillId.CREATOR);
            Pattern regex = (Pattern) parcel.readSerializable();
            return new RegexValidator(id, regex);
        }
        @Override public RegexValidator[] newArray(int size) {
            return new RegexValidator[size];
        }
    };
}
