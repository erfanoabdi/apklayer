package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * An out-of-band notification from the wallet service to the wallet UI -- so
 * far only "an NFC payment just started", which tells the UI to dismiss.
 *
 * See the package note on GetWalletCardsRequest.
 *
 * Constant transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   TYPE_NFC_PAYMENT_STARTED = 1 (0x1). There is no zero value in the public
 *   API.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/WalletServiceEvent.java
 */
public final class WalletServiceEvent implements Parcelable {

    public static final int TYPE_NFC_PAYMENT_STARTED = 1;

    private final int mEventType;

    public WalletServiceEvent(int eventType) { mEventType = eventType; }

    public int getEventType() { return mEventType; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEventType);
    }

    public static final Creator<WalletServiceEvent> CREATOR =
            new Creator<WalletServiceEvent>() {
        @Override public WalletServiceEvent createFromParcel(Parcel source) {
            return new WalletServiceEvent(source.readInt());
        }
        @Override public WalletServiceEvent[] newArray(int size) {
            return new WalletServiceEvent[size];
        }
    };
}
