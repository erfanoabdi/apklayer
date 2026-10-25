package android.service.autofill;

import android.view.autofill.AutofillValue;

import java.util.regex.Pattern;

/**
 * API 33 replacement for Dataset.Builder's long setValue overload chain: the
 * value to fill into one view, the presentations to show for it, and the
 * optional filter regex the user's typing is matched against.
 *
 * Pure value object. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Field.java
 */
public final class Field {

    private final AutofillValue mValue;
    private final Presentations mPresentations;
    private final Pattern mFilter;

    private Field(AutofillValue value, Presentations presentations, Pattern filter) {
        mValue = value;
        mPresentations = presentations;
        mFilter = filter;
    }

    public AutofillValue getValue() { return mValue; }

    public Presentations getPresentations() { return mPresentations; }

    public Pattern getFilter() { return mFilter; }

    public static final class Builder {
        private AutofillValue mValue;
        private Presentations mPresentations;
        private Pattern mFilter;

        public Builder() { }

        public Builder setValue(AutofillValue value) {
            if (value == null) throw new NullPointerException("value");
            mValue = value;
            return this;
        }

        public Builder setPresentations(Presentations presentations) {
            if (presentations == null) throw new NullPointerException("presentations");
            mPresentations = presentations;
            return this;
        }

        /** Nullable in AOSP: passing null clears a previously set filter. */
        public Builder setFilter(Pattern filter) {
            mFilter = filter;
            return this;
        }

        public Field build() { return new Field(mValue, mPresentations, mFilter); }
    }
}
