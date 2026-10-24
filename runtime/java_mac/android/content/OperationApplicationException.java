// android.content.OperationApplicationException -- thrown when a
// ContentProviderOperation cannot be applied: an insert that returned no Uri,
// a row count that did not match withExpectedCount, or an assert query whose
// values did not match.
//
// Why Mac.apk needs it: Telegram and Signal reference it. It is a CHECKED
// exception on applyBatch's signature, so its absence is not merely a missing
// type -- the catch clause in the caller fails to resolve and the whole
// containing method dies at link time even on the path where nothing throws.
//
// AOSP source mirrored: frameworks/base/core/java/android/content/
// OperationApplicationException.java (android11-release), all six constructors
// and getNumSuccessfulYieldPoints.
package android.content;

/**
 * Thrown when an application of a ContentProviderOperation fails due to the
 * specified constraints.
 */
public class OperationApplicationException extends Exception {
    private final int mNumSuccessfulYieldPoints;

    public OperationApplicationException() {
        super();
        mNumSuccessfulYieldPoints = 0;
    }

    public OperationApplicationException(String message) {
        super(message);
        mNumSuccessfulYieldPoints = 0;
    }

    public OperationApplicationException(String message, Throwable cause) {
        super(message, cause);
        mNumSuccessfulYieldPoints = 0;
    }

    public OperationApplicationException(Throwable cause) {
        super(cause);
        mNumSuccessfulYieldPoints = 0;
    }

    public OperationApplicationException(int numSuccessfulYieldPoints) {
        super();
        mNumSuccessfulYieldPoints = numSuccessfulYieldPoints;
    }

    public OperationApplicationException(String message, int numSuccessfulYieldPoints) {
        super(message);
        mNumSuccessfulYieldPoints = numSuccessfulYieldPoints;
    }

    /** How many yield points the batch got through before failing. Callers use
     *  it to decide how much of a partially-applied batch to undo. */
    public int getNumSuccessfulYieldPoints() {
        return mNumSuccessfulYieldPoints;
    }
}
