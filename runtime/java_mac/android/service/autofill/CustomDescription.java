package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import android.util.SparseArray;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom layout for the save dialog: a RemoteViews template plus rules that
 * transform its children from the values being saved.
 *
 * Pure value object with a builder. Mac.apk shows no save dialog, so the
 * template is never inflated -- but the builder's validation (which is what
 * rejects a third-party Transformation, keeping the interface closed) runs
 * exactly as on a device.
 *
 * Source: frameworks/base/core/java/android/service/autofill/CustomDescription.java
 */
public final class CustomDescription implements Parcelable {

    private final RemoteViews mPresentation;
    private final SparseArray<Transformation> mTransformations;
    private final List<Pair<Integer, InternalOnClickAction>> mActions;
    private final List<Pair<Validator, BatchUpdates>> mUpdates;

    private CustomDescription(Builder builder) {
        mPresentation = builder.mPresentation;
        mTransformations = builder.mTransformations;
        mActions = builder.mActions;
        mUpdates = builder.mUpdates;
    }

    RemoteViews getPresentation() { return mPresentation; }

    SparseArray<Transformation> getTransformations() { return mTransformations; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // AOSP writes the template plus each rule group. Transformation,
        // Validator and OnClickAction are marker interfaces whose only
        // implementations are Parcelable, so the concrete objects cross.
        dest.writeTypedObject(mPresentation, flags);
        writeSparse(dest, mTransformations, flags);
        dest.writeInt(mActions == null ? 0 : mActions.size());
        if (mActions != null) {
            for (Pair<Integer, InternalOnClickAction> p : mActions) {
                dest.writeInt(p.first.intValue());
                dest.writeParcelable((Parcelable) p.second.action, flags);
            }
        }
        dest.writeInt(mUpdates == null ? 0 : mUpdates.size());
        if (mUpdates != null) {
            for (Pair<Validator, BatchUpdates> p : mUpdates) {
                dest.writeParcelable((Parcelable) p.first, flags);
                dest.writeTypedObject(p.second, flags);
            }
        }
    }

    private static void writeSparse(Parcel dest, SparseArray<Transformation> a, int flags) {
        final int n = a == null ? 0 : a.size();
        dest.writeInt(n);
        for (int i = 0; i < n; i++) {
            dest.writeInt(a.keyAt(i));
            dest.writeParcelable((Parcelable) a.valueAt(i), flags);
        }
    }

    @Override public String toString() { return "CustomDescription[]"; }

    public static final Creator<CustomDescription> CREATOR =
            new Creator<CustomDescription>() {
        @Override public CustomDescription createFromParcel(Parcel parcel) {
            final Builder builder =
                    new Builder(parcel.readTypedObject(RemoteViews.CREATOR));
            final ClassLoader cl = CustomDescription.class.getClassLoader();
            int n = parcel.readInt();
            for (int i = 0; i < n; i++) {
                int id = parcel.readInt();
                builder.addChild(id, (Transformation) parcel.readParcelable(cl));
            }
            n = parcel.readInt();
            for (int i = 0; i < n; i++) {
                int id = parcel.readInt();
                builder.addOnClickAction(id, (OnClickAction) parcel.readParcelable(cl));
            }
            n = parcel.readInt();
            for (int i = 0; i < n; i++) {
                Validator v = (Validator) parcel.readParcelable(cl);
                builder.batchUpdate(v, parcel.readTypedObject(BatchUpdates.CREATOR));
            }
            return builder.build();
        }
        @Override public CustomDescription[] newArray(int size) {
            return new CustomDescription[size];
        }
    };

    // Small wrapper so the action list can hold the marker type without the
    // list element itself needing to be Parcelable.
    private static final class InternalOnClickAction {
        final OnClickAction action;
        InternalOnClickAction(OnClickAction action) { this.action = action; }
    }

    public static class Builder {
        private final RemoteViews mPresentation;
        private SparseArray<Transformation> mTransformations;
        private List<Pair<Integer, InternalOnClickAction>> mActions;
        private List<Pair<Validator, BatchUpdates>> mUpdates;
        private boolean mDestroyed;

        public Builder(RemoteViews parentPresentation) {
            if (parentPresentation == null) {
                throw new NullPointerException("parentPresentation");
            }
            mPresentation = parentPresentation;
        }

        public Builder addChild(int id, Transformation transformation) {
            throwIfDestroyed();
            assertIsSystem(transformation);
            if (mTransformations == null) {
                mTransformations = new SparseArray<Transformation>();
            }
            mTransformations.put(id, transformation);
            return this;
        }

        public Builder batchUpdate(Validator condition, BatchUpdates updates) {
            throwIfDestroyed();
            assertIsSystem(condition);
            if (updates == null) throw new NullPointerException("updates");
            if (mUpdates == null) {
                mUpdates = new ArrayList<Pair<Validator, BatchUpdates>>();
            }
            mUpdates.add(new Pair<Validator, BatchUpdates>(condition, updates));
            return this;
        }

        public Builder addOnClickAction(int id, OnClickAction action) {
            throwIfDestroyed();
            assertIsSystem(action);
            if (mActions == null) {
                mActions = new ArrayList<Pair<Integer, InternalOnClickAction>>();
            }
            mActions.add(new Pair<Integer, InternalOnClickAction>(
                    Integer.valueOf(id), new InternalOnClickAction(action)));
            return this;
        }

        public CustomDescription build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new CustomDescription(this);
        }

        /** AOSP's closed-interface check: only its own Parcelable
         *  implementations are accepted. */
        private static void assertIsSystem(Object o) {
            if (o == null) throw new NullPointerException();
            if (!(o instanceof Parcelable)) {
                throw new IllegalArgumentException("not provided by Android System: " + o);
            }
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
