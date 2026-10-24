package com.google.android.play.core.integrity;

import com.google.android.play.core.tasks.Task;

// com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
// -- the warmed-up provider returned by prepareIntegrityToken(), used to mint
// per-request tokens. Per project policy we NEVER forge a token, so request()
// FAILS its Task with an IntegrityServiceException. The type and signature must
// be exact so the app's catch/branch path runs. Mirrors the published interface.
public interface StandardIntegrityTokenProvider {

    // ALWAYS fails (never forged).
    Task<StandardIntegrityToken> request(StandardIntegrityTokenRequest request);
}
