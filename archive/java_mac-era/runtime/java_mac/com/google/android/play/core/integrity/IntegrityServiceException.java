package com.google.android.play.core.integrity;

// com.google.android.play.core.integrity.IntegrityServiceException -- the
// exception a failed integrity-token Task carries. Apps catch it and inspect
// getErrorCode() to choose a degrade path. Per project policy we NEVER forge an
// attestation token, so requestIntegrityToken() always fails with this carrying
// an IntegrityErrorCode. Extends RuntimeException to match the published Play
// Integrity type (it is not a checked exception). The type and getErrorCode()
// accessor must be exact so app catch-blocks work.
public class IntegrityServiceException extends RuntimeException {
    private final int errorCode;

    public IntegrityServiceException(int errorCode, Throwable cause) {
        super("Integrity API error (" + errorCode + ").", cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() { return errorCode; }
}
