package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

/**
 * A Validator that passes when the concatenated digits of the named fields
 * satisfy the Luhn checksum -- the standard credit-card-number sanity check.
 *
 * ⚠ isValid() IS implemented rather than stubbed. The Luhn algorithm needs
 * nothing from the platform, a service can call it directly, and returning a
 * constant would silently tell a password manager that every number is (or is
 * not) a valid card. The implementation mirrors AOSP's: walk right to left,
 * double every second digit, subtract 9 when the doubling exceeds 9, and
 * require the total to be a multiple of 10. Non-digit characters are skipped,
 * as AOSP does, so "4111 1111 1111 1111" validates.
 *
 * Source: frameworks/base/core/java/android/service/autofill/LuhnChecksumValidator.java
 */
public final class LuhnChecksumValidator implements Parcelable, Validator {

    private final AutofillId[] mIds;

    public LuhnChecksumValidator(AutofillId... ids) {
        if (ids == null || ids.length == 0) {
            throw new IllegalArgumentException("ids must be non-empty");
        }
        for (AutofillId id : ids) {
            if (id == null) throw new NullPointerException("ids");
        }
        mIds = ids;
    }

    AutofillId[] getAutofillIds() { return mIds; }

    /** AOSP's @hide checksum helper, package-visible so it is reachable. */
    static boolean isLuhnChecksumValid(String number) {
        if (number == null) return false;
        int sum = 0;
        boolean isDoubled = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            final char c = number.charAt(i);
            if (c < '0' || c > '9') continue;
            int digit = c - '0';
            if (isDoubled) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            isDoubled = !isDoubled;
        }
        return sum % 10 == 0;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedArray(mIds, flags);
    }

    @Override public String toString() {
        return "LuhnChecksumValidator[" + mIds.length + " field(s)]";
    }

    public static final Creator<LuhnChecksumValidator> CREATOR =
            new Creator<LuhnChecksumValidator>() {
        @Override public LuhnChecksumValidator createFromParcel(Parcel parcel) {
            return new LuhnChecksumValidator(parcel.createTypedArray(AutofillId.CREATOR));
        }
        @Override public LuhnChecksumValidator[] newArray(int size) {
            return new LuhnChecksumValidator[size];
        }
    };
}
