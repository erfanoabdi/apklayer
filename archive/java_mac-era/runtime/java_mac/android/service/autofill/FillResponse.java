package android.service.autofill;

import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.assist.classification.FieldClassification;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The service's answer to a fill request: the datasets to offer, how to
 * present them, what to save afterwards, and the authentication step (if any)
 * that must run before any of it is revealed.
 *
 * Pure value object with a large builder. Mac.apk consumes no FillResponse --
 * nothing binds an autofill service -- but the builder is exactly what a
 * password manager exercises in its own tests, and AOSP's rules (the builder is
 * single-use; disableAutofill() is exclusive with everything else; setting an
 * authentication requires ids and a presentation) are reproduced.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   FLAG_TRACK_CONTEXT_COMMITED = 1 (0x1)   -- note AOSP's spelling, one "T"
 *   FLAG_DISABLE_ACTIVITY_ONLY  = 2 (0x2)
 *   FLAG_DELAY_FILL             = 4 (0x4)
 *
 * Source: frameworks/base/core/java/android/service/autofill/FillResponse.java
 */
public final class FillResponse implements Parcelable {

    /** ⚠ AOSP spells it COMMITED, with one T. Correcting the typo would be a
     *  different field name and would not link. */
    public static final int FLAG_TRACK_CONTEXT_COMMITED = 0x1;
    public static final int FLAG_DISABLE_ACTIVITY_ONLY = 0x2;
    public static final int FLAG_DELAY_FILL = 0x4;

    private final List<Dataset> mDatasets;
    private final SaveInfo mSaveInfo;
    private final Bundle mClientState;
    private final RemoteViews mHeader;
    private final RemoteViews mFooter;
    private final RemoteViews mDialogHeader;
    private final IntentSender mAuthentication;
    private final AutofillId[] mAuthenticationIds;
    private final AutofillId[] mIgnoredIds;
    private final AutofillId[] mFieldClassificationIds;
    private final AutofillId[] mFillDialogTriggerIds;
    private final long mDisableDuration;
    private final int mFlags;
    private final int mIconResourceId;
    private final int mServiceDisplayNameResourceId;
    private final boolean mShowFillDialogIcon;
    private final boolean mShowSaveDialogIcon;
    private final int[] mCancelIds;
    private final UserData mUserData;
    private final Set<FieldClassification> mDetectedFieldClassifications;

    private FillResponse(Builder b) {
        mDatasets = b.mDatasets;
        mSaveInfo = b.mSaveInfo;
        mClientState = b.mClientState;
        mHeader = b.mHeader;
        mFooter = b.mFooter;
        mDialogHeader = b.mDialogHeader;
        mAuthentication = b.mAuthentication;
        mAuthenticationIds = b.mAuthenticationIds;
        mIgnoredIds = b.mIgnoredIds;
        mFieldClassificationIds = b.mFieldClassificationIds;
        mFillDialogTriggerIds = b.mFillDialogTriggerIds;
        mDisableDuration = b.mDisableDuration;
        mFlags = b.mFlags;
        mIconResourceId = b.mIconResourceId;
        mServiceDisplayNameResourceId = b.mServiceDisplayNameResourceId;
        mShowFillDialogIcon = b.mShowFillDialogIcon;
        mShowSaveDialogIcon = b.mShowSaveDialogIcon;
        mCancelIds = b.mCancelIds;
        mUserData = b.mUserData;
        mDetectedFieldClassifications = b.mDetectedFieldClassifications;
    }

    // AOSP's accessors are @hide; the platform reads them.
    List<Dataset> getDatasets() { return mDatasets; }

    SaveInfo getSaveInfo() { return mSaveInfo; }

    int getFlags() { return mFlags; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedList(mDatasets);
        parcel.writeTypedObject(mSaveInfo, flags);
        parcel.writeBundle(mClientState);
        parcel.writeTypedObject(mHeader, flags);
        parcel.writeTypedObject(mFooter, flags);
        parcel.writeTypedObject(mDialogHeader, flags);
        parcel.writeTypedObject(mAuthentication, flags);
        parcel.writeTypedArray(mAuthenticationIds, flags);
        parcel.writeTypedArray(mIgnoredIds, flags);
        parcel.writeTypedArray(mFieldClassificationIds, flags);
        parcel.writeTypedArray(mFillDialogTriggerIds, flags);
        parcel.writeLong(mDisableDuration);
        parcel.writeInt(mFlags);
        parcel.writeInt(mIconResourceId);
        parcel.writeInt(mServiceDisplayNameResourceId);
        parcel.writeBoolean(mShowFillDialogIcon);
        parcel.writeBoolean(mShowSaveDialogIcon);
        parcel.writeIntArray(mCancelIds);
        parcel.writeTypedObject(mUserData, flags);
    }

