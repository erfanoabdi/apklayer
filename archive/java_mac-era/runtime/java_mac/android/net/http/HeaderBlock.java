package android.net.http;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Response headers as an ordered list of name/value entries.
 *  Source: frameworks/base .../net/http/HeaderBlock.java (android.net.http Cronet) */
public abstract class HeaderBlock {
    public abstract List<Map.Entry<String, String>> getAsList();
    public abstract Map<String, List<String>> getAsMap();

    static HeaderBlock from(Map<String, List<String>> headers) {
        final List<Map.Entry<String,String>> list = new ArrayList<>();
        final Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() == null) continue;
                map.put(e.getKey(), e.getValue());
                for (String v : e.getValue()) {
                    list.add(new AbstractMap.SimpleEntry<>(e.getKey(), v));
                }
            }
        }
        return new HeaderBlock() {
            @Override public List<Map.Entry<String,String>> getAsList() { return list; }
            @Override public Map<String, List<String>> getAsMap() { return map; }
        };
    }
}
