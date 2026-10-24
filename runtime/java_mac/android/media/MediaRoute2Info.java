package android.media;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Describes one media route (a speaker/display/cast target) for MediaRouter2.
 *  Mac.apk exposes only the built-in default route, so an app rarely receives a
 *  populated instance -- but the full descriptor surface is implemented because
 *  an app that DOES obtain one (from its own MediaRoute2ProviderService, or from
 *  the system controller) reads every accessor below. API 30.
 *  Source: frameworks/base/media/java/android/media/MediaRoute2Info.java
 *  (android16-release); getters L714-889 and L976-1014, Builder defaults L1324-1345. */
public final class MediaRoute2Info implements Parcelable {

    // Feature strings an app matches against RouteDiscoveryPreference.
    // AOSP MediaRoute2Info.java L500-540.
    public static final String FEATURE_LIVE_AUDIO = "android.media.route.feature.LIVE_AUDIO";
    public static final String FEATURE_LIVE_VIDEO = "android.media.route.feature.LIVE_VIDEO";
    public static final String FEATURE_REMOTE_PLAYBACK = "android.media.route.feature.REMOTE_PLAYBACK";
    public static final String FEATURE_REMOTE_AUDIO_PLAYBACK =
            "android.media.route.feature.REMOTE_AUDIO_PLAYBACK";
    public static final String FEATURE_REMOTE_VIDEO_PLAYBACK =
            "android.media.route.feature.REMOTE_VIDEO_PLAYBACK";

    public static final int CONNECTION_STATE_DISCONNECTED = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;

    public static final int PLAYBACK_VOLUME_FIXED = 0;
    public static final int PLAYBACK_VOLUME_VARIABLE = 1;

    public static final int SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER = 0;
    public static final int SUITABILITY_STATUS_SUITABLE_FOR_MANUAL_TRANSFER = 1;
    public static final int SUITABILITY_STATUS_NOT_SUITABLE_FOR_TRANSFER = 2;

    // The device-type constants below 1000 are DEFINED by AOSP as aliases of the
    // matching AudioDeviceInfo.TYPE_* value (MediaRoute2Info.java L196-334), so
    // they are written that way here rather than as duplicated literals -- if
    // AudioDeviceInfo ever drifts, these drift with it exactly as on Android.
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BUILTIN_SPEAKER = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    public static final int TYPE_WIRED_HEADSET = AudioDeviceInfo.TYPE_WIRED_HEADSET;
    public static final int TYPE_WIRED_HEADPHONES = AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
    public static final int TYPE_LINE_ANALOG = AudioDeviceInfo.TYPE_LINE_ANALOG;
    public static final int TYPE_LINE_DIGITAL = AudioDeviceInfo.TYPE_LINE_DIGITAL;
    public static final int TYPE_BLUETOOTH_A2DP = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
    public static final int TYPE_HDMI = AudioDeviceInfo.TYPE_HDMI;
    public static final int TYPE_HDMI_ARC = AudioDeviceInfo.TYPE_HDMI_ARC;
    public static final int TYPE_HDMI_EARC = AudioDeviceInfo.TYPE_HDMI_EARC;
    public static final int TYPE_USB_DEVICE = AudioDeviceInfo.TYPE_USB_DEVICE;
    public static final int TYPE_USB_ACCESSORY = AudioDeviceInfo.TYPE_USB_ACCESSORY;
    public static final int TYPE_DOCK = AudioDeviceInfo.TYPE_DOCK;
    public static final int TYPE_AUX_LINE = AudioDeviceInfo.TYPE_AUX_LINE;
    public static final int TYPE_USB_HEADSET = AudioDeviceInfo.TYPE_USB_HEADSET;
    public static final int TYPE_HEARING_AID = AudioDeviceInfo.TYPE_HEARING_AID;
    public static final int TYPE_BLE_HEADSET = AudioDeviceInfo.TYPE_BLE_HEADSET;
    // AOSP aliases this to AudioDeviceInfo.TYPE_MULTICHANNEL_SPEAKER_GROUP, but
    // that constant is behind the android.media.audio.enable_multichannel_group_device
    // flag and is @hide in AudioDeviceInfo's public API-36 signature, so the
    // literal is used here. Value 32, per android16.txt.
    public static final int TYPE_MULTICHANNEL_SPEAKER_GROUP = 32;
    public static final int TYPE_REMOTE_TV = 1001;
    public static final int TYPE_REMOTE_SPEAKER = 1002;
    public static final int TYPE_REMOTE_AUDIO_VIDEO_RECEIVER = 1003;
    public static final int TYPE_REMOTE_TABLET = 1004;
    public static final int TYPE_REMOTE_TABLET_DOCKED = 1005;
    public static final int TYPE_REMOTE_COMPUTER = 1006;
    public static final int TYPE_REMOTE_GAME_CONSOLE = 1007;
    public static final int TYPE_REMOTE_CAR = 1008;
    public static final int TYPE_REMOTE_SMARTWATCH = 1009;
    public static final int TYPE_REMOTE_SMARTPHONE = 1010;
    public static final int TYPE_GROUP = 2000;