    @Override public String toString() {
        return "FillResponse[datasets=" + (mDatasets == null ? 0 : mDatasets.size())
                + ", flags=" + mFlags + "]";
    }

    public static final Creator<FillResponse> CREATOR = new Creator<FillResponse>() {
        @Override public FillResponse createFromParcel(Parcel parcel) {
            final Builder builder = new Builder();
            final List<Dataset> datasets = parcel.createTypedArrayList(Dataset.CREATOR);
            if (datasets != null) {
                for (Dataset d : datasets) builder.addDataset(d);
            }
            SaveInfo saveInfo = parcel.readTypedObject(SaveInfo.CREATOR);
            if (saveInfo != null) builder.setSaveInfo(saveInfo);
            builder.setClientState(parcel.readBundle());
            RemoteViews header = parcel.readTypedObject(RemoteViews.CREATOR);
            if (header != null) builder.setHeader(header);
            RemoteViews footer = parcel.readTypedObject(RemoteViews.CREATOR);
            if (footer != null) builder.setFooter(footer);
            RemoteViews dialogHeader = parcel.readTypedObject(RemoteViews.CREATOR);
            if (dialogHeader != null) builder.setDialogHeader(dialogHeader);
            IntentSender auth = parcel.readTypedObject(IntentSender.CREATOR);
            AutofillId[] authIds = parcel.createTypedArray(AutofillId.CREATOR);
            if (authIds != null) builder.setAuthentication(authIds, auth, (Presentations) null);
            AutofillId[] ignored = parcel.createTypedArray(AutofillId.CREATOR);
            if (ignored != null) builder.setIgnoredIds(ignored);
            AutofillId[] fcIds = parcel.createTypedArray(AutofillId.CREATOR);
            if (fcIds != null && fcIds.length > 0) builder.setFieldClassificationIds(fcIds);
            AutofillId[] triggerIds = parcel.createTypedArray(AutofillId.CREATOR);
            if (triggerIds != null && triggerIds.length > 0) {
                builder.setFillDialogTriggerIds(triggerIds);
            }
            long disableDuration = parcel.readLong();
            if (disableDuration > 0) builder.disableAutofill(disableDuration);
            builder.setFlags(parcel.readInt());
            int iconRes = parcel.readInt();
            if (iconRes != 0) builder.setIconResourceId(iconRes);
            int nameRes = parcel.readInt();
            if (nameRes != 0) builder.setServiceDisplayNameResourceId(nameRes);
            builder.setShowFillDialogIcon(parcel.readBoolean());
            builder.setShowSaveDialogIcon(parcel.readBoolean());
            builder.setPresentationCancelIds(parcel.createIntArray());
            UserData userData = parcel.readTypedObject(UserData.CREATOR);
            if (userData != null) builder.setUserData(userData);
            return builder.build();
        }
        @Override public FillResponse[] newArray(int size) { return new FillResponse[size]; }
    };

    public static final class Builder {
        private List<Dataset> mDatasets;
        private SaveInfo mSaveInfo;
        private Bundle mClientState;
        private RemoteViews mHeader;
        private RemoteViews mFooter;
        private RemoteViews mDialogHeader;
        private IntentSender mAuthentication;
        private AutofillId[] mAuthenticationIds;
        private AutofillId[] mIgnoredIds;
        private AutofillId[] mFieldClassificationIds;
        private AutofillId[] mFillDialogTriggerIds;
        private long mDisableDuration;
        private int mFlags;
        private int mIconResourceId;
        private int mServiceDisplayNameResourceId;
        private boolean mShowFillDialogIcon = true;
        private boolean mShowSaveDialogIcon = true;
        private int[] mCancelIds;
        private UserData mUserData;
        private Set<FieldClassification> mDetectedFieldClassifications;
        private boolean mDestroyed;
        private boolean mDisableAutofillCalled;

        public Builder() { }

        public Builder addDataset(Dataset dataset) {
            throwIfDestroyed();
            throwIfDisableAutofillCalled();
            if (dataset == null) return this;
            if (mDatasets == null) mDatasets = new ArrayList<Dataset>();
            mDatasets.add(dataset);
            return this;
        }

        public Builder setSaveInfo(SaveInfo saveInfo) {
            throwIfDestroyed();
            throwIfDisableAutofillCalled();
            mSaveInfo = saveInfo;
            return this;
        }

        public Builder setClientState(Bundle clientState) {
            throwIfDestroyed();
            throwIfDisableAutofillCalled();
            mClientState = clientState;
            return this;
        }

        /** @deprecated at API 33; use the Presentations overload. */
        @Deprecated
        public Builder setAuthentication(AutofillId[] ids, IntentSender authentication,
                RemoteViews presentation) {
            return setAuthenticationInternal(ids, authentication);
        }

