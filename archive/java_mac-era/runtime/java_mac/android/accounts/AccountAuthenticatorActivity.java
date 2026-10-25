package android.accounts;

import android.app.Activity;
import android.os.Bundle;

/**
 * Base Activity for an authenticator's login screen.
 *
 * Fully implementable in-process -- the whole class is "read the response out of
 * the launching Intent, tell it the request continued, and report the result (or
 * ERROR_CODE_CANCELED) on finish". No AccountManagerService is involved on the
 * app's side, so this is faithful here, not a stub.
 *
 * @deprecated AOSP deprecated this in API 30 in favour of handling the response
 * directly; still shipped, and still what older authenticator apps extend.
 *
 * Source: frameworks/base/core/java/android/accounts/AccountAuthenticatorActivity.java
 */
@Deprecated
public class AccountAuthenticatorActivity extends Activity {

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;

    public final void setAccountAuthenticatorResult(Bundle result) {
        mResultBundle = result;
    }

    @Override protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mAccountAuthenticatorResponse = getIntent().getParcelableExtra(
                AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                AccountAuthenticatorResponse.class);
        if (mAccountAuthenticatorResponse != null) {
            mAccountAuthenticatorResponse.onRequestContinued();
        }
    }

    @Override public void finish() {
        if (mAccountAuthenticatorResponse != null) {
            // AOSP: send the result bundle back if set, otherwise report cancellation.
            if (mResultBundle != null) {
                mAccountAuthenticatorResponse.onResult(mResultBundle);
            } else {
                mAccountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED,
                        "canceled");
            }
            mAccountAuthenticatorResponse = null;
        }
        super.finish();
    }
}
