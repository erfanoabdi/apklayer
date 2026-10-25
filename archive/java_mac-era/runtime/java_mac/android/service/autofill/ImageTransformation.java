package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Picks the drawable for a CustomDescription child by matching one field's
 * value against a list of regexes (the card-brand logo case).
 *
 * Pure value object with a builder. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/ImageTransformation.java
 */
public final class ImageTransformation implements Parcelable, Transformation {

    private final AutofillId mId;
    private final List<Option> mOptions;

    private ImageTransformation(AutofillId id, List<Option> options) {
        mId = id;
        mOptions = options;
    }

    AutofillId getAutofillId() { return mId; }

    List<Option> getOptions() { return mOptions; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mId, flags);
        final int size = mOptions.size();
        final String[] regexes = new String[size];
        final int[] resIds = new int[size];
        final CharSequence[] descriptions = new CharSequence[size];
        for (int i = 0; i < size; i++) {
            Option o = mOptions.get(i);
            regexes[i] = o.pattern.pattern();
            resIds[i] = o.resId;
            descriptions[i] = o.contentDescription;
        }
        dest.writeStringArray(regexes);
        dest.writeIntArray(resIds);
        dest.writeCharSequenceArray(descriptions);
    }

    public static final Creator<ImageTransformation> CREATOR =
            new Creator<ImageTransformation>() {
        @Override public ImageTransformation createFromParcel(Parcel parcel) {
            AutofillId id = parcel.readTypedObject(AutofillId.CREATOR);
            String[] regexes = parcel.createStringArray();
            int[] resIds = parcel.createIntArray();
            CharSequence[] descriptions = parcel.readCharSequenceArray();
            Builder builder = new Builder(id, Pattern.compile(regexes[0]), resIds[0],
                    descriptions == null ? "" : descriptions[0]);
            for (int i = 1; i < regexes.length; i++) {
                builder.addOption(Pattern.compile(regexes[i]), resIds[i],
                        descriptions == null ? "" : descriptions[i]);
            }
            return builder.build();
        }
        @Override public ImageTransformation[] newArray(int size) {
            return new ImageTransformation[size];
        }
    };

    /** AOSP's @hide per-option record. */
    static final class Option {
        final Pattern pattern;
        final int resId;
        final CharSequence contentDescription;

        Option(Pattern pattern, int resId, CharSequence contentDescription) {
            this.pattern = pattern;
            this.resId = resId;
            this.contentDescription = contentDescription;
        }
    }

    public static class Builder {
        private final AutofillId mId;
        private final List<Option> mOptions = new ArrayList<Option>();
        private boolean mDestroyed;

        /** @deprecated in AOSP: an option with no content description is
         *  unreadable to a screen reader. */
        @Deprecated
        public Builder(AutofillId id, Pattern regex, int resId) {
            if (id == null) throw new NullPointerException("id");
            mId = id;
            addOptionInternal(regex, resId, null);
        }

        public Builder(AutofillId id, Pattern regex, int resId,
                CharSequence contentDescription) {
            if (id == null) throw new NullPointerException("id");
            if (contentDescription == null) {
                throw new NullPointerException("contentDescription");
            }
            mId = id;
            addOptionInternal(regex, resId, contentDescription);
        }

        /** @deprecated in AOSP; use the content-description overload. */
        @Deprecated
        public Builder addOption(Pattern regex, int resId) {
            throwIfDestroyed();
            addOptionInternal(regex, resId, null);
            return this;
        }

        public Builder addOption(Pattern regex, int resId, CharSequence contentDescription) {
            throwIfDestroyed();
            if (contentDescription == null) {
                throw new NullPointerException("contentDescription");
            }
            addOptionInternal(regex, resId, contentDescription);
            return this;
        }

        public ImageTransformation build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new ImageTransformation(mId, mOptions);
        }

        private void addOptionInternal(Pattern regex, int resId,
                CharSequence contentDescription) {
            if (regex == null) throw new NullPointerException("regex");
            if (resId == 0) throw new IllegalArgumentException("resId cannot be 0");
            mOptions.add(new Option(regex, resId, contentDescription));
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