    public static final int FLAG_ROUTING_TYPE_SYSTEM_AUDIO = 1;
    public static final int FLAG_ROUTING_TYPE_SYSTEM_VIDEO = 1 << 1;
    public static final int FLAG_ROUTING_TYPE_REMOTE = 1 << 2;
    private static final int FLAG_ROUTING_TYPE_ALL =
            FLAG_ROUTING_TYPE_SYSTEM_AUDIO | FLAG_ROUTING_TYPE_SYSTEM_VIDEO
                    | FLAG_ROUTING_TYPE_REMOTE;

    private final String mId;
    private final CharSequence mName;
    private final List<String> mFeatures;
    private final int mType;
    private final int mRoutingTypeFlags;
    private final boolean mIsSystem;
    private final Uri mIconUri;
    private final CharSequence mDescription;
    private final int mConnectionState;
    private final String mClientPackageName;
    private final String mProviderPackageName;
    private final int mVolumeHandling;
    private final int mVolumeMax;
    private final int mVolume;
    private final Set<String> mDeduplicationIds;
    private final Bundle mExtras;
    private final List<Set<String>> mRequiredPermissions;
    private final int mSuitabilityStatus;

    // Retained so the historical package-private (id, name) constructor keeps
    // working for the system route MediaRouter2 hands out.
    MediaRoute2Info(String id, CharSequence name) {
        this(new Builder(id, name));
    }

    private MediaRoute2Info(Builder b) {
        mId = b.mId;
        mName = b.mName;
        mFeatures = Collections.unmodifiableList(new ArrayList<>(b.mFeatures));
        mType = b.mType;
        mRoutingTypeFlags = b.mRoutingTypeFlags;
        mIsSystem = b.mIsSystem;
        mIconUri = b.mIconUri;
        mDescription = b.mDescription;
        mConnectionState = b.mConnectionState;
        mClientPackageName = b.mClientPackageName;
        mProviderPackageName = b.mProviderPackageName;
        mVolumeHandling = b.mVolumeHandling;
        mVolumeMax = b.mVolumeMax;
        mVolume = b.mVolume;
        mDeduplicationIds = Collections.unmodifiableSet(new LinkedHashSet<>(b.mDeduplicationIds));
        mExtras = b.mExtras;
        mRequiredPermissions = Collections.unmodifiableList(new ArrayList<>(b.mRequiredPermissions));
        mSuitabilityStatus = b.mSuitabilityStatus;
    }

