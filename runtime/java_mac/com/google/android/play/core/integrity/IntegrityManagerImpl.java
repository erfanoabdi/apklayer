package com.google.android.play.core.integrity;

import android.content.Context;
import com.google.android.play.core.integrity.model.IntegrityErrorCode;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// Internal concrete IntegrityManager. NOT part of the published Play Core API
// (apps only see the IntegrityManager interface) -- this is our backing impl.
// Per project policy requestIntegrityToken ALWAYS fails with an honest
// "no backend" error; we never fabricate an attestation token.
final class IntegrityManagerImpl implements IntegrityManager {

    IntegrityManagerImpl(Context context) {
        // No Play Integrity backend; nothing to retain.
    }

    @Override
    public Task<IntegrityTokenResponse> requestIntegrityToken(IntegrityTokenRequest request) {
        return Tasks.forException(
                new IntegrityServiceException(IntegrityErrorCode.PLAY_STORE_NOT_FOUND, null));
    }
}
