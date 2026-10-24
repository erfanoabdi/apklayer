// android.net.MailTo
//
// Parses a "mailto:" URI into to/cc/subject/body fields. Pure string parsing,
// no hardware -- implemented faithfully against AOSP's behavior. Mirrors
// frameworks/base/core/java/android/net/MailTo.java.
package android.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class MailTo {

    public static final String MAILTO_SCHEME = "mailto:";

    private static final String TO = "to";
    private static final String BODY = "body";
    private static final String CC = "cc";
    private static final String SUBJECT = "subject";

    private final HashMap<String, String> mHeaders = new HashMap<>();

    private MailTo() {}

    public static boolean isMailTo(String url) {
        return url != null && url.startsWith(MAILTO_SCHEME);
    }

    public static MailTo parse(String url) throws ParseException {
        if (url == null) throw new ParseException("Null url");
        if (!isMailTo(url)) throw new ParseException("Not a mailto scheme");

        MailTo m = new MailTo();
        // Strip scheme, split address from the optional ?query.
        String noScheme = url.substring(MAILTO_SCHEME.length());
        String address;
        String query = null;
        int q = noScheme.indexOf('?');
        if (q >= 0) {
            address = noScheme.substring(0, q);
            query = noScheme.substring(q + 1);
        } else {
            address = noScheme;
        }
        if (!address.isEmpty()) {
            m.mHeaders.put(TO, decode(address));
        }
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String key = decode(pair.substring(0, eq)).toLowerCase();
                String value = decode(pair.substring(eq + 1));
                if (key.equals(TO) && m.mHeaders.containsKey(TO)) {
                    m.mHeaders.put(TO, m.mHeaders.get(TO) + ", " + value);
                } else {
                    m.mHeaders.put(key, value);
                }
            }
        }
        return m;
    }

    public String getTo() { return mHeaders.get(TO); }
    public String getCc() { return mHeaders.get(CC); }
    public String getSubject() { return mHeaders.get(SUBJECT); }
    public String getBody() { return mHeaders.get(BODY); }
    public Map<String, String> getHeaders() { return mHeaders; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder(MAILTO_SCHEME);
        sb.append('?');
        for (Map.Entry<String, String> e : mHeaders.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('&');
        }
        return sb.toString();
    }

    private static String decode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); }
        catch (UnsupportedEncodingException e) { return s; }
    }
}