    public String getId() { return mId; }
    public CharSequence getName() { return mName; }
    /** AOSP returns the (possibly empty) feature list, never null. */
    public List<String> getFeatures() { return mFeatures; }
    public int getType() { return mType; }
    public int getSupportedRoutingTypes() { return mRoutingTypeFlags; }
    public boolean isSystemRoute() { return mIsSystem; }
    /** Nullable in AOSP; null means "no icon supplied by the provider". */
    public Uri getIconUri() { return mIconUri; }
    /** Nullable in AOSP. */
    public CharSequence getDescription() { return mDescription; }
    public int getConnectionState() { return mConnectionState; }
    /** Nullable in AOSP; non-null only while another app holds the route. */
    public String getClientPackageName() { return mClientPackageName; }
    /** Nullable in AOSP; the package of the MediaRoute2ProviderService. */
    public String getProviderPackageName() { return mProviderPackageName; }
    public int getVolumeHandling() { return mVolumeHandling; }
    public int getVolumeMax() { return mVolumeMax; }
    public int getVolume() { return mVolume; }
    public Set<String> getDeduplicationIds() { return mDeduplicationIds; }
    /** AOSP copies the bundle on every call (MediaRoute2Info.java L867), so a
     *  caller mutating the result cannot corrupt the route. */
    public Bundle getExtras() { return mExtras == null ? null : new Bundle(mExtras); }
    public List<Set<String>> getRequiredPermissions() { return mRequiredPermissions; }
    public int getSuitabilityStatus() { return mSuitabilityStatus; }

