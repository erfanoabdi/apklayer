package android.service.autofill;

import android.content.IntentSender;

/**
 * The handle AutofillService.onSaveRequest answers through: exactly one of
 * onSuccess / onFailure, once.
 *
 * See FillCallback for why the double-call guard is reproduced.
 *
 * Source: frameworks/base/core/java/android/service/autofill/SaveCallback.java
 */
public final class SaveCallback {

    private boolean mCalled;

    SaveCallback() { }

    public void onSuccess() { assertNotCalled(); }

    /** API 28. The IntentSender launches the service's own "saved" activity. */
    public void onSuccess(IntentSender intentSender) {
        if (intentSender == null) throw new NullPointerException("intentSender");
        assertNotCalled();
    }

    public void onFailure(CharSequence message) { assertNotCalled(); }

    private void assertNotCalled() {
        if (mCalled) throw new IllegalStateException("Already called");
        mCalled = true;
    }
}
