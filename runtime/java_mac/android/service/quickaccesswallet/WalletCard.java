package android.service.quickaccesswallet;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One card in the quick-access wallet: an id, an image, a label, and the
 * PendingIntent that opens it in the wallet app.
 *
 * See the package note on GetWalletCardsRequest. Note what a WalletCard is NOT:
 * it carries no PAN, no token and no key -- an Icon, some text, and an intent
 * back into the owning app. That is AOSP's design, and it is reproduced as-is.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   CARD_TYPE_UNKNOWN = 0, CARD_TYPE_PAYMENT = 1, CARD_TYPE_NON_PAYMENT = 2.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/WalletCard.java
 */
public final class WalletCard implements Parcelable {

    public static final int CARD_TYPE_UNKNOWN = 0;
    public static final int CARD_TYPE_PAYMENT = 1;
    public static final int CARD_TYPE_NON_PAYMENT = 2;

    private final String mCardId;
    private final int mCardType;
    private final Icon mCardImage;
    private final CharSequence mContentDescription;
    private final PendingIntent mPendingIntent;
    private final Icon mCardIcon;
    private final CharSequence mCardLabel;
    private final Icon mNonPaymentCardSecondaryImage;
    private final List<Location> mCardLocations;

    private WalletCard(Builder b) {
        mCardId = b.mCardId;
        mCardType = b.mCardType;
        mCardImage = b.mCardImage;
        mContentDescription = b.mContentDescription;
        mPendingIntent = b.mPendingIntent;
        mCardIcon = b.mCardIcon;
        mCardLabel = b.mCardLabel;
        mNonPaymentCardSecondaryImage = b.mNonPaymentCardSecondaryImage;
        mCardLocations = b.mCardLocations;
    }

    public String getCardId() { return mCardId; }

    /** API 33. */
    public int getCardType() { return mCardType; }

    public Icon getCardImage() { return mCardImage; }

    public CharSequence getContentDescription() { return mContentDescription; }

    public PendingIntent getPendingIntent() { return mPendingIntent; }

    public Icon getCardIcon() { return mCardIcon; }

    public CharSequence getCardLabel() { return mCardLabel; }

    /** API 33. */
    public Icon getNonPaymentCardSecondaryImage() { return mNonPaymentCardSecondaryImage; }

    /** API 31. Never null in AOSP -- an empty list when the card is not
     *  location-scoped. */
    public List<Location> getCardLocations() { return mCardLocations; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCardId);
        dest.writeInt(mCardType);
        dest.writeTypedObject(mCardImage, flags);
        TextUtils.writeToParcel(mContentDescription, dest, flags);
        dest.writeTypedObject(mPendingIntent, flags);
        dest.writeTypedObject(mCardIcon, flags);
        TextUtils.writeToParcel(mCardLabel, dest, flags);
        dest.writeTypedObject(mNonPaymentCardSecondaryImage, flags);
        dest.writeTypedList(mCardLocations);
    }

    @Override public String toString() {
        return "WalletCard[id=" + mCardId + ", type=" + mCardType + "]";
    }

    public static final Creator<WalletCard> CREATOR = new Creator<WalletCard>() {
        @Override public WalletCard createFromParcel(Parcel source) {
            final String cardId = source.readString();
            final int cardType = source.readInt();
            final Icon cardImage = source.readTypedObject(Icon.CREATOR);
            final CharSequence contentDescription =
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            final PendingIntent pendingIntent =
                    source.readTypedObject(PendingIntent.CREATOR);
            final Builder builder = new Builder(cardId, cardType, cardImage,
                    contentDescription == null ? "" : contentDescription, pendingIntent);
            builder.setCardIcon(source.readTypedObject(Icon.CREATOR));
            builder.setCardLabel(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source));
            builder.setNonPaymentCardSecondaryImage(source.readTypedObject(Icon.CREATOR));
            final List<Location> locations =
                    source.createTypedArrayList(Location.CREATOR);
            if (locations != null) builder.setCardLocations(locations);
            return builder.build();
        }
        @Override public WalletCard[] newArray(int size) { return new WalletCard[size]; }
    };

    public static final class Builder {
        private final String mCardId;
        private final int mCardType;
        private final Icon mCardImage;
        private final CharSequence mContentDescription;
        private final PendingIntent mPendingIntent;
        private Icon mCardIcon;
        private CharSequence mCardLabel;
        private Icon mNonPaymentCardSecondaryImage;
        private List<Location> mCardLocations = Collections.emptyList();

        /** API 33. */
        public Builder(String cardId, int cardType, Icon cardImage,
                CharSequence contentDescription, PendingIntent pendingIntent) {
            if (cardId == null) throw new NullPointerException("cardId");
            if (cardImage == null) throw new NullPointerException("cardImage");
            if (contentDescription == null) {
                throw new NullPointerException("contentDescription");
            }
            if (pendingIntent == null) throw new NullPointerException("pendingIntent");
            mCardId = cardId;
            mCardType = cardType;
            mCardImage = cardImage;
            mContentDescription = contentDescription;
            mPendingIntent = pendingIntent;
        }

        /** Pre-API-33 form; AOSP defaults the type to CARD_TYPE_UNKNOWN. */
        public Builder(String cardId, Icon cardImage, CharSequence contentDescription,
                PendingIntent pendingIntent) {
            this(cardId, CARD_TYPE_UNKNOWN, cardImage, contentDescription, pendingIntent);
        }

        public Builder setCardIcon(Icon cardIcon) { mCardIcon = cardIcon; return this; }

        public Builder setCardLabel(CharSequence cardLabel) {
            mCardLabel = cardLabel;
            return this;
        }

        public Builder setNonPaymentCardSecondaryImage(Icon image) {
            mNonPaymentCardSecondaryImage = image;
            return this;
        }

        public Builder setCardLocations(List<Location> cardLocations) {
            if (cardLocations == null) throw new NullPointerException("cardLocations");
            mCardLocations = new ArrayList<Location>(cardLocations);
            return this;
        }

        public WalletCard build() { return new WalletCard(this); }
    }
}
