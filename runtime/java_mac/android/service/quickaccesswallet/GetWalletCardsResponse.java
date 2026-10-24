package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;

/**
 * The cards a wallet service returns, plus which one starts selected.
 *
 * See the package note on GetWalletCardsRequest.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/GetWalletCardsResponse.java
 */
public final class GetWalletCardsResponse implements Parcelable {

    private final List<WalletCard> mWalletCards;
    private final int mSelectedIndex;

    public GetWalletCardsResponse(List<WalletCard> walletCards, int selectedIndex) {
        if (walletCards == null) throw new NullPointerException("walletCards");
        mWalletCards = walletCards;
        mSelectedIndex = selectedIndex;
    }

    public List<WalletCard> getWalletCards() { return mWalletCards; }

    public int getSelectedIndex() { return mSelectedIndex; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mWalletCards);
        dest.writeInt(mSelectedIndex);
    }

    public static final Creator<GetWalletCardsResponse> CREATOR =
            new Creator<GetWalletCardsResponse>() {
        @Override public GetWalletCardsResponse createFromParcel(Parcel source) {
            List<WalletCard> cards = source.createTypedArrayList(WalletCard.CREATOR);
            return new GetWalletCardsResponse(
                    cards == null ? Collections.<WalletCard>emptyList() : cards,
                    source.readInt());
        }
        @Override public GetWalletCardsResponse[] newArray(int size) {
            return new GetWalletCardsResponse[size];
        }
    };
}
