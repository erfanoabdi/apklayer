// android.view.contentcapture.DataRemovalRequest -- an app asking the
// content-capture service to forget what it captured, for some locus ids or for
// everything. Source: frameworks/base
// core/java/android/view/contentcapture/DataRemovalRequest.java (android16-release).
package android.view.contentcapture;

public final class DataRemovalRequest implements android.os.Parcelable {
    /** The LocusId is a PREFIX, not a literal id. */
    public static final int FLAG_IS_PREFIX = 0x1;

    private final String mPackageName;
    private final boolean mForEverything;
    private final java.util.List<LocusIdRequest> mLocusIdRequests;

    private DataRemovalRequest(String packageName, boolean forEverything,
            java.util.List<LocusIdRequest> requests) {
        mPackageName = packageName;
        mForEverything = forEverything;
        mLocusIdRequests = requests;
    }

    public String getPackageName() { return mPackageName; }
    public boolean isForEverything() { return mForEverything; }
    /** AOSP: @NonNull, empty when isForEverything(). */
    public java.util.List<LocusIdRequest> getLocusIdRequests() { return mLocusIdRequests; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeBoolean(mForEverything);
        dest.writeInt(mLocusIdRequests.size());
        for (LocusIdRequest r : mLocusIdRequests) {
            dest.writeParcelable(r.getLocusId(), flags);
            dest.writeInt(r.getFlags());
        }
    }

    public static final android.os.Parcelable.Creator<DataRemovalRequest> CREATOR =
            new android.os.Parcelable.Creator<DataRemovalRequest>() {
        @Override public DataRemovalRequest createFromParcel(android.os.Parcel in) {
            final String pkg = in.readString();
            final boolean everything = in.readBoolean();
            final int n = in.readInt();
            java.util.ArrayList<LocusIdRequest> list = new java.util.ArrayList<LocusIdRequest>(n);
            ClassLoader cl = android.content.LocusId.class.getClassLoader();
            for (int i = 0; i < n; i++) {
                list.add(new LocusIdRequest((android.content.LocusId) in.readParcelable(cl),
                        in.readInt()));
            }
            return new DataRemovalRequest(pkg, everything,
                    java.util.Collections.unmodifiableList(list));
        }
        @Override public DataRemovalRequest[] newArray(int size) {
            return new DataRemovalRequest[size];
        }
    };

    /** AOSP DataRemovalRequest.LocusIdRequest: one locus id plus its flags. */
    public static final class LocusIdRequest {
        private final android.content.LocusId mLocusId;
        private final int mFlags;
        LocusIdRequest(android.content.LocusId locusId, int flags) {
            mLocusId = locusId;
            mFlags = flags;
        }
        public android.content.LocusId getLocusId() { return mLocusId; }
        public int getFlags() { return mFlags; }
    }

    public static final class Builder {
        private boolean mForEverything;
        private java.util.ArrayList<LocusIdRequest> mLocusIds;
        private java.util.ArrayList<Integer> mFlags;

        public Builder() {}

        /** AOSP: forEverything() and addLocusId() are mutually exclusive, and
         *  AOSP raises IllegalStateException when they are mixed. */
        public Builder forEverything() {
            throwIfLocusIdsAdded();
            mForEverything = true;
            return this;
        }
        public Builder addLocusId(android.content.LocusId locusId, int flags) {
            if (mForEverything) {
                throw new IllegalStateException("Already is for everything");
            }
            java.util.Objects.requireNonNull(locusId);
            if (mLocusIds == null) {
                mLocusIds = new java.util.ArrayList<LocusIdRequest>();
            }
            mLocusIds.add(new LocusIdRequest(locusId, flags));
            return this;
        }
        private void throwIfLocusIdsAdded() {
            if (mLocusIds != null && !mLocusIds.isEmpty()) {
                throw new IllegalStateException("Already added " + mLocusIds.size() + " ids");
            }
        }
        public DataRemovalRequest build() {
            java.util.List<LocusIdRequest> list = mLocusIds == null
                    ? java.util.Collections.<LocusIdRequest>emptyList()
                    : java.util.Collections.unmodifiableList(mLocusIds);
            // AOSP stamps the CALLING package. There is exactly ONE app in this
            // process, so the app's own package name IS the calling package;
            // Resources knows it (Boot seeds it from the manifest).
            String pkg = "";
            try {
                android.content.res.Resources res = android.content.res.Resources.getSystem();
                if (res != null) pkg = res.getResourcePackageName(0x7f000000);
            } catch (Throwable ignored) {}
            return new DataRemovalRequest(pkg == null ? "" : pkg, mForEverything, list);
        }
    }
}
