package android.accounts;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * The channel an AbstractAccountAuthenticator uses to return a result to the
 * AccountManager.
 *
 * AOSP wraps an IAccountAuthenticatorResponse Binder living in the
 * AccountManagerService. Mac.apk has no such service, so there is no peer to
 * deliver to and the three callbacks are genuinely inert -- an authenticator
 * that calls onResult() is talking to a framework component we do not run.
 * That is an absence, not a fabrication: pretending the result was accepted
 * somewhere would be worse.
 *
 * The Parcelable half IS real, because the response travels to the
 * authenticator's own Activity inside KEY_ACCOUNT_AUTHENTICATOR_RESPONSE and
 * AccountAuthenticatorActivity reads it back out. The binder we carry is
 * whatever was written; a null binder round-trips as null.
 *
 * Source: frameworks/base/core/java/android/accounts/AccountAuthenticatorResponse.java
 */
public class AccountAuthenticatorResponse implements Parcelable {

    private final IBinder mResponseBinder;

    /** AOSP's public constructor takes a Parcel and reads the response binder. */
    public AccountAuthenticatorResponse(Parcel parcel) {
        mResponseBinder = parcel.readStrongBinder();
        mSink = null;
    }

    /**
     * Not in AOSP's public API (AOSP's other constructor takes the AIDL
     * interface, which does not exist here). Kept because framework code and
     * tests construct a detached response.
     */
    public AccountAuthenticatorResponse() { mResponseBinder = null; mSink = null; }

    /** Where an in-process authenticator session (AccountManager driving the app's OWN
     *  authenticator, see AccountManager.removeAccount) receives the authenticator's answer.
     *  It stands in for AOSP's IAccountAuthenticatorResponse binder into the
     *  AccountManagerService Session, whose onResult/onError are what these forward to. */
    interface Sink {
        void onResult(Bundle result);
        void onError(int errorCode, String errorMessage);
    }

    private final Sink mSink;

    AccountAuthenticatorResponse(Sink sink) { mResponseBinder = null; mSink = sink; }

    public void onResult(Bundle result) { if (mSink != null) mSink.onResult(result); }

    public void onRequestContinued() { }

    public void onError(int errorCode, String errorMessage) {
        if (mSink != null) mSink.onError(errorCode, errorMessage);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(mResponseBinder);
    }

    public static final Creator<AccountAuthenticatorResponse> CREATOR =
            new Creator<AccountAuthenticatorResponse>() {
        @Override public AccountAuthenticatorResponse createFromParcel(Parcel source) {
            return new AccountAuthenticatorResponse(source);
        }
        @Override public AccountAuthenticatorResponse[] newArray(int size) {
            return new AccountAuthenticatorResponse[size];
        }
    };
}
