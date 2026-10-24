package org.apache.http.message;
import org.apache.http.NameValuePair;
public class BasicNameValuePair implements NameValuePair {
    private final String name;
    private final String value;
    public BasicNameValuePair(String name, String value) {
        this.name = name; this.value = value;
    }
    @Override public String getName()  { return name; }
    @Override public String getValue() { return value; }
    @Override public String toString() { return name + "=" + value; }
}
