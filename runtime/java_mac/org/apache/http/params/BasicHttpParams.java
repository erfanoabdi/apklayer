package org.apache.http.params;
import java.util.HashMap;
import java.util.Map;
public class BasicHttpParams extends AbstractHttpParams {
    private final Map<String, Object> map = new HashMap<>();
    public BasicHttpParams() {}
    @Override public Object getParameter(String name) { return map.get(name); }
    @Override public HttpParams setParameter(String name, Object value) { map.put(name, value); return this; }
    @Override public HttpParams copy() { BasicHttpParams c = new BasicHttpParams(); c.map.putAll(map); return c; }
    @Override public boolean removeParameter(String name) { return map.remove(name) != null; }
    @Override public long getLongParameter(String name, long def) { Object v = map.get(name); return v instanceof Long ? (Long) v : def; }
    @Override public HttpParams setLongParameter(String name, long v) { map.put(name, v); return this; }
    @Override public int  getIntParameter(String name, int def)  { Object v = map.get(name); return v instanceof Integer ? (Integer) v : def; }
    @Override public HttpParams setIntParameter(String name, int v) { map.put(name, v); return this; }
    @Override public double getDoubleParameter(String name, double def) { Object v = map.get(name); return v instanceof Double ? (Double) v : def; }
    @Override public HttpParams setDoubleParameter(String name, double v) { map.put(name, v); return this; }
    @Override public boolean getBooleanParameter(String name, boolean def) { Object v = map.get(name); return v instanceof Boolean ? (Boolean) v : def; }
    @Override public HttpParams setBooleanParameter(String name, boolean v) { map.put(name, v); return this; }
    @Override public boolean isParameterTrue(String name)  { return getBooleanParameter(name, false); }
    @Override public boolean isParameterFalse(String name) { return !getBooleanParameter(name, false); }
}
