package android.service.quickaccesswallet;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * What the wallet shows instead of cards when the service cannot supply them.
 *
 * See the package note on GetWalletCardsRequest.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/GetWalletCardsError.java
 */
public final class GetWalletCardsError implements Parcelable {

    private final Icon mIcon;
    private final CharSequence mMessage;

    public GetWalletCardsError(Icon icon, CharSequence message) {
        mIcon = icon;
        mMessage = message;
    }

    public Icon getIcon() { return mIcon; }

    public CharSequence getMessage() { return mMessage; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mIcon, flags);
        TextUtils.writeToParcel(mMessage, dest, flags);
    }

    public static final Creator<GetWalletCardsError> CREATOR =
            new Creator<GetWalletCardsError>() {
        @Override public GetWalletCardsError createFromParcel(Parcel source) {
            return new GetWalletCardsError(source.readTypedObject(Icon.CREATOR),
                    TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source));
        }
        @Override public GetWalletCardsError[] newArray(int size) {
            return new GetWalletCardsError[size];
        }
    };
}
