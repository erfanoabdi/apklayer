package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import android.view.autofill.AutofillId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the text of a CustomDescription child by running a regex over one or
 * more autofilled fields and substituting the capture groups into a template
 * (for example, masking a card number down to its last four digits).
 *
 * Pure value object with a builder. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/CharSequenceTransformation.java
 */
public final class CharSequenceTransformation implements Parcelable, Transformation {

    private final Map<AutofillId, Pair<Pattern, String>> mFields;

    private CharSequenceTransformation(Map<AutofillId, Pair<Pattern, String>> fields) {
        mFields = fields;
    }

    Map<AutofillId, Pair<Pattern, String>> getFields() { return mFields; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        final int size = mFields.size();
        final AutofillId[] ids = new AutofillId[size];
        final String[] regexes = new String[size];
        final String[] substs = new String[size];
        int i = 0;
        for (Map.Entry<AutofillId, Pair<Pattern, String>> e : mFields.entrySet()) {
            ids[i] = e.getKey();
            regexes[i] = e.getValue().first.pattern();
            substs[i] = e.getValue().second;
            i++;
        }
        dest.writeTypedArray(ids, flags);
        dest.writeStringArray(regexes);
        dest.writeStringArray(substs);
    }

    public static final Creator<CharSequenceTransformation> CREATOR =
            new Creator<CharSequenceTransformation>() {
        @Override public CharSequenceTransformation createFromParcel(Parcel parcel) {
            AutofillId[] ids = parcel.createTypedArray(AutofillId.CREATOR);
            String[] regexes = parcel.createStringArray();
            String[] substs = parcel.createStringArray();
            Builder builder = new Builder(ids[0], Pattern.compile(regexes[0]), substs[0]);
            for (int i = 1; i < ids.length; i++) {
                builder.addField(ids[i], Pattern.compile(regexes[i]), substs[i]);
            }
            return builder.build();
        }
        @Override public CharSequenceTransformation[] newArray(int size) {
            return new CharSequenceTransformation[size];
        }
    };

    public static class Builder {
        // LinkedHashMap: AOSP concatenates the fields IN INSERTION ORDER, so
        // the map type is load-bearing, not incidental.
        private final Map<AutofillId, Pair<Pattern, String>> mFields =
                new LinkedHashMap<AutofillId, Pair<Pattern, String>>();
        private boolean mDestroyed;

        public Builder(AutofillId id, Pattern regex, String subst) {
            addField(id, regex, subst);
        }

        public Builder addField(AutofillId id, Pattern regex, String subst) {
            throwIfDestroyed();
            if (id == null) throw new NullPointerException("id");
            if (regex == null) throw new NullPointerException("regex");
            if (subst == null) throw new NullPointerException("subst");
            mFields.put(id, new Pair<Pattern, String>(regex, subst));
            return this;
        }

        public CharSequenceTransformation build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new CharSequenceTransformation(mFields);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
