package org.apache.http.client.utils;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.HttpEntity;
public final class URLEncodedUtils {
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private URLEncodedUtils() {}
    public static String format(List<? extends NameValuePair> parameters, String encoding) {
        StringBuilder sb = new StringBuilder();
        for (NameValuePair pair : parameters) {
            try {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(pair.getName(), encoding));
                sb.append('=');
                sb.append(URLEncoder.encode(pair.getValue() == null ? "" : pair.getValue(), encoding));
            } catch (UnsupportedEncodingException e) { throw new RuntimeException(e); }
        }
        return sb.toString();
    }
    public static List<NameValuePair> parse(String s, String encoding) { return new ArrayList<>(); }
    public static List<NameValuePair> parse(HttpEntity entity)         { return new ArrayList<>(); }
    public static List<NameValuePair> parse(java.net.URI uri, String encoding) { return new ArrayList<>(); }
}
