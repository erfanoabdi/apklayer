package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import android.widget.RemoteViews;

/**
 * A set of edits applied to a CustomDescription's template when an associated
 * Validator passes: child transformations plus an optional template update.
 *
 * Pure value object with a builder. See the package note on AutofillService.
 *
 * Source: frameworks/base/core/java/android/service/autofill/BatchUpdates.java
 */
public final class BatchUpdates implements Parcelable {

    private final SparseArray<Transformation> mTransformations;
    private final RemoteViews mUpdates;

    private BatchUpdates(SparseArray<Transformation> transformations, RemoteViews updates) {
        mTransformations = transformations;
        mUpdates = updates;
    }

    /** @hide-shaped accessors: AOSP exposes these only to the platform. */
    SparseArray<Transformation> getTransformations() { return mTransformations; }

    RemoteViews getUpdates() { return mUpdates; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // AOSP writes the transformation ids + parcelables and then the
        // template. Transformation is a marker interface, so the concrete
        // subtypes are what actually cross; write them as generic parcelables.
        final int n = mTransformations == null ? 0 : mTransformations.size();
        dest.writeInt(n);
        for (int i = 0; i < n; i++) {
            dest.writeInt(mTransformations.keyAt(i));
            dest.writeParcelable((Parcelable) mTransformations.valueAt(i), flags);
        }
        dest.writeTypedObject(mUpdates, flags);
    }

    public static final Creator<BatchUpdates> CREATOR = new Creator<BatchUpdates>() {
        @Override public BatchUpdates createFromParcel(Parcel in) {
            final int n = in.readInt();
            SparseArray<Transformation> transformations =
                    new SparseArray<Transformation>(n);
            for (int i = 0; i < n; i++) {
                int id = in.readInt();
                transformations.put(id,
                        (Transformation) in.readParcelable(
                                BatchUpdates.class.getClassLoader()));
            }
            RemoteViews updates = in.readTypedObject(RemoteViews.CREATOR);
            return new BatchUpdates(transformations, updates);
        }
        @Override public BatchUpdates[] newArray(int size) { return new BatchUpdates[size]; }
    };

    public static class Builder {
        private SparseArray<Transformation> mTransformations;
        private RemoteViews mUpdates;
        private boolean mDestroyed;

        public Builder() { }

        public Builder transformChild(int id, Transformation transformation) {
            throwIfDestroyed();
            if (transformation == null) throw new NullPointerException("transformation");
            if (!(transformation instanceof Parcelable)) {
                // AOSP's own check: only its three built-in transformations
                // qualify, which is how it keeps the interface closed.
                throw new IllegalArgumentException("not a valid transformation: "
                        + transformation);
            }
            if (mTransformations == null) {
                mTransformations = new SparseArray<Transformation>();
            }
            mTransformations.put(id, transformation);
            return this;
        }

        public Builder updateTemplate(RemoteViews updates) {
            throwIfDestroyed();
            if (updates == null) throw new NullPointerException("updates");
            mUpdates = updates;
            return this;
        }

        /** AOSP requires at least one of the two, and makes the builder
         *  single-use. Both checks are observable, so both are kept. */
        public BatchUpdates build() {
            throwIfDestroyed();
            if (mUpdates == null && mTransformations == null) {
                throw new IllegalStateException("must call either updateTemplate() or "
                        + "transformChild() at least once");
            }
            mDestroyed = true;
            return new BatchUpdates(mTransformations, mUpdates);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
