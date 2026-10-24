package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The sizes System UI's quick-access wallet wants cards rendered at, and how
 * many it will show.
 *
 * PACKAGE NOTE -- applies to android.service.quickaccesswallet. A
 * QuickAccessWalletService is bound by System UI's power-button wallet, which
 * Mac.apk does not have: nothing binds the service, no card request arrives,
 * and sendWalletServiceEvent has no listener. The request/response objects are
 * ordinary value types a wallet app builds in its own tests, and those are
 * faithful. Nothing here touches a payment credential; a WalletCard carries an
 * Icon, a label and a PendingIntent, which is all AOSP puts in one.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/GetWalletCardsRequest.java
 */
public final class GetWalletCardsRequest implements Parcelable {

    private final int mCardWidthPx;
    private final int mCardHeightPx;
    private final int mIconSizePx;
    private final int mMaxCards;

    public GetWalletCardsRequest(int cardWidthPx, int cardHeightPx, int iconSizePx,
            int maxCards) {
        mCardWidthPx = cardWidthPx;
        mCardHeightPx = cardHeightPx;
        mIconSizePx = iconSizePx;
        mMaxCards = maxCards;
    }

    public int getCardWidthPx() { return mCardWidthPx; }

    public int getCardHeightPx() { return mCardHeightPx; }

    public int getIconSizePx() { return mIconSizePx; }

    public int getMaxCards() { return mMaxCards; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCardWidthPx);
        dest.writeInt(mCardHeightPx);
        dest.writeInt(mIconSizePx);
        dest.writeInt(mMaxCards);
    }

    public static final Creator<GetWalletCardsRequest> CREATOR =
            new Creator<GetWalletCardsRequest>() {
        @Override public GetWalletCardsRequest createFromParcel(Parcel source) {
            return new GetWalletCardsRequest(source.readInt(), source.readInt(),
                    source.readInt(), source.readInt());
        }
        @Override public GetWalletCardsRequest[] newArray(int size) {
            return new GetWalletCardsRequest[size];
        }
    };
}
