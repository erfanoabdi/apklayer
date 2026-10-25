package com.google.android.gms.auth.api.signin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.Arrays;

// com.google.android.gms.auth.api.signin.GoogleSignIn -- the static entry point
// for Google Sign-In. On a de-Googled / no-Play-services device (Mac.apk's
// policy) there is never a signed-in Google account and no backend to reach, so:
//   - getLastSignedInAccount: null (honest "nobody is signed in").
//   - getSignedInAccountFromIntent: the Task fails with
//     ApiException(SIGN_IN_REQUIRED) because no sign-in flow ever produced an
//     account. We never forge a GoogleSignInAccount or an id token.
//   - hasPermissions: false (no account holds any granted scope).
//   - getClient: returns a real GoogleSignInClient (so the app's wiring
//     proceeds) whose flows degrade cleanly.
//
// Ref: developers.google.com/android/reference/com/google/android/gms/auth/api/
// signin/GoogleSignIn
public final class GoogleSignIn {

    private GoogleSignIn() {}

    public static GoogleSignInClient getClient(Activity activity, GoogleSignInOptions options) {
        return new GoogleSignInClient(activity, options);
    }

    public static GoogleSignInClient getClient(Context context, GoogleSignInOptions options) {
        return new GoogleSignInClient(context, options);
    }

    // No account is ever cached on a de-Googled device.
    public static GoogleSignInAccount getLastSignedInAccount(Context context) {
        return null;
    }

    // The result Intent from a sign-in flow that never produced an account. The
    // real method reads the account out of the Intent extras; here there is no
    // account, so the Task fails with the honest SIGN_IN_REQUIRED status. If the
    // Intent did carry a status with a real error code we would surface that,
    // but on Mac.apk no GMS sign-in Activity runs, so data is always empty.
    public static Task<GoogleSignInAccount> getSignedInAccountFromIntent(Intent data) {
        TaskCompletionSource<GoogleSignInAccount> tcs =
                new TaskCompletionSource<GoogleSignInAccount>();
        tcs.setException(new ApiException(
                new Status(GoogleSignInStatusCodes.SIGN_IN_REQUIRED)));
        return tcs.getTask();
    }

    // No account holds any scope, so no requested scope set is ever satisfied.
    public static boolean hasPermissions(GoogleSignInAccount account, Scope... scopes) {
        if (account == null) return false;
        if (scopes == null || scopes.length == 0) return true;
        return account.getGrantedScopes().containsAll(Arrays.asList(scopes));
    }

    // Real Android launches a consent flow here. With no GMS backend there is
    // nothing to request against; this is a clean no-op (the subsequent
    // onActivityResult will carry no granted account, mirroring a denied flow).
    public static void requestPermissions(Activity activity, int requestCode,
                                          GoogleSignInAccount account, Scope... scopes) {
        // Nothing to launch on a de-Googled device.
    }

    public static GoogleSignInAccount getAccountForExtension(Context context,
            GoogleSignInOptionsExtension extension) {
        return null;
    }

    public static GoogleSignInAccount getAccountForScopes(Context context,
            Scope scope, Scope... scopes) {
        return null;
    }
}
