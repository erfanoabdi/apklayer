package android.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A request for a font from a downloadable-font provider.
 *
 * Pure immutable value type -- it names a provider authority/package, a query,
 * and the certificate list the caller expects that provider to be signed with.
 * Nothing here needs the provider to exist.
 *
 * @deprecated AOSP deprecated the platform FontsContract in API 29 in favour of
 * androidx.core.provider; still shipped, and still what older apps link against.
 *
 * Source: frameworks/base/core/java/android/provider/FontRequest.java
 */
@Deprecated
public final class FontRequest {

    private final String mProviderAuthority;
    private final String mProviderPackage;
    private final String mQuery;
    private final List<List<byte[]>> mCertificates;

    public FontRequest(String providerAuthority, String providerPackage, String query) {
        mProviderAuthority = java.util.Objects.requireNonNull(providerAuthority);
        mProviderPackage = java.util.Objects.requireNonNull(providerPackage);
        mQuery = java.util.Objects.requireNonNull(query);
        mCertificates = Collections.emptyList();
    }

    public FontRequest(String providerAuthority, String providerPackage, String query,
            List<List<byte[]>> certificates) {
        mProviderAuthority = java.util.Objects.requireNonNull(providerAuthority);
        mProviderPackage = java.util.Objects.requireNonNull(providerPackage);
        mQuery = java.util.Objects.requireNonNull(query);
        mCertificates = java.util.Objects.requireNonNull(certificates);
    }

    public String getProviderAuthority() { return mProviderAuthority; }

    public String getProviderPackage() { return mProviderPackage; }

    public String getQuery() { return mQuery; }

    public List<List<byte[]>> getCertificates() { return mCertificates; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("FontRequest {mProviderAuthority: ")
                .append(mProviderAuthority)
                .append(", mProviderPackage: ").append(mProviderPackage)
                .append(", mQuery: ").append(mQuery)
                .append("}");
        return sb.toString();
    }

    /** @hide Helper the framework uses when logging a request. */
    List<List<byte[]>> copyCertificates() { return new ArrayList<>(mCertificates); }
}
