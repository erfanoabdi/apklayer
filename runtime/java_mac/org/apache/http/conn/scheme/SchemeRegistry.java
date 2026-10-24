package org.apache.http.conn.scheme;
import java.util.HashMap;
import java.util.Map;
public final class SchemeRegistry {
    private final Map<String, Scheme> map = new HashMap<>();
    public SchemeRegistry() {}
    public synchronized Scheme register(Scheme scheme) {
        Scheme prev = map.put(scheme.getName(), scheme);
        return prev;
    }
    public synchronized Scheme unregister(String name) { return map.remove(name); }
    public synchronized Scheme get(String name)        { return map.get(name); }
    public synchronized Scheme getScheme(String name)  { return map.get(name); }
}
