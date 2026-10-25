// android.net.ipsec.ike.IkeTunnelConnectionParams -- the pair of IKE session
// and tunnel-mode child session parameters an Ikev2VpnProfile can be built
// from.
//
// The class ITSELF is a real, faithful value type (AOSP's is a two-field
// holder with two accessors and value equality, and so is this one). What is
// absent is everything AROUND it: see IkeSessionParams in this package -- the
// android.net.ipsec.ike library is not implemented, so neither of the two
// parameter objects this holds can actually be constructed, and no app can
// reach this constructor with real arguments.
//
// Mirrors packages/modules/IPsec/src/java/android/net/ipsec/ike/
// IkeTunnelConnectionParams.java.
package android.net.ipsec.ike;

import java.util.Objects;

public final class IkeTunnelConnectionParams {

    private final IkeSessionParams mIkeParams;
    private final TunnelModeChildSessionParams mChildParams;

    public IkeTunnelConnectionParams(IkeSessionParams ikeParams,
            TunnelModeChildSessionParams childParams) {
        Objects.requireNonNull(ikeParams, "ikeParams was null");
        Objects.requireNonNull(childParams, "childParams was null");
        mIkeParams = ikeParams;
        mChildParams = childParams;
    }

    public IkeSessionParams getIkeSessionParams() { return mIkeParams; }

    public TunnelModeChildSessionParams getTunnelModeChildSessionParams() { return mChildParams; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IkeTunnelConnectionParams)) return false;
        IkeTunnelConnectionParams other = (IkeTunnelConnectionParams) o;
        return Objects.equals(mIkeParams, other.mIkeParams)
                && Objects.equals(mChildParams, other.mChildParams);
    }

    @Override public int hashCode() {
        return Objects.hash(mIkeParams, mChildParams);
    }
}
