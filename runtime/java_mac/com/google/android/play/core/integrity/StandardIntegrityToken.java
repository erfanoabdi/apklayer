package com.google.android.play.core.integrity;

import android.app.Activity;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
// in the published API is a nested type; the SDK also exposes it at this top
// level path in some artifacts. Per project policy we NEVER forge a token, so no
// instance is ever produced by our provider (the request Task fails first). The
// type exists only so app references compile. Mirrors the published surface.
public abstract class StandardIntegrityToken {

    // The signed integrity verdict. Never produced by us (we do not forge).
    public abstract String token();

    // Remediation dialog; no Play UI, so resolves with a no-op result.
    public Task<Integer> showDialog(Activity activity, int integrityDialogTypeCode) {
        return Tasks.forResult(Integer.valueOf(0));
    }
}
