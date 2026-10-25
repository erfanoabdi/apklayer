package org.apache.http.protocol;
public interface HttpContext {
    String RESERVED_PREFIX = "http.";
    Object getAttribute(String id);
    void   setAttribute(String id, Object obj);
    Object removeAttribute(String id);
}
