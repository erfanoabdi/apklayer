package android.service.quickaccesswallet;

/**
 * How a QuickAccessWalletService answers onWalletCardsRequested: exactly one of
 * onSuccess / onFailure.
 *
 * See the package note on GetWalletCardsRequest.
 *
 * Source: frameworks/base/core/java/android/service/quickaccesswallet/GetWalletCardsCallback.java
 */
public interface GetWalletCardsCallback {

    void onSuccess(GetWalletCardsResponse response);

    void onFailure(GetWalletCardsError error);
}
