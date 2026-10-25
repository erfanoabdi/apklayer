package com.google.android.play.core.integrity;

import com.google.android.play.core.tasks.Task;

// com.google.android.play.core.integrity.StandardIntegrityManager -- the entry
// point for the newer "standard" Play Integrity request flow (prepare once, then
// mint many tokens). On a de-Googled device there is no backend, and per project
// policy we NEVER forge a token, so prepareIntegrityToken() FAILS its Task with
// an IntegrityServiceException. Apps then take their "attestation unavailable"
// branch. The type and signature must be exact so that path runs instead of a
// missing-class crash. Mirrors the published interface.
public interface StandardIntegrityManager {

    // ALWAYS fails (no backend; never forged).
    Task<StandardIntegrityTokenProvider> prepareIntegrityToken(PrepareIntegrityTokenRequest request);
}
