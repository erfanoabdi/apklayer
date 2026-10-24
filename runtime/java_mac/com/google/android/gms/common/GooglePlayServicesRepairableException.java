// com.google.android.gms.common.GooglePlayServicesRepairableException
//
// The sibling checked exception the real play-services-basement client library
// can throw when Google Play Services is present but stale/disabled and could be
// "repaired" by sending the user to the Play Store. Mac.apk is a device with NO
// GMS at all, so the honest thrown type is GooglePlayServicesNotAvailableException
// (nothing to repair, no Play Store); this class exists only so that any app-side
// `catch (GooglePlayServicesRepairableException)` / instanceof resolves to a real
// class instead of NoClassDefFoundError at link time.
//
// The real class extends UserRecoverableException; Mac.apk has no such base type,
// so it extends Exception directly and preserves the observable public surface
// (getConnectionStatusCode + getIntent).
package com.google.android.gms.common;

import android.content.Intent;

public class GooglePlayServicesRepairableException extends Exception {
    private final int connectionStatusCode;
    private final Intent intent;

    public GooglePlayServicesRepairableException(int connectionStatusCode,
                                                 String message, Intent intent) {
        super(message);
        this.connectionStatusCode = connectionStatusCode;
        this.intent = intent;
    }

    public int getConnectionStatusCode() {
        return connectionStatusCode;
    }

    public Intent getIntent() {
        return new Intent(intent);
    }
}
