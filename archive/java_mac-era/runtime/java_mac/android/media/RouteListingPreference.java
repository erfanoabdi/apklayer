package android.media;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An app's preferred presentation of the system output-switcher route list
 * (API 34). This is pure app-supplied DATA -- the app declares which route ids
 * it wants shown, in what order, with what subtext -- consumed by the System UI
 * output switcher. Mac.apk has no output switcher, so the preference is stored
 * and read back faithfully (an app that sets it and then reads
 * MediaRouter2.getRouteListingPreference() gets its own object back, as on
 * Android) and simply has no UI to influence.
 *
 * Source: frameworks/base/media/java/android/media/RouteListingPreference.java
 * (android16-release).
 */
public final class RouteListingPreference implements Parcelable {

    /** Intent action an app may declare to handle "transfer to this route". */
    public static final String ACTION_TRANSFER_MEDIA = "android.media.action.TRANSFER_MEDIA";
    /** Extra on {@link #ACTION_TRANSFER_MEDIA} carrying the target route id. */
    public static final String EXTRA_ROUTE_ID = "android.media.extra.ROUTE_ID";

    private final List<Item> mItems;
    private final boolean mUseSystemOrdering;
    private final ComponentName mLinkedItemComponentName;

    private RouteListingPreference(Builder b) {
        mItems = Collections.unmodifiableList(new ArrayList<>(b.mItems));
        mUseSystemOrdering = b.mUseSystemOrdering;
        mLinkedItemComponentName = b.mLinkedItemComponentName;
    }

