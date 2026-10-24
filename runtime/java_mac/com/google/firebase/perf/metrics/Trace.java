package com.google.firebase.perf.metrics;

// com.google.firebase.perf.metrics.Trace -- a custom performance trace. Firebase
// Performance Monitoring uploads timing/metric data to Google; on a de-Googled
// device there is no upload backend, so start/stop/putMetric are local no-ops
// (NEVER reports a fabricated metric anywhere). The type and methods exist so
// apps that wrap code in a Trace link and run. Mirrors the published surface.
// Ref: firebase.google.com/docs/reference/android/com/google/firebase/perf/metrics/Trace
public class Trace {

    private final String name;
    private final java.util.HashMap<String, Long> metrics = new java.util.HashMap<String, Long>();
    private final java.util.HashMap<String, String> attributes = new java.util.HashMap<String, String>();

    public Trace(String name) { this.name = name; }

    public void start() {}
    public void stop() {}

    public void incrementMetric(String metricName, long incrementBy) {
        Long cur = metrics.get(metricName);
        metrics.put(metricName, (cur == null ? 0L : cur) + incrementBy);
    }
    public void putMetric(String metricName, long value) { metrics.put(metricName, value); }
    public long getLongMetric(String metricName) {
        Long v = metrics.get(metricName);
        return v == null ? 0L : v;
    }

    public void putAttribute(String attribute, String value) { attributes.put(attribute, value); }
    public void removeAttribute(String attribute) { attributes.remove(attribute); }
    public String getAttribute(String attribute) { return attributes.get(attribute); }
    public java.util.Map<String, String> getAttributes() {
        return new java.util.HashMap<String, String>(attributes);
    }

    // Static factory mirroring Trace.create(name) used by some call sites.
    public static Trace create(String name) { return new Trace(name); }

    public String getName() { return name; }
}
