package org.apache.http;
public class ProtocolVersion implements Cloneable {
    protected final String protocol;
    protected final int major;
    protected final int minor;
    public ProtocolVersion(String protocol, int major, int minor) {
        this.protocol = protocol; this.major = major; this.minor = minor;
    }
    public final String getProtocol() { return protocol; }
    public final int getMajor() { return major; }
    public final int getMinor() { return minor; }
    public ProtocolVersion forVersion(int major, int minor) { return new ProtocolVersion(protocol, major, minor); }
    public final int hashCode() { return (protocol.hashCode() * 100003) ^ (major << 16) ^ minor; }
    public boolean equals(Object obj) {
        if (!(obj instanceof ProtocolVersion p)) return false;
        return protocol.equals(p.protocol) && major == p.major && minor == p.minor;
    }
    public String toString() { return protocol + "/" + major + "." + minor; }
    @Override public Object clone() throws CloneNotSupportedException { return super.clone(); }
    public boolean isComparable(ProtocolVersion that) { return protocol.equals(that.protocol); }
    public int compareToVersion(ProtocolVersion that) {
        int d = major - that.major; if (d != 0) return d;
        return minor - that.minor;
    }
    public boolean greaterEquals(ProtocolVersion v) { return isComparable(v) && compareToVersion(v) >= 0; }
    public boolean lessEquals(ProtocolVersion v)    { return isComparable(v) && compareToVersion(v) <= 0; }
}
