package android.service.autofill;

import android.icu.text.DateFormat;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Normalises a date-typed field to the service's own format before the
 * platform compares it against what was filled, so a purely cosmetic
 * difference does not trigger a save prompt.
 *
 * Pure value object. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/DateValueSanitizer.java
 */
public final class DateValueSanitizer implements Parcelable, Sanitizer {

    private final DateFormat mDateFormat;

    public DateValueSanitizer(DateFormat dateFormat) {
        if (dateFormat == null) throw new NullPointerException("dateFormat");
        mDateFormat = dateFormat;
    }

    DateFormat getDateFormat() { return mDateFormat; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeSerializable(mDateFormat);
    }

    @Override public String toString() { return "DateValueSanitizer[]"; }

    public static final Creator<DateValueSanitizer> CREATOR =
            new Creator<DateValueSanitizer>() {
        @Override public DateValueSanitizer createFromParcel(Parcel parcel) {
            return new DateValueSanitizer((DateFormat) parcel.readSerializable());
        }
        @Override public DateValueSanitizer[] newArray(int size) {
            return new DateValueSanitizer[size];
        }
    };
}
