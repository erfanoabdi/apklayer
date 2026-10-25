// android.net.LocalSocketAddress
//
// Names a Unix-domain (local) socket endpoint. Android supports three
// namespaces; only FILESYSTEM maps onto a JDK UnixDomainSocketAddress, so the
// other two are recorded but only meaningfully usable for FILESYSTEM. Pure
// value type. Mirrors
// frameworks/base/core/java/android/net/LocalSocketAddress.java.
package android.net;

public class LocalSocketAddress {

    public enum Namespace {
        ABSTRACT,
        RESERVED,
        FILESYSTEM
    }

    private final String mName;
    private final Namespace mNamespace;

    public LocalSocketAddress(String name, Namespace namespace) {
        mName = name;
        mNamespace = namespace;
    }

    public LocalSocketAddress(String name) {
        this(name, Namespace.ABSTRACT);
    }

    public String getName() { return mName; }
    public Namespace getNamespace() { return mNamespace; }
}
