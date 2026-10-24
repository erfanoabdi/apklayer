package org.apache.http;
public final class HttpVersion extends ProtocolVersion {
    public static final String HTTP = "HTTP";
    public static final HttpVersion HTTP_0_9 = new HttpVersion(0, 9);
    public static final HttpVersion HTTP_1_0 = new HttpVersion(1, 0);
    public static final HttpVersion HTTP_1_1 = new HttpVersion(1, 1);
    public HttpVersion(int major, int minor) { super(HTTP, major, minor); }
    @Override public ProtocolVersion forVersion(int major, int minor) { return new HttpVersion(major, minor); }
}
