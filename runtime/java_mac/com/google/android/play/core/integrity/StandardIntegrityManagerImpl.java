package com.google.android.play.core.integrity;

import android.content.Context;
import com.google.android.play.core.integrity.model.IntegrityErrorCode;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// Internal concrete StandardIntegrityManager. NOT part of the published Play
// Core API (apps only see the interface) -- this is our backing impl. Per
// project policy prepareIntegrityToken ALWAYS fails with an honest "no backend"
// error; we never fabricate a provider that would mint a forged token.
final class StandardIntegrityManagerImpl implements StandardIntegrityManager {

    StandardIntegrityManagerImpl(Context context) {
        // No Play Integrity backend; nothing to retain.
    }

    @Override
    public Task<StandardIntegrityTokenProvider> prepareIntegrityToken(
            PrepareIntegrityTokenRequest request) {
        return Tasks.forException(
                new IntegrityServiceException(IntegrityErrorCode.PLAY_STORE_NOT_FOUND, null));
    }
}
