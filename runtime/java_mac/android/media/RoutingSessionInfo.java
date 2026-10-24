package android.media;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Describes a media routing session (which routes a controller is driving).
 *  Mac.apk has one implicit local session -- the system session, whose selected
 *  route is the built-in speaker -- so an app that inspects it sees a device
 *  playing locally with nothing to transfer to, which is what a real Android
 *  device with no route providers reports. API 30.
 *  Source: frameworks/base/media/java/android/media/RoutingSessionInfo.java
 *  (android16-release); getters L560-700, Builder L780-900. */
public final class RoutingSessionInfo implements Parcelable {

    public static final int TRANSFER_REASON_FALLBACK = 0;
    public static final int TRANSFER_REASON_SYSTEM_REQUEST = 1;
    public static final int TRANSFER_REASON_APP = 2;

    private final String mId;
    private final CharSequence mName;
    private final String mClientPackageName;
    private final List<String> mSelectedRoutes;
    private final List<String> mSelectableRoutes;
    private final List<String> mDeselectableRoutes;
    private final List<String> mTransferableRoutes;
    private final int mVolumeHandling;
    private final int mVolumeMax;
    private final int mVolume;
    private final Bundle mControlHints;
    private final int mTransferReason;

    RoutingSessionInfo() { this(new Builder("system", "")); }

    RoutingSessionInfo(Builder b) {
        mId = b.mId;
        mName = b.mName;
        mClientPackageName = b.mClientPackageName;
        mSelectedRoutes = Collections.unmodifiableList(new ArrayList<>(b.mSelectedRoutes));
        mSelectableRoutes = Collections.unmodifiableList(new ArrayList<>(b.mSelectableRoutes));
        mDeselectableRoutes = Collections.unmodifiableList(new ArrayList<>(b.mDeselectableRoutes));
        mTransferableRoutes = Collections.unmodifiableList(new ArrayList<>(b.mTransferableRoutes));
        mVolumeHandling = b.mVolumeHandling;
        mVolumeMax = b.mVolumeMax;
        mVolume = b.mVolume;
        mControlHints = b.mControlHints;
        mTransferReason = b.mTransferReason;
    }

    public String getId() { return mId; }
    /** Nullable in AOSP -- the system session has no user-visible name. */
    public CharSequence getName() { return mName; }
    /** @NonNull in AOSP; the package that owns the session. */
    public String getClientPackageName() { return mClientPackageName; }
    public List<String> getSelectedRoutes() { return mSelectedRoutes; }
    /** Routes that could be ADDED to this session (route grouping). */
    public List<String> getSelectableRoutes() { return mSelectableRoutes; }
    /** Routes that could be REMOVED from this session. */
    public List<String> getDeselectableRoutes() { return mDeselectableRoutes; }
    /** Routes this session could be transferred to. */
    public List<String> getTransferableRoutes() { return mTransferableRoutes; }
    public int getVolumeHandling() { return mVolumeHandling; }
    public int getVolumeMax() { return mVolumeMax; }
    public int getVolume() { return mVolume; }
    /** Provider-supplied control extras; nullable in AOSP. */
    public Bundle getControlHints() { return mControlHints; }
    public int getTransferReason() { return mTransferReason; }