        @Deprecated
        public Builder setAuthentication(AutofillId[] ids, IntentSender authentication,
                RemoteViews presentation, InlinePresentation inlinePresentation) {
            return setAuthenticationInternal(ids, authentication);
        }

        @Deprecated
        public Builder setAuthentication(AutofillId[] ids, IntentSender authentication,
                RemoteViews presentation, InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            return setAuthenticationInternal(ids, authentication);
        }

        /** API 33. */
        public Builder setAuthentication(AutofillId[] ids, IntentSender authentication,
                Presentations presentations) {
            return setAuthenticationInternal(ids, authentication);
        }

        private Builder setAuthenticationInternal(AutofillId[] ids,
                IntentSender authentication) {
            throwIfDestroyed();
            throwIfDisableAutofillCalled();
            if (ids != null && ids.length == 0) {
                throw new IllegalArgumentException("ids cannot be empty");
            }
            mAuthentication = authentication;
            mAuthenticationIds = ids;
            return this;
        }

        public Builder setIgnoredIds(AutofillId... ids) {
            throwIfDestroyed();
            mIgnoredIds = ids;
            return this;
        }

        public Builder setFieldClassificationIds(AutofillId... ids) {
            throwIfDestroyed();
            throwIfDisableAutofillCalled();
            if (ids == null || ids.length == 0) {
                throw new IllegalArgumentException("ids cannot be empty");
            }
            // AOSP caps this at UserData.getMaxFieldClassificationIdsSize().
            if (ids.length > UserData.getMaxFieldClassificationIdsSize()) {
                throw new IllegalArgumentException("too many ids: " + ids.length);
            }
            mFieldClassificationIds = ids;
            mFlags |= FLAG_TRACK_CONTEXT_COMMITED;
            return this;
        }

        public Builder setFlags(int flags) {
            throwIfDestroyed();
            mFlags = flags;
            return this;
        }

        /** AOSP: exclusive with every other setter, in both directions. */
        public Builder disableAutofill(long duration) {
            throwIfDestroyed();
            if (duration <= 0) {
                throw new IllegalArgumentException("duration must be greater than 0");
            }
            if (mDatasets != null || mSaveInfo != null || mAuthentication != null
                    || mFieldClassificationIds != null || mClientState != null) {
                throw new IllegalStateException("disableAutofill() must be the only "
                        + "method called");
            }
            mDisableDuration = duration;
            mDisableAutofillCalled = true;
            return this;
        }

        public Builder setHeader(RemoteViews header) {
            throwIfDestroyed();
            if (header == null) throw new NullPointerException("header");
            mHeader = header;
            return this;
        }

        public Builder setFooter(RemoteViews footer) {
            throwIfDestroyed();
            if (footer == null) throw new NullPointerException("footer");
            mFooter = footer;
            return this;
        }

        /** API 33. */
        public Builder setDialogHeader(RemoteViews header) {
            throwIfDestroyed();
            if (header == null) throw new NullPointerException("header");
            mDialogHeader = header;
            return this;
        }

        /** API 33. */
        public Builder setFillDialogTriggerIds(AutofillId... ids) {
            throwIfDestroyed();
            if (ids == null || ids.length == 0) {
                throw new IllegalArgumentException("ids cannot be empty");
            }
            mFillDialogTriggerIds = ids;
            return this;
        }

        public Builder setUserData(UserData userData) {
            throwIfDestroyed();
            if (userData == null) throw new NullPointerException("userData");
            mUserData = userData;
            return this;
        }

        /** API 34. */
        public Builder setDetectedFieldClassifications(
                Set<FieldClassification> fieldClassifications) {
            throwIfDestroyed();
            mDetectedFieldClassifications = fieldClassifications;
            return this;
        }

        public Builder setPresentationCancelIds(int[] ids) {
            throwIfDestroyed();
            mCancelIds = ids;
            return this;
        }

        public Builder setIconResourceId(int resId) {
            throwIfDestroyed();
            mIconResourceId = resId;
            return this;
        }

        public Builder setServiceDisplayNameResourceId(int resId) {
            throwIfDestroyed();
            mServiceDisplayNameResourceId = resId;
            return this;
        }

        public Builder setShowFillDialogIcon(boolean show) {
            throwIfDestroyed();
            mShowFillDialogIcon = show;
            return this;
        }

        public Builder setShowSaveDialogIcon(boolean show) {
            throwIfDestroyed();
            mShowSaveDialogIcon = show;
            return this;
        }

        public FillResponse build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new FillResponse(this);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }

        private void throwIfDisableAutofillCalled() {
            if (mDisableAutofillCalled) {
                throw new IllegalStateException("Already called disableAutofill()");
            }
        }
    }
}
