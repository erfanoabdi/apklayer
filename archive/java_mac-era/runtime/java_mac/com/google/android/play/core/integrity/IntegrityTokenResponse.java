package com.google.android.play.core.integrity;

import android.app.Activity;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// com.google.android.play.core.integrity.IntegrityTokenResponse -- the success
// result of IntegrityManager.requestIntegrityToken(). Per project policy we
// NEVER forge an attestation token, so requestIntegrityToken() always FAILS its
// Task and no IntegrityTokenResponse is ever produced by our impl. The type
// exists only so app code that references it (catch/branch/field types) compiles.
// Mirrors the published Play Integrity IntegrityTokenResponse.
public abstract class IntegrityTokenResponse {

    // The signed+encrypted integrity verdict. We never produce a response, so
    // this is never called; a foreign subclass would supply its own. We do NOT
    // fabricate a token here.
    public abstract String token();

    // Shows the Play-provided remediation dialog for the given dialog-type code.
    // We have no Play UI, so this resolves immediately with a no-op result.
    public Task<Integer> showDialog(Activity activity, int integrityDialogTypeCode) {
        return Tasks.forResult(Integer.valueOf(0));
    }
}
