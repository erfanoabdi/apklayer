// android.telecom.PhoneAccount -- an app's description of a calling account it
// would like Telecom to know about (a SIM, or a VoIP app's own account).
//
// A REAL value object: builder, getters, capability bits and parcelling are all
// pure data, and an app builds one before it ever asks whether the device can
// use it. Building a PhoneAccount succeeds here exactly as on Android.
//
// What does NOT quietly succeed is REGISTERING it --
// TelecomManager.registerPhoneAccount throws, because a registration that
// silently succeeds and is then never called back is what hangs a calling app.
// The reasoning is at that method.
//
// isEnabled() returns false. On Android an account is disabled until the user
// enables it in Settings, and a freshly built one is never enabled, so false is
// both AOSP-accurate and the honest answer for a device that cannot enable it
// at all.
//
// Mirrors telecomm/java/android/telecom/PhoneAccount.java; constants and the
// public member list confirmed against frameworks/base/core/api/current.txt
// (the @hide setters setLabel/setIsEnabled/setSupportedAudioRoutes are
// deliberately absent -- they are not public API).
package android.telecom;

import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PhoneAccount implements Parcelable {

    public static final String EXTRA_SORT_ORDER = "android.telecom.extra.SORT_ORDER";
    public static final String EXTRA_CALL_SUBJECT_MAX_LENGTH =
            "android.telecom.extra.CALL_SUBJECT_MAX_LENGTH";
    public static final String EXTRA_CALL_SUBJECT_CHARACTER_ENCODING =
            "android.telecom.extra.CALL_SUBJECT_CHARACTER_ENCODING";
    public static final String EXTRA_ADD_SELF_MANAGED_CALLS_TO_INCALLSERVICE =
            "android.telecom.extra.ADD_SELF_MANAGED_CALLS_TO_INCALLSERVICE";
    public static final String EXTRA_ALWAYS_USE_VOIP_AUDIO_MODE =
            "android.telecom.extra.ALWAYS_USE_VOIP_AUDIO_MODE";
    public static final String EXTRA_SUPPORTS_HANDOVER_TO =
            "android.telecom.extra.SUPPORTS_HANDOVER_TO";
    public static final String EXTRA_SUPPORTS_HANDOVER_FROM =
            "android.telecom.extra.SUPPORTS_HANDOVER_FROM";
    public static final String EXTRA_LOG_SELF_MANAGED_CALLS =
            "android.telecom.extra.LOG_SELF_MANAGED_CALLS";
    public static final String EXTRA_PLAY_CALL_RECORDING_TONE =
            "android.telecom.extra.PLAY_CALL_RECORDING_TONE";
    public static final String EXTRA_SKIP_CALL_FILTERING =
            "android.telecom.extra.SKIP_CALL_FILTERING";

    public static final int CAPABILITY_CONNECTION_MANAGER = 0x1;
    public static final int CAPABILITY_CALL_PROVIDER = 0x2;
    public static final int CAPABILITY_SIM_SUBSCRIPTION = 0x4;
    public static final int CAPABILITY_VIDEO_CALLING = 0x8;
    public static final int CAPABILITY_PLACE_EMERGENCY_CALLS = 0x10;
    public static final int CAPABILITY_CALL_SUBJECT = 0x40;
    public static final int CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE = 0x100;
    public static final int CAPABILITY_SUPPORTS_VIDEO_CALLING = 0x400;
    public static final int CAPABILITY_SELF_MANAGED = 0x800;
    public static final int CAPABILITY_RTT = 0x1000;
    public static final int CAPABILITY_ADHOC_CONFERENCE_CALLING = 0x4000;
    public static final int CAPABILITY_CALL_COMPOSER = 0x8000;
    public static final int CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS = 0x10000;
    public static final int CAPABILITY_VOICE_CALLING_AVAILABLE = 0x20000;
    public static final int CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS = 0x40000;
    public static final int CAPABILITY_SUPPORTS_CALL_STREAMING = 0x80000;

    public static final String SCHEME_TEL = "tel";
    public static final String SCHEME_VOICEMAIL = "voicemail";
    public static final String SCHEME_SIP = "sip";

    public static final int NO_HIGHLIGHT_COLOR = 0;
    public static final int NO_RESOURCE_ID = -1;

    private final PhoneAccountHandle mAccountHandle;
    private final Uri mAddress;
    private final Uri mSubscriptionAddress;
    private final int mCapabilities;
    private final int mHighlightColor;
    private final CharSequence mLabel;
    private final CharSequence mShortDescription;
    private final List<String> mSupportedUriSchemes;
    private final Icon mIcon;
    private final Bundle mExtras;
    private boolean mIsEnabled;
    private final String mGroupId;
    /**
     * Null means NO restriction (every account may be used simultaneously) --
     * which is a different state from an EMPTY set, meaning "this account may
     * be used with no other". AOSP encodes the difference exactly this way and
     * getSimultaneousCallingRestriction() throws on the null case, so the two
     * must not be collapsed.
     */
    private final Set<PhoneAccountHandle> mSimultaneousCallingRestriction;

    private PhoneAccount(PhoneAccountHandle account, Uri address, Uri subscriptionAddress,
            int capabilities, Icon icon, int highlightColor, CharSequence label,
            CharSequence shortDescription, List<String> supportedUriSchemes,
            Bundle extras, boolean isEnabled, String groupId,
            Set<PhoneAccountHandle> simultaneousCallingRestriction) {
        mAccountHandle = account;
        mAddress = address;
        mSubscriptionAddress = subscriptionAddress;
        mCapabilities = capabilities;
        mIcon = icon;
        mHighlightColor = highlightColor;
        mLabel = label;
        mShortDescription = shortDescription;
        mSupportedUriSchemes = Collections.unmodifiableList(supportedUriSchemes);
        mExtras = extras;
        mIsEnabled = isEnabled;
        mGroupId = groupId;
        mSimultaneousCallingRestriction = simultaneousCallingRestriction;
    }

    public static Builder builder(PhoneAccountHandle accountHandle, CharSequence label) {
        return new Builder(accountHandle, label);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder {
        private PhoneAccountHandle mAccountHandle;
        private Uri mAddress;
        private Uri mSubscriptionAddress;
        private int mCapabilities;
        private int mHighlightColor = NO_HIGHLIGHT_COLOR;
        private CharSequence mLabel;
        private CharSequence mShortDescription;
        private List<String> mSupportedUriSchemes = new ArrayList<String>();
        private Icon mIcon;
        private Bundle mExtras;
        private boolean mIsEnabled = false;
        private String mGroupId = "";
        private Set<PhoneAccountHandle> mSimultaneousCallingRestriction = null;

        public Builder(PhoneAccountHandle accountHandle, CharSequence label) {
            this.mAccountHandle = accountHandle;
            this.mLabel = label;
        }

        public Builder(PhoneAccount phoneAccount) {
            mAccountHandle = phoneAccount.getAccountHandle();
            mAddress = phoneAccount.getAddress();
            mSubscriptionAddress = phoneAccount.getSubscriptionAddress();
            mCapabilities = phoneAccount.getCapabilities();
            mHighlightColor = phoneAccount.getHighlightColor();
            mLabel = phoneAccount.getLabel();
            mShortDescription = phoneAccount.getShortDescription();
            mSupportedUriSchemes.addAll(phoneAccount.getSupportedUriSchemes());
            mIcon = phoneAccount.getIcon();
            mIsEnabled = phoneAccount.isEnabled();
            mExtras = phoneAccount.getExtras();
            mGroupId = phoneAccount.getGroupId();
            // AOSP guards the copy: getSimultaneousCallingRestriction() throws
            // when there is no restriction, so toBuilder() must ask first.
            if (phoneAccount.hasSimultaneousCallingRestriction()) {
                mSimultaneousCallingRestriction = phoneAccount.getSimultaneousCallingRestriction();
            }
        }

        public Builder setAddress(Uri value) {
            this.mAddress = value;
            return this;
        }

        public Builder setSubscriptionAddress(Uri value) {
            this.mSubscriptionAddress = value;
            return this;
        }

        public Builder setCapabilities(int value) {
            this.mCapabilities = value;
            return this;
        }

        public Builder setIcon(Icon icon) {
            mIcon = icon;
            return this;
        }

        public Builder setHighlightColor(int value) {
            this.mHighlightColor = value;
            return this;
        }

        public Builder setShortDescription(CharSequence value) {
            this.mShortDescription = value;
            return this;
        }

        public Builder addSupportedUriScheme(String uriScheme) {
            if (uriScheme != null && uriScheme.length() > 0
                    && !mSupportedUriSchemes.contains(uriScheme)) {
                this.mSupportedUriSchemes.add(uriScheme);
            }
            return this;
        }

        public Builder setSupportedUriSchemes(List<String> uriSchemes) {
            mSupportedUriSchemes.clear();

            if (uriSchemes != null && !uriSchemes.isEmpty()) {
                for (String uriScheme : uriSchemes) {
                    addSupportedUriScheme(uriScheme);
                }
            }
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        /**
         * Restricts which other PhoneAccounts this one may be in a call with
         * at the same time. Pure local bookkeeping on the value object -- what
         * a Mac lacks is a Telecom service to ENFORCE it, not the ability to
         * record it, so an app that sets a restriction and reads it back gets
         * its own set. AOSP rejects null here rather than treating it as
         * "clear"; clearSimultaneousCallingRestriction() is the clear.
         */
        public Builder setSimultaneousCallingRestriction(Set<PhoneAccountHandle> handles) {
            if (handles == null) {
                throw new IllegalArgumentException("the Set of PhoneAccountHandles must not be "
                        + "null");
            }
            mSimultaneousCallingRestriction = handles;
            return this;
        }

        /** Removes the restriction, restoring the default "no restriction". */
        public Builder clearSimultaneousCallingRestriction() {
            mSimultaneousCallingRestriction = null;
            return this;
        }

        public PhoneAccount build() {
            // AOSP: an account with no explicit schemes defaults to tel.
            if (mSupportedUriSchemes.isEmpty()) {
                addSupportedUriScheme(SCHEME_TEL);
            }

            return new PhoneAccount(
                    mAccountHandle,
                    mAddress,
                    mSubscriptionAddress,
                    mCapabilities,
                    mIcon,
                    mHighlightColor,
                    mLabel,
                    mShortDescription,
                    mSupportedUriSchemes,
                    mExtras,
                    mIsEnabled,
                    mGroupId,
                    mSimultaneousCallingRestriction);
        }
    }

    public PhoneAccountHandle getAccountHandle() {
        return mAccountHandle;
    }

    /**
     * AOSP THROWS when no restriction is set rather than returning null or an
     * empty set, because empty legitimately means "may be used with no other
     * account". Copied exactly -- an app that skipped
     * hasSimultaneousCallingRestriction() must fail the same way here as on
     * Android instead of silently reading an empty set as a restriction.
     *
     * @throws IllegalStateException if there is no restriction set.
     */
    public Set<PhoneAccountHandle> getSimultaneousCallingRestriction() {
        if (mSimultaneousCallingRestriction == null) {
            throw new IllegalStateException("This method must not be called if there is no "
                    + "simultaneous calling restriction. See #hasSimultaneousCallingRestriction");
        }
        return mSimultaneousCallingRestriction;
    }

    public boolean hasSimultaneousCallingRestriction() {
        return mSimultaneousCallingRestriction != null;
    }

    public Uri getAddress() {
        return mAddress;
    }

    public Uri getSubscriptionAddress() {
        return mSubscriptionAddress;
    }

    public int getCapabilities() {
        return mCapabilities;
    }

    public boolean hasCapabilities(int capability) {
        return (mCapabilities & capability) == capability;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public CharSequence getShortDescription() {
        return mShortDescription;
    }

    public List<String> getSupportedUriSchemes() {
        return mSupportedUriSchemes;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public Icon getIcon() {
        return mIcon;
    }

    /**
     * False: on Android an account is disabled until the user enables it in
     * Settings, so a built-but-unregistered account is never enabled. There is
     * no Settings surface here that could enable one either.
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /** @hide on AOSP. */
    public String getGroupId() {
        return mGroupId;
    }

    public boolean supportsUriScheme(String uriScheme) {
        if (mSupportedUriSchemes == null || uriScheme == null) {
            return false;
        }

        for (String scheme : mSupportedUriSchemes) {
            if (scheme != null && scheme.equals(uriScheme)) {
                return true;
            }
        }
        return false;
    }

    public int getHighlightColor() {
        return mHighlightColor;
    }

    public boolean isSelfManaged() {
        return (mCapabilities & CAPABILITY_SELF_MANAGED) == CAPABILITY_SELF_MANAGED;
    }

    @Override public int describeContents() {
        return 0;
    }

    // The Icon is not marshalled: android.graphics.drawable.Icon in this tree
    // does not implement Parcelable. A fixed marker slot keeps the read and
    // write sides in step. REMOVAL CONDITION: when Icon implements Parcelable,
    // marshal it here. Nothing round-trips a PhoneAccount today, because the
    // only consumer would be the Telecom service.
    private static final int ICON_ABSENT = 0;

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mAccountHandle, 0);
        out.writeParcelable(mAddress, 0);
        out.writeParcelable(mSubscriptionAddress, 0);
        out.writeInt(mCapabilities);
        out.writeInt(mHighlightColor);
        out.writeCharSequence(mLabel);
        out.writeCharSequence(mShortDescription);
        out.writeStringList(mSupportedUriSchemes);
        out.writeInt(ICON_ABSENT);
        out.writeByte((byte) (mIsEnabled ? 1 : 0));
        out.writeBundle(mExtras);
        out.writeString(mGroupId);
        // AOSP marshals the restriction as a presence byte + the handle list,
        // so that "no restriction" (null) survives the round trip distinctly
        // from "restricted to nothing" (empty).
        if (mSimultaneousCallingRestriction == null) {
            out.writeByte((byte) 0);
        } else {
            out.writeByte((byte) 1);
            out.writeTypedList(new ArrayList<>(mSimultaneousCallingRestriction));
        }
    }

    public static final Creator<PhoneAccount> CREATOR = new Creator<PhoneAccount>() {
        @Override public PhoneAccount createFromParcel(Parcel in) {
            PhoneAccountHandle accountHandle = in.readParcelable(getClass().getClassLoader());
            Uri address = in.readParcelable(getClass().getClassLoader());
            Uri subscriptionAddress = in.readParcelable(getClass().getClassLoader());
            int capabilities = in.readInt();
            int highlightColor = in.readInt();
            CharSequence label = in.readCharSequence();
            CharSequence shortDescription = in.readCharSequence();
            List<String> supportedUriSchemes = new ArrayList<>();
            in.readStringList(supportedUriSchemes);
            in.readInt();
            Icon icon = null;
            boolean isEnabled = in.readByte() == 1;
            Bundle extras = in.readBundle();
            String groupId = in.readString();
            Set<PhoneAccountHandle> restriction = null;
            if (in.readByte() == 1) {
                List<PhoneAccountHandle> handles =
                        in.createTypedArrayList(PhoneAccountHandle.CREATOR);
                restriction = new LinkedHashSet<>(
                        handles == null ? Collections.emptyList() : handles);
            }
            return new PhoneAccount(accountHandle, address, subscriptionAddress, capabilities,
                    icon, highlightColor, label, shortDescription, supportedUriSchemes, extras,
                    isEnabled, groupId, restriction);
        }

        @Override public PhoneAccount[] newArray(int size) {
            return new PhoneAccount[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhoneAccount that = (PhoneAccount) o;
        return mCapabilities == that.mCapabilities
                && mHighlightColor == that.mHighlightColor
                && mIsEnabled == that.mIsEnabled
                && java.util.Objects.equals(mAccountHandle, that.mAccountHandle)
                && java.util.Objects.equals(mAddress, that.mAddress)
                && java.util.Objects.equals(mSubscriptionAddress, that.mSubscriptionAddress)
                && java.util.Objects.equals(mSupportedUriSchemes, that.mSupportedUriSchemes)
                && java.util.Objects.equals(mGroupId, that.mGroupId)
                && java.util.Objects.equals(mSimultaneousCallingRestriction,
                        that.mSimultaneousCallingRestriction);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mAccountHandle, mAddress, mSubscriptionAddress,
                mCapabilities, mHighlightColor, mLabel, mShortDescription, mSupportedUriSchemes,
                mIsEnabled, mGroupId, mSimultaneousCallingRestriction);
    }

    @Override public String toString() {
        return "[PhoneAccount: " + mAccountHandle
                + " Capabilities: " + mCapabilities
                + " Schemes: " + mSupportedUriSchemes
                + " Enabled: " + mIsEnabled + "]";
    }
}