    public List<Item> getItems() { return mItems; }
    public boolean getUseSystemOrdering() { return mUseSystemOrdering; }
    public ComponentName getLinkedItemComponentName() { return mLinkedItemComponentName; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mItems);
        dest.writeBoolean(mUseSystemOrdering);
        dest.writeTypedObject(mLinkedItemComponentName, flags);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RouteListingPreference)) return false;
        RouteListingPreference o = (RouteListingPreference) obj;
        return mItems.equals(o.mItems)
                && mUseSystemOrdering == o.mUseSystemOrdering
                && Objects.equals(mLinkedItemComponentName, o.mLinkedItemComponentName);
    }

    @Override public int hashCode() {
        return Objects.hash(mItems, mUseSystemOrdering, mLinkedItemComponentName);
    }

    public static final Parcelable.Creator<RouteListingPreference> CREATOR =
            new Parcelable.Creator<RouteListingPreference>() {
        @Override public RouteListingPreference createFromParcel(Parcel in) {
            Builder b = new Builder();
            List<Item> items = in.createTypedArrayList(Item.CREATOR);
            if (items != null) b.setItems(items);
            b.setUseSystemOrdering(in.readBoolean());
            b.setLinkedItemComponentName(in.readTypedObject(ComponentName.CREATOR));
            return b.build();
        }
        @Override public RouteListingPreference[] newArray(int size) {
            return new RouteListingPreference[size];
        }
    };

    /** Builder for {@link RouteListingPreference}. AOSP defaults: empty item
     *  list, useSystemOrdering true, no linked component
     *  (RouteListingPreference.java L216-224). */
    public static final class Builder {
        private List<Item> mItems = Collections.emptyList();
        private boolean mUseSystemOrdering = true;
        private ComponentName mLinkedItemComponentName;

        public Builder() { }

        public Builder setItems(List<Item> items) {
            mItems = new ArrayList<>(Objects.requireNonNull(items));
            return this;
        }
        public Builder setUseSystemOrdering(boolean useSystemOrdering) {
            mUseSystemOrdering = useSystemOrdering;
            return this;
        }
        public Builder setLinkedItemComponentName(ComponentName componentName) {
            mLinkedItemComponentName = componentName;
            return this;
        }
        public RouteListingPreference build() { return new RouteListingPreference(this); }
    }

    /** One entry in the preferred route list. */
    public static final class Item implements Parcelable {

        public static final int FLAG_ONGOING_SESSION = 1;
        public static final int FLAG_ONGOING_SESSION_MANAGED = 1 << 1;
        public static final int FLAG_SUGGESTED = 1 << 2;

        public static final int SELECTION_BEHAVIOR_NONE = 0;
        public static final int SELECTION_BEHAVIOR_TRANSFER = 1;
        public static final int SELECTION_BEHAVIOR_GO_TO_APP = 2;

        public static final int SUBTEXT_NONE = 0;
        public static final int SUBTEXT_ERROR_UNKNOWN = 1;
        public static final int SUBTEXT_SUBSCRIPTION_REQUIRED = 2;
        public static final int SUBTEXT_DOWNLOADED_CONTENT_ROUTING_DISALLOWED = 3;
        public static final int SUBTEXT_AD_ROUTING_DISALLOWED = 4;
        public static final int SUBTEXT_DEVICE_LOW_POWER = 5;
        public static final int SUBTEXT_UNAUTHORIZED = 6;
        public static final int SUBTEXT_TRACK_UNSUPPORTED = 7;
        public static final int SUBTEXT_CUSTOM = 10_000;

        private final String mRouteId;
        private final int mSelectionBehavior;
        private final int mFlags;
        private final int mSubText;
        private final CharSequence mCustomSubtextMessage;

        private Item(Builder b) {
            mRouteId = b.mRouteId;
            mSelectionBehavior = b.mSelectionBehavior;
            mFlags = b.mFlags;
            mSubText = b.mSubText;
            mCustomSubtextMessage = b.mCustomSubtextMessage;
        }

        public String getRouteId() { return mRouteId; }
        public int getSelectionBehavior() { return mSelectionBehavior; }
        public int getFlags() { return mFlags; }
        public int getSubText() { return mSubText; }
        public CharSequence getCustomSubtextMessage() { return mCustomSubtextMessage; }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mRouteId);
            dest.writeInt(mSelectionBehavior);
            dest.writeInt(mFlags);
            dest.writeInt(mSubText);
            dest.writeCharSequence(mCustomSubtextMessage);
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Item)) return false;
            Item o = (Item) obj;
            return Objects.equals(mRouteId, o.mRouteId)
                    && mSelectionBehavior == o.mSelectionBehavior
                    && mFlags == o.mFlags
                    && mSubText == o.mSubText
                    && Objects.equals(mCustomSubtextMessage, o.mCustomSubtextMessage);
        }

        @Override public int hashCode() {
            return Objects.hash(mRouteId, mSelectionBehavior, mFlags, mSubText,
                    mCustomSubtextMessage);
        }

        public static final Parcelable.Creator<Item> CREATOR =
                new Parcelable.Creator<Item>() {
            @Override public Item createFromParcel(Parcel in) {
                Builder b = new Builder(in.readString());
                b.setSelectionBehavior(in.readInt());
                b.setFlags(in.readInt());
                int subText = in.readInt();
                CharSequence custom = in.readCharSequence();
                // setSubText validates SUBTEXT_CUSTOM against a non-null custom
                // message, so the message has to be installed first on the way back in.
                b.mCustomSubtextMessage = custom;
                b.mSubText = subText;
                return b.build();
            }
            @Override public Item[] newArray(int size) { return new Item[size]; }
        };

        /** Builder for {@link Item}. AOSP defaults: SELECTION_BEHAVIOR_TRANSFER,
         *  no flags, SUBTEXT_NONE (RouteListingPreference.java L520-530). */
        public static final class Builder {
            private final String mRouteId;
            private int mSelectionBehavior = SELECTION_BEHAVIOR_TRANSFER;
            private int mFlags;
            private int mSubText = SUBTEXT_NONE;
            private CharSequence mCustomSubtextMessage;

            /** AOSP throws IllegalArgumentException on an empty route id
             *  (RouteListingPreference.java L515). */
            public Builder(String routeId) {
                if (routeId == null || routeId.isEmpty()) {
                    throw new IllegalArgumentException("routeId must not be null or empty");
                }
                mRouteId = routeId;
            }

            public Builder setSelectionBehavior(int selectionBehavior) {
                mSelectionBehavior = selectionBehavior; return this;
            }
            public Builder setFlags(int flags) { mFlags = flags; return this; }
            public Builder setSubText(int subText) { mSubText = subText; return this; }
            public Builder setCustomSubtextMessage(CharSequence customSubtextMessage) {
                mCustomSubtextMessage = customSubtextMessage; return this;
            }
            /** AOSP throws IllegalArgumentException when SUBTEXT_CUSTOM is used
             *  without a custom message (RouteListingPreference.java L575-580). */
            public Item build() {
                if (mSubText == SUBTEXT_CUSTOM && mCustomSubtextMessage == null) {
                    throw new IllegalArgumentException(
                            "The custom subtext message must not be null when subtext is "
                                    + "SUBTEXT_CUSTOM.");
                }
                return new Item(this);
            }
        }
    }
}
