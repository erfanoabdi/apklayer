package android.service.autofill;

import android.icu.text.DateFormat;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

/**
 * Renders a date-typed field into a CustomDescription child using an ICU
 * DateFormat (an expiry date shown as "MM/yy", say).
 *
 * Pure value object. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/DateTransformation.java
 */
public final class DateTransformation implements Parcelable, Transformation {

    private final AutofillId mFieldId;
    private final DateFormat mDateFormat;

    public DateTransformation(AutofillId id, DateFormat dateFormat) {
        if (id == null) throw new NullPointerException("id");
        if (dateFormat == null) throw new NullPointerException("dateFormat");
        mFieldId = id;
        mDateFormat = dateFormat;
    }

    AutofillId getFieldId() { return mFieldId; }

    DateFormat getDateFormat() { return mDateFormat; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mFieldId, flags);
        // AOSP writes the DateFormat with writeSerializable; ICU DateFormat is
        // Serializable, which is the only way an arbitrary subclass crosses.
        parcel.writeSerializable(mDateFormat);
    }

    @Override public String toString() {
        return "DateTransformation[" + mFieldId + "]";
    }

    public static final Creator<DateTransformation> CREATOR =
            new Creator<DateTransformation>() {
        @Override public DateTransformation createFromParcel(Parcel parcel) {
            AutofillId id = parcel.readTypedObject(AutofillId.CREATOR);
            DateFormat dateFormat = (DateFormat) parcel.readSerializable();
            return new DateTransformation(id, dateFormat);
        }
        @Override public DateTransformation[] newArray(int size) {
            return new DateTransformation[size];
        }
    };
}
