package android.service.autofill;

import android.content.IntentSender;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One row of an autofill dropdown: the values to fill into a set of views, plus
 * how to present the row and (optionally) an authentication step to run first.
 *
 * Pure value object with a long-lived builder. See the package note on
 * AutofillService for why nothing here is stubbed. The deprecated setValue
 * overload chain is kept in full: apps built against older SDKs link against
 * those exact descriptors, and dropping one is a NoSuchMethodError at fill time.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Dataset.java
 */
public final class Dataset implements Parcelable {

    private final Map<AutofillId, Field> mFields;
    private final Map<String, Field> mHintFields;
    private final Field mFieldForAllHints;
    private final IntentSender mAuthentication;
    private final String mId;

    private Dataset(Builder builder) {
        mFields = builder.mFields;
        mHintFields = builder.mHintFields;
        mFieldForAllHints = builder.mFieldForAllHints;
        mAuthentication = builder.mAuthentication;
        mId = builder.mId;
    }

    // AOSP exposes these only to the platform (@hide getFieldIds/getFieldValues).
    List<AutofillId> getFieldIds() { return new ArrayList<AutofillId>(mFields.keySet()); }

    Field getField(AutofillId id) { return mFields.get(id); }

    IntentSender getAuthentication() { return mAuthentication; }

    String getId() { return mId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        final int n = mFields.size();
        dest.writeInt(n);
        for (Map.Entry<AutofillId, Field> e : mFields.entrySet()) {
            dest.writeTypedObject(e.getKey(), flags);
            writeField(dest, e.getValue(), flags);
        }
        dest.writeInt(mHintFields.size());
        for (Map.Entry<String, Field> e : mHintFields.entrySet()) {
            dest.writeString(e.getKey());
            writeField(dest, e.getValue(), flags);
        }
        dest.writeTypedObject(mAuthentication, flags);
        dest.writeString(mId);
    }

    // Field is NOT Parcelable in AOSP (it is a plain value type), so its parts
    // are written individually. Presentations is not Parcelable either; only
    // the pieces that are cross the parcel.
    private static void writeField(Parcel dest, Field field, int flags) {
        if (field == null) { dest.writeInt(0); return; }
        dest.writeInt(1);
        dest.writeTypedObject(field.getValue(), flags);
        Pattern filter = field.getFilter();
        dest.writeString(filter == null ? null : filter.pattern());
    }

    private static Field readField(Parcel in) {
        if (in.readInt() == 0) return null;
        AutofillValue value = in.readTypedObject(AutofillValue.CREATOR);
        String filter = in.readString();
        Field.Builder b = new Field.Builder();
        if (value != null) b.setValue(value);
        if (filter != null) b.setFilter(Pattern.compile(filter));
        return b.build();
    }

    @Override public String toString() {
        return "Dataset[id=" + mId + ", fields=" + mFields.size() + "]";
    }

    public static final Creator<Dataset> CREATOR = new Creator<Dataset>() {
        @Override public Dataset createFromParcel(Parcel in) {
            final Builder builder = new Builder();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                AutofillId id = in.readTypedObject(AutofillId.CREATOR);
                Field f = readField(in);
                builder.setField(id, f);
            }
            n = in.readInt();
            for (int i = 0; i < n; i++) {
                String hint = in.readString();
                Field f = readField(in);
                if (f != null) builder.setField(hint, f);
            }
            builder.setAuthentication(in.readTypedObject(IntentSender.CREATOR));
            builder.setId(in.readString());
            return builder.build();
        }
        @Override public Dataset[] newArray(int size) { return new Dataset[size]; }
    };

    public static final class Builder {
        // LinkedHashMap: AOSP fills in insertion order, and a service that
        // depends on which field is focused first would see a difference.
        private final Map<AutofillId, Field> mFields =
                new LinkedHashMap<AutofillId, Field>();
        private final Map<String, Field> mHintFields = new LinkedHashMap<String, Field>();
        private Field mFieldForAllHints;
        private IntentSender mAuthentication;
        private String mId;
        private Presentations mPresentations;
        private RemoteViews mPresentation;
        private boolean mDestroyed;

        public Builder() { }

        /** @deprecated at API 33 in favour of the Presentations overload. */
        @Deprecated
        public Builder(RemoteViews presentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            mPresentation = presentation;
        }

        /** API 33. */
        public Builder(Presentations presentations) {
            if (presentations == null) throw new NullPointerException("presentations");
            mPresentations = presentations;
        }

        public Builder setAuthentication(IntentSender authentication) {
            throwIfDestroyed();
            mAuthentication = authentication;
            return this;
        }

        public Builder setId(String id) {
            throwIfDestroyed();
            mId = id;
            return this;
        }

        /** API 33. */
        public Builder setField(AutofillId id, Field field) {
            throwIfDestroyed();
            if (id == null) throw new NullPointerException("id");
            mFields.put(id, field);
            return this;
        }

        /** API 33 hint-keyed variant, for a dataset filled by autofill hint
         *  rather than by view id. */
        public Builder setField(String hint, Field field) {
            throwIfDestroyed();
            if (hint == null) throw new NullPointerException("hint");
            if (field == null) throw new NullPointerException("field");
            mHintFields.put(hint, field);
            return this;
        }

        /** API 33. */
        public Builder setFieldForAllHints(Field field) {
            throwIfDestroyed();
            if (field == null) throw new NullPointerException("field");
            mFieldForAllHints = field;
            return this;
        }

        /** @deprecated at API 33; use setField(AutofillId, Field). */
        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value) {
            return setField(id, toField(value, null));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, RemoteViews presentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setField(id, toField(value, null));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, Pattern filter) {
            return setField(id, toField(value, filter));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, Pattern filter,
                RemoteViews presentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setField(id, toField(value, filter));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, RemoteViews presentation,
                InlinePresentation inlinePresentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            if (inlinePresentation == null) {
                throw new NullPointerException("inlinePresentation");
            }
            return setField(id, toField(value, null));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, RemoteViews presentation,
                InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setField(id, toField(value, null));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, Pattern filter,
                RemoteViews presentation, InlinePresentation inlinePresentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setField(id, toField(value, filter));
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, Pattern filter,
                RemoteViews presentation, InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setField(id, toField(value, filter));
        }

        /** @deprecated at API 33; folded into Presentations. */
        @Deprecated
        public Builder setInlinePresentation(InlinePresentation inlinePresentation) {
            throwIfDestroyed();
            if (inlinePresentation == null) {
                throw new NullPointerException("inlinePresentation");
            }
            return this;
        }

        @Deprecated
        public Builder setInlinePresentation(InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            throwIfDestroyed();
            if (inlinePresentation == null) {
                throw new NullPointerException("inlinePresentation");
            }
            return this;
        }

        /** AOSP requires at least one field (or an authentication step) before
         *  build(), and makes the builder single-use. */
        public Dataset build() {
            throwIfDestroyed();
            mDestroyed = true;
            if (mFields.isEmpty() && mHintFields.isEmpty() && mFieldForAllHints == null) {
                throw new IllegalStateException("at least one value must be set");
            }
            return new Dataset(this);
        }

        private static Field toField(AutofillValue value, Pattern filter) {
            Field.Builder b = new Field.Builder();
            if (value != null) b.setValue(value);
            if (filter != null) b.setFilter(filter);
            return b.build();
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
