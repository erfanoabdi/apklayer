package com.google.firebase.perf.metrics;

// com.google.firebase.perf.metrics.HttpMetric -- a network-request performance
// metric. No upload backend on a de-Googled device, so start/stop and all setters
// are local no-ops (NEVER reports a fabricated metric). The type exists so apps
// that instrument HTTP calls link and run. Mirrors the published surface.
// Ref: firebase.google.com/docs/reference/android/.../perf/metrics/HttpMetric
public class HttpMetric {

    private final java.util.HashMap<String, String> attributes = new java.util.HashMap<String, String>();

    public HttpMetric(String url, String httpMethod) {}

    public void start() {}
    public void stop() {}
    public void setHttpResponseCode(int responseCode) {}
    public void setRequestPayloadSize(long bytes) {}
    public void setResponsePayloadSize(long bytes) {}
    public void setResponseContentType(String contentType) {}

    public void putAttribute(String attribute, String value) { attributes.put(attribute, value); }
    public void removeAttribute(String attribute) { attributes.remove(attribute); }
    public String getAttribute(String attribute) { return attributes.get(attribute); }
    public java.util.Map<String, String> getAttributes() {
        return new java.util.HashMap<String, String>(attributes);
    }
}
