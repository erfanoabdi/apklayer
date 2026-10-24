package com.google.android.play.core.integrity;

import com.google.android.play.core.tasks.Task;

// com.google.android.play.core.integrity.IntegrityManager -- the entry point for
// the (classic) Play Integrity API. On a de-Googled device there is no Play
// Integrity backend, and per PROJECT POLICY we NEVER forge an attestation token.
// Therefore requestIntegrityToken() FAILS its Task with an
// IntegrityServiceException (PLAY_STORE_NOT_FOUND) rather than returning a fake
// verdict. Apps that gate on integrity will see the failure and take their
// "attestation unavailable" branch, exactly as on a device without Play. The
// type and signature must be exact so the app's catch/branch path runs instead
// of crashing on a missing class. Mirrors the published interface.
public interface IntegrityManager {

    // ALWAYS fails (never forged). See policy note above.
    Task<IntegrityTokenResponse> requestIntegrityToken(IntegrityTokenRequest request);
}