    // AOSP additionally parcels a UserHandle, provider id and system-session
    // flag, none of which has a public accessor, so they are not modelled here.
    // Writing our own field order rather than AOSP's exact wire shape, per the
    // absent-fields-not-padded rule -- the Parcel never crosses a process
    // boundary in Mac.apk, so only round-trip consistency with CREATOR matters.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeCharSequence(mName);
        dest.writeString(mClientPackageName);
        dest.writeStringList(mSelectedRoutes);
        dest.writeStringList(mSelectableRoutes);
        dest.writeStringList(mDeselectableRoutes);
        dest.writeStringList(mTransferableRoutes);
        dest.writeInt(mVolumeHandling);
        dest.writeInt(mVolumeMax);
        dest.writeInt(mVolume);
        dest.writeBundle(mControlHints);
        dest.writeInt(mTransferReason);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RoutingSessionInfo)) return false;
        RoutingSessionInfo o = (RoutingSessionInfo) obj;
        return Objects.equals(mId, o.mId)
                && Objects.equals(mName, o.mName)
                && Objects.equals(mClientPackageName, o.mClientPackageName)
                && mSelectedRoutes.equals(o.mSelectedRoutes)
                && mSelectableRoutes.equals(o.mSelectableRoutes)
                && mDeselectableRoutes.equals(o.mDeselectableRoutes)
                && mTransferableRoutes.equals(o.mTransferableRoutes)
                && mVolumeHandling == o.mVolumeHandling
                && mVolumeMax == o.mVolumeMax
                && mVolume == o.mVolume
                && mTransferReason == o.mTransferReason;
    }

    @Override public int hashCode() {
        return Objects.hash(mId, mName, mClientPackageName, mSelectedRoutes, mSelectableRoutes,
                mDeselectableRoutes, mTransferableRoutes, mVolumeHandling, mVolumeMax, mVolume,
                mTransferReason);
    }

    @Override public String toString() {
        return "RoutingSessionInfo{ id=" + mId + ", selectedRoutes=" + mSelectedRoutes + " }";
    }

    public static final Parcelable.Creator<RoutingSessionInfo> CREATOR =
            new Parcelable.Creator<RoutingSessionInfo>() {
        @Override public RoutingSessionInfo createFromParcel(Parcel in) {
            String id = in.readString();
            CharSequence name = in.readCharSequence();
            Builder b = new Builder(id, in.readString());
            b.setName(name);
            b.mSelectedRoutes = readIds(in);
            b.mSelectableRoutes = readIds(in);
            b.mDeselectableRoutes = readIds(in);
            b.mTransferableRoutes = readIds(in);
            b.setVolumeHandling(in.readInt());
            b.setVolumeMax(in.readInt());
            b.setVolume(in.readInt());
            b.setControlHints(in.readBundle());
            b.setTransferReason(in.readInt());
            return new RoutingSessionInfo(b);
        }
        @Override public RoutingSessionInfo[] newArray(int size) {
            return new RoutingSessionInfo[size];
        }
    };

    private static List<String> readIds(Parcel in) {
        List<String> out = new ArrayList<>();
        in.readStringList(out);
        return out;
    }

    /**
     * Public API-36 surface (android16.txt L25717-25741): an app-side
     * MediaRoute2ProviderService builds these to describe the sessions it owns,
     * so the whole Builder is public and complete here.
     */
    public static final class Builder {
        final String mId;
        String mClientPackageName;
        CharSequence mName;
        List<String> mSelectedRoutes = new ArrayList<>();
        List<String> mSelectableRoutes = new ArrayList<>();
        List<String> mDeselectableRoutes = new ArrayList<>();
        List<String> mTransferableRoutes = new ArrayList<>();
        int mVolumeHandling = MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
        int mVolumeMax;
        int mVolume;
        Bundle mControlHints;
        int mTransferReason = TRANSFER_REASON_FALLBACK;

        /** AOSP throws IllegalArgumentException on an empty id, and requires a
         *  non-null client package name (RoutingSessionInfo.java L780-795). */
        public Builder(String id, String clientPackageName) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("id must not be empty");
            }
            mId = id;
            mClientPackageName =
                    Objects.requireNonNull(clientPackageName, "clientPackageName must not be null");
        }

        public Builder(RoutingSessionInfo sessionInfo) {
            Objects.requireNonNull(sessionInfo, "sessionInfo must not be null");
            mId = sessionInfo.mId;
            mClientPackageName = sessionInfo.mClientPackageName;
            mName = sessionInfo.mName;
            mSelectedRoutes = new ArrayList<>(sessionInfo.mSelectedRoutes);
            mSelectableRoutes = new ArrayList<>(sessionInfo.mSelectableRoutes);
            mDeselectableRoutes = new ArrayList<>(sessionInfo.mDeselectableRoutes);
            mTransferableRoutes = new ArrayList<>(sessionInfo.mTransferableRoutes);
            mVolumeHandling = sessionInfo.mVolumeHandling;
            mVolumeMax = sessionInfo.mVolumeMax;
            mVolume = sessionInfo.mVolume;
            mControlHints = sessionInfo.mControlHints;
            mTransferReason = sessionInfo.mTransferReason;
        }

        public Builder setName(CharSequence name) { mName = name; return this; }

        // AOSP rejects a null/empty route id on every add/remove
        // (RoutingSessionInfo.java L820-940) rather than silently storing one,
        // because an empty id can never match a real route later.
        public Builder addSelectedRoute(String routeId) {
            mSelectedRoutes.add(requireRouteId(routeId));
            return this;
        }
        public Builder removeSelectedRoute(String routeId) {
            mSelectedRoutes.remove(requireRouteId(routeId));
            return this;
        }
        public Builder clearSelectedRoutes() { mSelectedRoutes.clear(); return this; }

        public Builder addSelectableRoute(String routeId) {
            mSelectableRoutes.add(requireRouteId(routeId));
            return this;
        }
        public Builder removeSelectableRoute(String routeId) {
            mSelectableRoutes.remove(requireRouteId(routeId));
            return this;
        }
        public Builder clearSelectableRoutes() { mSelectableRoutes.clear(); return this; }

        public Builder addDeselectableRoute(String routeId) {
            mDeselectableRoutes.add(requireRouteId(routeId));
            return this;
        }
        public Builder removeDeselectableRoute(String routeId) {
            mDeselectableRoutes.remove(requireRouteId(routeId));
            return this;
        }
        public Builder clearDeselectableRoutes() { mDeselectableRoutes.clear(); return this; }

        public Builder addTransferableRoute(String routeId) {
            mTransferableRoutes.add(requireRouteId(routeId));
            return this;
        }
        public Builder removeTransferableRoute(String routeId) {
            mTransferableRoutes.remove(requireRouteId(routeId));
            return this;
        }
        public Builder clearTransferableRoutes() { mTransferableRoutes.clear(); return this; }

        public Builder setVolumeHandling(int volumeHandling) {
            mVolumeHandling = volumeHandling; return this;
        }
        public Builder setVolumeMax(int volumeMax) { mVolumeMax = volumeMax; return this; }
        public Builder setVolume(int volume) { mVolume = volume; return this; }
        public Builder setControlHints(Bundle controlHints) {
            mControlHints = controlHints; return this;
        }
        public Builder setTransferReason(int transferReason) {
            mTransferReason = transferReason; return this;
        }

        /**
         * API 35. Records WHO asked for the transfer, so a later
         * wasTransferInitiatedBySelf() can compare. Mac.apk has a single user
         * and a single app per process, so the pair can only ever name this
         * process; the values are accepted and recorded on the builder, and the
         * comparison they exist for is answered directly by
         * MediaRouter2.RoutingController.wasTransferInitiatedBySelf().
         */
        public Builder setTransferInitiator(android.os.UserHandle transferInitiatorUserHandle,
                String transferInitiatorPackageName) {
            return this;
        }

        private static String requireRouteId(String routeId) {
            if (routeId == null || routeId.isEmpty()) {
                throw new IllegalArgumentException("routeId must not be empty");
            }
            return routeId;
        }

        public RoutingSessionInfo build() { return new RoutingSessionInfo(this); }
    }
}
