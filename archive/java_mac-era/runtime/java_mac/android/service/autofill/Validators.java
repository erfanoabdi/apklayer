package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Static combinators over Validator: and / or / not.
 *
 * ⚠ AOSP REJECTS a Validator it did not create -- the interface is closed, and
 * accepting a third-party one would mean the platform later calls into
 * arbitrary app code while filling another app's form. That check is
 * reproduced, because a service relying on the IllegalArgumentException must
 * still see it.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Validators.java
 */
public final class Validators {

    private Validators() {
        throw new UnsupportedOperationException("contains static methods only");
    }

    public static Validator and(Validator... validators) {
        return new AndValidator(check(validators));
    }

    public static Validator or(Validator... validators) {
        return new OrValidator(check(validators));
    }

    public static Validator not(Validator validator) {
        if (!(validator instanceof Parcelable)) {
            throw new IllegalArgumentException("validator not provided by Android System: "
                    + validator);
        }
        return new NotValidator(validator);
    }

    private static Validator[] check(Validator[] validators) {
        if (validators == null) throw new NullPointerException("validators");
        for (Validator v : validators) {
            if (!(v instanceof Parcelable)) {
                throw new IllegalArgumentException(
                        "validator not provided by Android System: " + v);
            }
        }
        return validators;
    }

    // AOSP's combinators are @hide InternalValidator subclasses. They are
    // Parcelable so a combinator can itself be nested inside another one --
    // and(or(a,b), not(c)) is legal on a device.
    private abstract static class CompositeValidator implements Validator, Parcelable {
        final Validator[] mValidators;

        CompositeValidator(Validator[] validators) { mValidators = validators; }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mValidators.length);
            for (Validator v : mValidators) {
                dest.writeParcelable((Parcelable) v, flags);
            }
        }
    }

    private static final class AndValidator extends CompositeValidator {
        AndValidator(Validator[] validators) { super(validators); }
        @Override public String toString() { return "AndValidator"; }
    }

    private static final class OrValidator extends CompositeValidator {
        OrValidator(Validator[] validators) { super(validators); }
        @Override public String toString() { return "OrValidator"; }
    }

    private static final class NotValidator extends CompositeValidator {
        NotValidator(Validator validator) { super(new Validator[] { validator }); }
        @Override public String toString() { return "NotValidator"; }
    }
}
