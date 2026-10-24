package org.apache.http.message;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.ParseException;
public class BasicHeader implements Header {
    private final String name, value;
    public BasicHeader(String name, String value) { this.name = name; this.value = value; }
    @Override public String getName() { return name; }
    @Override public String getValue() { return value; }
    @Override public HeaderElement[] getElements() throws ParseException { return new HeaderElement[0]; }
    @Override public String toString() { return name + ": " + value; }
}
