// android.security.identity.ResultData -- the entries returned by
// IdentityCredential.getEntries(), with a per-entry access-control status.
//
// Abstract on AOSP and unreachable here (it is only produced by a live
// credential, and our store returns null -- see IdentityCredentialStore). The
// STATUS_* constants are the part apps actually read, so they carry AOSP's
// exact values.
//
// Mirrors identity/java/android/security/identity/ResultData.java
// (android11-release, API 30).
package android.security.identity;

import java.util.Collection;

public abstract class ResultData {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_SUCH_ENTRY = 1;
    public static final int STATUS_NOT_REQUESTED = 2;
    public static final int STATUS_NOT_IN_REQUEST_MESSAGE = 3;
    public static final int STATUS_USER_AUTHENTICATION_FAILED = 4;
    public static final int STATUS_READER_AUTHENTICATION_FAILED = 5;
    public static final int STATUS_NO_ACCESS_CONTROL_PROFILES = 6;

    protected ResultData() {}

    public abstract byte[] getAuthenticatedData();

    public abstract byte[] getMessageAuthenticationCode();

    public abstract byte[] getStaticAuthenticationData();

    public abstract Collection<String> getNamespaces();

    public abstract Collection<String> getEntryNames(String namespaceName);

    /** The subset of getEntryNames() that was actually RETRIEVED (an entry can
     *  be requested and then withheld by its access-control profile). Abstract
     *  on AOSP (ResultData.java:179-180), so abstract here. */
    public abstract Collection<String> getRetrievedEntryNames(String namespaceName);

    public abstract int getStatus(String namespaceName, String name);

    public abstract byte[] getEntry(String namespaceName, String name);
}
