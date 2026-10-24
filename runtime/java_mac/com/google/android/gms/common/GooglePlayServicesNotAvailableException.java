// com.google.android.gms.common.GooglePlayServicesNotAvailableException
//
// The checked exception the real play-services-basement client library throws
// from GoogleApiAvailabilityLight.verifyGooglePlayServicesIsAvailable(...) (and
// GoogleApiAvailability.ensurePlayServicesAvailable(...)) when Google Play
// Services is missing and cannot be made available. On a device with no GMS at
// all -- which is what Mac.apk is -- "not available, nothing to repair" is the
// honest terminal, so this (NotAvailable), not GooglePlayServicesRepairable, is
// the type thrown. Apps wrap those calls in try/catch for exactly this class; if
// the class were absent the throw would surface as NoClassDefFoundError instead.
//
// Mirrors the public GMS API surface: a final int errorCode + a ConnectionResult
// error code, extending Exception.
package com.google.android.gms.common;

public class GooglePlayServicesNotAvailableException extends Exception {
    public final int errorCode;

    public GooglePlayServicesNotAvailableException(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        return "GooglePlayServicesNotAvailableException: "
                + GoogleApiAvailabilityLight.getInstance().getErrorString(errorCode);
    }
}
