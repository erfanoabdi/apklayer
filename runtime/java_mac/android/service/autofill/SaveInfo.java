package android.service.autofill;

import android.content.IntentSender;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Pair;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the platform when to offer to SAVE what the user typed, and what kind
 * of data it is.
 *
 * Pure value object with a builder. Mac.apk shows no save dialog, so the
 * prompt never appears -- but the builder's validation is what a password
 * manager's own tests exercise, and AOSP's rules (a type is required, required
 * ids must be non-empty, a custom description and a plain description are
 * mutually exclusive) are reproduced.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt
 * (signature format 2.0):
 *   SAVE_DATA_TYPE_GENERIC=0, PASSWORD=1, ADDRESS=2, CREDIT_CARD=4,
 *   USERNAME=8, EMAIL_ADDRESS=16 (0x10), DEBIT_CARD=32 (0x20),
 *   PAYMENT_CARD=64 (0x40), GENERIC_CARD=128 (0x80)
 *   NEGATIVE_BUTTON_STYLE_CANCEL=0, REJECT=1, NEVER=2
 *   POSITIVE_BUTTON_STYLE_SAVE=0, CONTINUE=1
 *   FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE=1, FLAG_DONT_SAVE_ON_FINISH=2,
 *   FLAG_DELAY_SAVE=4
 * ⚠ The SAVE_DATA_TYPE_* run is powers of two used as a BITMASK; USERNAME is 8
 * and EMAIL_ADDRESS is 16, which is easy to transpose.
 *
 * Source: frameworks/base/core/java/android/service/autofill/SaveInfo.java
 */
public final class SaveInfo implements Parcelable {

    public static final int SAVE_DATA_TYPE_GENERIC = 0;
    public static final int SAVE_DATA_TYPE_PASSWORD = 0x01;
    public static final int SAVE_DATA_TYPE_ADDRESS = 0x02;
    public static final int SAVE_DATA_TYPE_CREDIT_CARD = 0x04;
    public static final int SAVE_DATA_TYPE_USERNAME = 0x08;
    public static final int SAVE_DATA_TYPE_EMAIL_ADDRESS = 0x10;
    public static final int SAVE_DATA_TYPE_DEBIT_CARD = 0x20;
    public static final int SAVE_DATA_TYPE_PAYMENT_CARD = 0x40;
    public static final int SAVE_DATA_TYPE_GENERIC_CARD = 0x80;

    public static final int NEGATIVE_BUTTON_STYLE_CANCEL = 0;
    public static final int NEGATIVE_BUTTON_STYLE_REJECT = 1;
    public static final int NEGATIVE_BUTTON_STYLE_NEVER = 2;

    public static final int POSITIVE_BUTTON_STYLE_SAVE = 0;
    public static final int POSITIVE_BUTTON_STYLE_CONTINUE = 1;

    public static final int FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE = 0x01;
    public static final int FLAG_DONT_SAVE_ON_FINISH = 0x02;
    public static final int FLAG_DELAY_SAVE = 0x04;

    private final int mType;
    private final int mNegativeButtonStyle;
    private final IntentSender mNegativeActionListener;
    private final int mPositiveButtonStyle;
    private final AutofillId[] mRequiredIds;
    private final AutofillId[] mOptionalIds;
    private final CharSequence mDescription;
    private final int mFlags;
    private final CustomDescription mCustomDescription;
    private final Validator mValidator;
    private final List<Pair<Sanitizer, AutofillId[]>> mSanitizers;
    private final AutofillId mTriggerId;

    private SaveInfo(Builder builder) {
        mType = builder.mType;
        mNegativeButtonStyle = builder.mNegativeButtonStyle;
        mNegativeActionListener = builder.mNegativeActionListener;
        mPositiveButtonStyle = builder.mPositiveButtonStyle;
        mRequiredIds = builder.mRequiredIds;
        mOptionalIds = builder.mOptionalIds;
        mDescription = builder.mDescription;
        mFlags = builder.mFlags;
        mCustomDescription = builder.mCustomDescription;
        mValidator = builder.mValidator;
        mSanitizers = builder.mSanitizers;
        mTriggerId = builder.mTriggerId;
    }

    // AOSP's accessors here are all @hide; the platform reads them.
    int getType() { return mType; }

    int getFlags() { return mFlags; }

    AutofillId[] getRequiredIds() { return mRequiredIds; }

    AutofillId[] getOptionalIds() { return mOptionalIds; }

    CharSequence getDescription() { return mDescription; }