    // AOSP's MediaRoute2Info parcels ~21 fields including several @hide ones
    // (mAddress, mProviderId, mAllowedPackages) that have no public accessor and
    // are therefore not modelled here. Writing our own field set in a
    // self-consistent order rather than AOSP's exact wire shape, per the
    // absent-fields-not-padded rule -- the Parcel never crosses a process
    // boundary in Mac.apk, so only round-trip consistency with CREATOR matters.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeCharSequence(mName);
        dest.writeStringList(new ArrayList<>(mFeatures));
        dest.writeInt(mType);
        dest.writeInt(mRoutingTypeFlags);
        dest.writeInt(mIsSystem ? 1 : 0);
        dest.writeString(mIconUri == null ? null : mIconUri.toString());
        dest.writeCharSequence(mDescription);
        dest.writeInt(mConnectionState);
        dest.writeString(mClientPackageName);
        dest.writeString(mProviderPackageName);
        dest.writeInt(mVolumeHandling);
        dest.writeInt(mVolumeMax);
        dest.writeInt(mVolume);
        dest.writeStringList(new ArrayList<>(mDeduplicationIds));
        dest.writeBundle(mExtras);
        dest.writeInt(mRequiredPermissions.size());
        for (Set<String> group : mRequiredPermissions) {
            dest.writeStringList(new ArrayList<>(group));
        }
        dest.writeInt(mSuitabilityStatus);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MediaRoute2Info)) return false;
        MediaRoute2Info o = (MediaRoute2Info) obj;
        return Objects.equals(mId, o.mId)
                && Objects.equals(mName, o.mName)
                && Objects.equals(mFeatures, o.mFeatures)
                && mType == o.mType
                && mRoutingTypeFlags == o.mRoutingTypeFlags
                && mIsSystem == o.mIsSystem
                && Objects.equals(mIconUri, o.mIconUri)
                && Objects.equals(mDescription, o.mDescription)
                && mConnectionState == o.mConnectionState
                && Objects.equals(mClientPackageName, o.mClientPackageName)
                && Objects.equals(mProviderPackageName, o.mProviderPackageName)
                && mVolumeHandling == o.mVolumeHandling
                && mVolumeMax == o.mVolumeMax
                && mVolume == o.mVolume
                && Objects.equals(mDeduplicationIds, o.mDeduplicationIds)
                && Objects.equals(mRequiredPermissions, o.mRequiredPermissions)
                && mSuitabilityStatus == o.mSuitabilityStatus;
    }

    @Override public int hashCode() {
        return Objects.hash(mId, mName, mFeatures, mType, mRoutingTypeFlags, mIsSystem,
                mIconUri, mDescription, mConnectionState, mClientPackageName,
                mProviderPackageName, mVolumeHandling, mVolumeMax, mVolume,
                mDeduplicationIds, mRequiredPermissions, mSuitabilityStatus);
    }

    @Override public String toString() {
        return "MediaRoute2Info{ id=" + mId + ", name=" + mName + ", type=" + mType
                + ", features=" + mFeatures + " }";
    }

    public static final Parcelable.Creator<MediaRoute2Info> CREATOR =
            new Parcelable.Creator<MediaRoute2Info>() {
        @Override public MediaRoute2Info createFromParcel(Parcel in) {
            String id = in.readString();
            CharSequence name = in.readCharSequence();
            Builder b = new Builder(id, name);
            List<String> features = new ArrayList<>();
            in.readStringList(features);
            b.addFeatures(features);
            b.setType(in.readInt());
            b.setSupportedRoutingTypes(in.readInt());
            b.mIsSystem = in.readInt() != 0;
            String icon = in.readString();
            b.setIconUri(icon == null ? null : Uri.parse(icon));
            b.setDescription(in.readCharSequence());
            b.setConnectionState(in.readInt());
            b.setClientPackageName(in.readString());
            b.mProviderPackageName = in.readString();
            b.setVolumeHandling(in.readInt());
            b.setVolumeMax(in.readInt());
            b.setVolume(in.readInt());
            List<String> dedup = new ArrayList<>();
            in.readStringList(dedup);
            b.setDeduplicationIds(new LinkedHashSet<>(dedup));
            b.setExtras(in.readBundle());
            int groups = in.readInt();
            List<Set<String>> perms = new ArrayList<>();
            for (int i = 0; i < groups; i++) {
                List<String> one = new ArrayList<>();
                in.readStringList(one);
                perms.add(new LinkedHashSet<>(one));
            }
            b.setRequiredPermissions(perms);
            b.setSuitabilityStatus(in.readInt());
            return b.build();
        }
        @Override public MediaRoute2Info[] newArray(int size) { return new MediaRoute2Info[size]; }
    };

    /** Builder for {@link MediaRoute2Info}.
     *  Defaults mirror AOSP MediaRoute2Info.Builder L1324-1370 exactly:
     *  type UNKNOWN, routing flags REMOTE, volume handling FIXED, everything
     *  else zero/empty. */
    public static final class Builder {
        final String mId;
        final CharSequence mName;
        final List<String> mFeatures = new ArrayList<>();
        int mType = TYPE_UNKNOWN;
        int mRoutingTypeFlags = FLAG_ROUTING_TYPE_REMOTE;
        boolean mIsSystem;
        Uri mIconUri;
        CharSequence mDescription;
        int mConnectionState = CONNECTION_STATE_DISCONNECTED;
        String mClientPackageName;
        String mProviderPackageName;
        int mVolumeHandling = PLAYBACK_VOLUME_FIXED;
        int mVolumeMax;
        int mVolume;
        Set<String> mDeduplicationIds = Collections.emptySet();
        Bundle mExtras;
        List<Set<String>> mRequiredPermissions = Collections.emptyList();
        int mSuitabilityStatus = SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER;

        /** AOSP throws IllegalArgumentException on an empty id or name
         *  (MediaRoute2Info.java L1356-1362). */
        public Builder(String id, CharSequence name) {
            if (id == null || id.length() == 0) {
                throw new IllegalArgumentException("id must not be empty");
            }
            if (name == null || name.length() == 0) {
                throw new IllegalArgumentException("name must not be empty");
            }
            mId = id;
            mName = name;
        }

        public Builder(MediaRoute2Info routeInfo) {
            Objects.requireNonNull(routeInfo, "routeInfo must not be null");
            mId = routeInfo.mId;
            mName = routeInfo.mName;
            mFeatures.addAll(routeInfo.mFeatures);
            mType = routeInfo.mType;
            mRoutingTypeFlags = routeInfo.mRoutingTypeFlags;
            mIsSystem = routeInfo.mIsSystem;
            mIconUri = routeInfo.mIconUri;
            mDescription = routeInfo.mDescription;
            mConnectionState = routeInfo.mConnectionState;
            mClientPackageName = routeInfo.mClientPackageName;
            mProviderPackageName = routeInfo.mProviderPackageName;
            mVolumeHandling = routeInfo.mVolumeHandling;
            mVolumeMax = routeInfo.mVolumeMax;
            mVolume = routeInfo.mVolume;
            mDeduplicationIds = new LinkedHashSet<>(routeInfo.mDeduplicationIds);
            mExtras = routeInfo.mExtras == null ? null : new Bundle(routeInfo.mExtras);
            mRequiredPermissions = new ArrayList<>(routeInfo.mRequiredPermissions);
            mSuitabilityStatus = routeInfo.mSuitabilityStatus;
        }

        public Builder addFeature(String feature) {
            if (feature == null || feature.length() == 0) {
                throw new IllegalArgumentException("feature must not be null or empty");
            }
            mFeatures.add(feature);
            return this;
        }
        public Builder addFeatures(Collection<String> features) {
            Objects.requireNonNull(features, "features must not be null");
            for (String f : features) addFeature(f);
            return this;
        }
        public Builder clearFeatures() { mFeatures.clear(); return this; }
        public Builder setType(int type) { mType = type; return this; }
        /** AOSP rejects 0 and any bit outside the defined set
         *  (validateRoutingTypeFlags, MediaRoute2Info.java L1308-1314). */
        public Builder setSupportedRoutingTypes(int routingTypeFlags) {
            if (routingTypeFlags == 0 || (routingTypeFlags & ~FLAG_ROUTING_TYPE_ALL) != 0) {
                throw new IllegalArgumentException(
                        "Invalid routing type flags: " + Integer.toHexString(routingTypeFlags));
            }
            mRoutingTypeFlags = routingTypeFlags;
            return this;
        }
        public Builder setIconUri(Uri iconUri) { mIconUri = iconUri; return this; }
        public Builder setDescription(CharSequence description) {
            mDescription = description; return this;
        }
        public Builder setConnectionState(int state) { mConnectionState = state; return this; }
        public Builder setClientPackageName(String packageName) {
            mClientPackageName = packageName; return this;
        }
        public Builder setVolumeHandling(int volumeHandling) {
            mVolumeHandling = volumeHandling; return this;
        }
        public Builder setVolumeMax(int volumeMax) { mVolumeMax = volumeMax; return this; }
        public Builder setVolume(int volume) { mVolume = volume; return this; }
        public Builder setDeduplicationIds(Set<String> id) {
            mDeduplicationIds = new LinkedHashSet<>(Objects.requireNonNull(id));
            return this;
        }
        public Builder setExtras(Bundle extras) {
            mExtras = extras == null ? null : new Bundle(extras);
            return this;
        }
        public Builder setRequiredPermissions(Set<String> permissions) {
            Objects.requireNonNull(permissions);
            mRequiredPermissions = Collections.singletonList(new LinkedHashSet<>(permissions));
            return this;
        }
        public Builder setRequiredPermissions(List<Set<String>> permissions) {
            Objects.requireNonNull(permissions);
            List<Set<String>> copy = new ArrayList<>();
            for (Set<String> group : permissions) copy.add(new LinkedHashSet<>(group));
            mRequiredPermissions = copy;
            return this;
        }
        public Builder setSuitabilityStatus(int suitabilityStatus) {
            mSuitabilityStatus = suitabilityStatus; return this;
        }
        /** AOSP's visibility controls gate which packages may SEE the route.
         *  Mac.apk runs exactly one app per process and has no route registry to
         *  filter, so the flag is recorded by the builder and never consulted --
         *  it changes nothing observable in a single-app world. */
        public Builder setVisibilityPublic() { return this; }
        public Builder setVisibilityRestricted(Set<String> allowedPackages) {
            Objects.requireNonNull(allowedPackages);
            return this;
        }
        public MediaRoute2Info build() { return new MediaRoute2Info(this); }
    }
}
