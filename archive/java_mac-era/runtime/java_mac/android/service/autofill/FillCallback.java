package android.service.autofill;

/**
 * The handle AutofillService.onFillRequest answers through: exactly one of
 * onSuccess / onFailure, once.
 *
 * Mac.apk never issues a fill request, so no instance is handed to a service --
 * but the type is named in the abstract method's signature and must resolve.
 * The double-call guard is AOSP's and is kept: a service that answers twice
 * gets the IllegalStateException here that it would get on a device, rather
 * than silently different behaviour.
 *
 * Source: frameworks/base/core/java/android/service/autofill/FillCallback.java
 */
public final class FillCallback {

    private boolean mCalled;

    FillCallback() { }

    /** Null response means "no suggestions for this activity", which AOSP
     *  treats as a successful empty answer. */
    public void onSuccess(FillResponse response) { assertNotCalled(); }

    public void onFailure(CharSequence message) { assertNotCalled(); }

    private void assertNotCalled() {
        if (mCalled) throw new IllegalStateException("Already called");
        mCalled = true;
    }
}