    AutofillId getTriggerId() { return mTriggerId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mType);
        parcel.writeTypedArray(mRequiredIds, flags);
        parcel.writeTypedArray(mOptionalIds, flags);
        parcel.writeInt(mNegativeButtonStyle);
        parcel.writeTypedObject(mNegativeActionListener, flags);
        parcel.writeInt(mPositiveButtonStyle);
        TextUtils.writeToParcel(mDescription, parcel, flags);
        parcel.writeTypedObject(mCustomDescription, flags);
        parcel.writeParcelable((Parcelable) mValidator, flags);
        parcel.writeTypedObject(mTriggerId, flags);
        parcel.writeInt(mFlags);
    }

    @Override public String toString() {
        return "SaveInfo[type=" + mType + ", flags=" + mFlags + "]";
    }

    public static final Creator<SaveInfo> CREATOR = new Creator<SaveInfo>() {
        @Override public SaveInfo createFromParcel(Parcel parcel) {
            final int type = parcel.readInt();
            final AutofillId[] required = parcel.createTypedArray(AutofillId.CREATOR);
            final Builder builder = required != null && required.length > 0
                    ? new Builder(type, required) : new Builder(type);
            final AutofillId[] optional = parcel.createTypedArray(AutofillId.CREATOR);
            if (optional != null && optional.length > 0) builder.setOptionalIds(optional);
            builder.setNegativeAction(parcel.readInt(),
                    parcel.readTypedObject(IntentSender.CREATOR));
            builder.setPositiveAction(parcel.readInt());
            CharSequence description =
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel);
            CustomDescription custom = parcel.readTypedObject(CustomDescription.CREATOR);
            if (custom != null) {
                builder.setCustomDescription(custom);
            } else if (description != null) {
                builder.setDescription(description);
            }
            Validator validator = (Validator)
                    parcel.readParcelable(SaveInfo.class.getClassLoader());
            if (validator != null) builder.setValidator(validator);
            AutofillId triggerId = parcel.readTypedObject(AutofillId.CREATOR);
            if (triggerId != null) builder.setTriggerId(triggerId);
            builder.setFlags(parcel.readInt());
            return builder.build();
        }
        @Override public SaveInfo[] newArray(int size) { return new SaveInfo[size]; }
    };

    public static final class Builder {
        private final int mType;
        private int mNegativeButtonStyle = NEGATIVE_BUTTON_STYLE_CANCEL;
        private IntentSender mNegativeActionListener;
        private int mPositiveButtonStyle = POSITIVE_BUTTON_STYLE_SAVE;
        private final AutofillId[] mRequiredIds;
        private AutofillId[] mOptionalIds;
        private CharSequence mDescription;
        private int mFlags;
        private CustomDescription mCustomDescription;
        private Validator mValidator;
        private List<Pair<Sanitizer, AutofillId[]>> mSanitizers;
        private AutofillId mTriggerId;
        private boolean mDestroyed;

        public Builder(int type, AutofillId[] requiredIds) {
            mType = type;
            mRequiredIds = assertValid(requiredIds, "requiredIds");
        }

        /** API 28. No required ids: the save prompt is driven entirely by
         *  FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE or by an explicit trigger id. */
        public Builder(int type) {
            mType = type;
            mRequiredIds = null;
        }

        public Builder setOptionalIds(AutofillId[] ids) {
            throwIfDestroyed();
            mOptionalIds = assertValid(ids, "optionalIds");
            return this;
        }

        public Builder setDescription(CharSequence description) {
            throwIfDestroyed();
            if (mCustomDescription != null) {
                throw new IllegalStateException("Can call setDescription() or "
                        + "setCustomDescription(), but not both");
            }
            mDescription = description;
            return this;
        }

        public Builder setCustomDescription(CustomDescription customDescription) {
            throwIfDestroyed();
            if (mDescription != null) {
                throw new IllegalStateException("Can call setDescription() or "
                        + "setCustomDescription(), but not both");
            }
            if (customDescription == null) throw new NullPointerException("customDescription");
            mCustomDescription = customDescription;
            return this;
        }

        public Builder setNegativeAction(int style, IntentSender listener) {
            throwIfDestroyed();
            switch (style) {
                case NEGATIVE_BUTTON_STYLE_CANCEL:
                case NEGATIVE_BUTTON_STYLE_REJECT:
                case NEGATIVE_BUTTON_STYLE_NEVER:
                    break;
                default:
                    throw new IllegalArgumentException("Invalid style: " + style);
            }
            mNegativeButtonStyle = style;
            mNegativeActionListener = listener;
            return this;
        }

        /** API 30. */
        public Builder setPositiveAction(int style) {
            throwIfDestroyed();
            switch (style) {
                case POSITIVE_BUTTON_STYLE_SAVE:
                case POSITIVE_BUTTON_STYLE_CONTINUE:
                    break;
                default:
                    throw new IllegalArgumentException("Invalid style: " + style);
            }
            mPositiveButtonStyle = style;
            return this;
        }

        public Builder setValidator(Validator validator) {
            throwIfDestroyed();
            if (!(validator instanceof Parcelable)) {
                throw new IllegalArgumentException(
                        "not provided by Android System: " + validator);
            }
            mValidator = validator;
            return this;
        }

        public Builder addSanitizer(Sanitizer sanitizer, AutofillId... ids) {
            throwIfDestroyed();
            if (!(sanitizer instanceof Parcelable)) {
                throw new IllegalArgumentException(
                        "not provided by Android System: " + sanitizer);
            }
            assertValid(ids, "ids");
            if (mSanitizers == null) {
                mSanitizers = new ArrayList<Pair<Sanitizer, AutofillId[]>>();
            }
            mSanitizers.add(new Pair<Sanitizer, AutofillId[]>(sanitizer, ids));
            return this;
        }

        public Builder setFlags(int flags) {
            throwIfDestroyed();
            mFlags = flags;
            return this;
        }

        public Builder setTriggerId(AutofillId id) {
            throwIfDestroyed();
            if (id == null) throw new NullPointerException("id");
            mTriggerId = id;
            return this;
        }

        public SaveInfo build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new SaveInfo(this);
        }

        private static AutofillId[] assertValid(AutofillId[] ids, String name) {
            if (ids == null || ids.length == 0) {
                throw new IllegalArgumentException(name + " must be non-empty");
            }
            for (AutofillId id : ids) {
                if (id == null) throw new NullPointerException(name);
            }
            return ids;
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
