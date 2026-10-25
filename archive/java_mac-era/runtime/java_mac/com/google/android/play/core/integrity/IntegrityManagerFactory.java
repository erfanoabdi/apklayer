package com.google.android.play.core.integrity;

import android.content.Context;

// com.google.android.play.core.integrity.IntegrityManagerFactory -- the static
// factory apps call to obtain an IntegrityManager (classic flow) and, in newer
// artifacts, a StandardIntegrityManager. Mirrors the published factory's static
// create(Context) methods. Both managers honour project policy: requests FAIL
// with an honest "no backend" error; tokens are never forged.
public final class IntegrityManagerFactory {
    private IntegrityManagerFactory() {}

    public static IntegrityManager create(Context context) {
        return new IntegrityManagerImpl(context);
    }

    public static StandardIntegrityManager createStandard(Context context) {
        return new StandardIntegrityManagerImpl(context);
    }
}
