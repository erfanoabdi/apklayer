package org.apache.http.message;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
public class BasicStatusLine implements StatusLine {
    private final ProtocolVersion protoVersion;
    private final int statusCode;
    private final String reasonPhrase;
    public BasicStatusLine(ProtocolVersion version, int statusCode, String reasonPhrase) {
        this.protoVersion = version; this.statusCode = statusCode; this.reasonPhrase = reasonPhrase;
    }
    @Override public ProtocolVersion getProtocolVersion() { return protoVersion; }
    @Override public int getStatusCode() { return statusCode; }
    @Override public String getReasonPhrase() { return reasonPhrase; }
    @Override public String toString() {
        return protoVersion + " " + statusCode + (reasonPhrase != null ? " " + reasonPhrase : "");
    }
}
