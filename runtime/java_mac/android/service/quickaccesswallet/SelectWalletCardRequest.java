package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Tells the wallet service which card the user scrolled to.
 *
 * See the package note on GetWalletCardsRequest.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/SelectWalletCardRequest.java
 */
public final class SelectWalletCardRequest implements Parcelable {

    private final String mCardId;

    public SelectWalletCardRequest(String cardId) {
        if (cardId == null) throw new NullPointerException("cardId");
        mCardId = cardId;
    }

    public String getCardId() { return mCardId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCardId);
    }

    public static final Creator<SelectWalletCardRequest> CREATOR =
            new Creator<SelectWalletCardRequest>() {
        @Override public SelectWalletCardRequest createFromParcel(Parcel source) {
            return new SelectWalletCardRequest(source.readString());
        }
        @Override public SelectWalletCardRequest[] newArray(int size) {
            return new SelectWalletCardRequest[size];
        }
    };
}
