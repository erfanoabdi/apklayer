package com.google.android.gms.auth.api.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.auth.api.signin.GoogleSignInClient -- the client an app
// uses to start a sign-in flow, sign out, revoke access, or attempt a silent
// sign-in. It extends GoogleApi<GoogleSignInOptions> exactly as the real SDK.
//
// De-Googled / no-Play-services policy (owner directive): there is no Google
// account backend reachable. We therefore:
//   - getSignInIntent(): return a real Intent that resolves to NO account (the
//     sign-in Activity does not exist, so startActivityForResult returns a null
//     data Intent -> getSignedInAccountFromIntent fails SIGN_IN_REQUIRED). We
//     do NOT launch a fake account chooser or forge a result.
//   - signOut() / revokeAccess(): succeed locally (there is nothing signed in to
//     tear down) -- a clean Task<Void> success, matching real-device semantics
//     when no account is present.
//   - silentSignIn(): fail the Task with ApiException(SIGN_IN_REQUIRED) -- the
//     honest result a real device returns when no account has previously signed
//     in. We never forge a GoogleSignInAccount or id token.
//
// Ref: developers.google.com/android/reference/com/google/android/gms/auth/api/
// signin/GoogleSignInClient
public class GoogleSignInClient extends GoogleApi<GoogleSignInOptions> {

    // The Api descriptor for the Google Sign-In API. Apps don't reference this
    // directly through the client; it just satisfies the GoogleApi super-ctor.
    static final Api<GoogleSignInOptions> GOOGLE_SIGN_IN_API =
            new Api<GoogleSignInOptions>("Auth.Api.SignIn.GOOGLE_SIGN_IN_API");

    public GoogleSignInClient(Activity activity, GoogleSignInOptions options) {
        super(activity, GOOGLE_SIGN_IN_API, options, Settings.DEFAULT_SETTINGS);
    }

    public GoogleSignInClient(Context context, GoogleSignInOptions options) {
        super(context, GOOGLE_SIGN_IN_API, options, Settings.DEFAULT_SETTINGS);
    }

    // Returns the Intent that, on a real device, launches the account-chooser
    // Activity. Here it targets the standard GMS sign-in action with the options
    // attached. No GMS sign-in Activity exists on Mac.apk, so launching it via
    // startActivityForResult yields no result account; getSignedInAccountFromIntent
    // then fails SIGN_IN_REQUIRED. We never manufacture an account chooser UI.
    public Intent getSignInIntent() {
        Intent intent = new Intent("com.google.android.gms.auth.GOOGLE_SIGN_IN");
        intent.setPackage("com.google.android.gms");
        return intent;
    }

    // No account is signed in; signing out is a clean local no-op success.
    public Task<Void> signOut() {
        return Tasks.<Void>forResult(null);
    }

    // No account/grant exists to revoke; clean local success.
    public Task<Void> revokeAccess() {
        return Tasks.<Void>forResult(null);
    }

    // Honest negative result: no prior account, so silent sign-in cannot
    // succeed. We fail the Task rather than block on an unreachable backend.
    public Task<GoogleSignInAccount> silentSignIn() {
        TaskCompletionSource<GoogleSignInAccount> tcs =
                new TaskCompletionSource<GoogleSignInAccount>();
        tcs.setException(new ApiException(
                new Status(GoogleSignInStatusCodes.SIGN_IN_REQUIRED)));
        return tcs.getTask();
    }
}
