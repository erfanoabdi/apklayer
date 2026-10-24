package com.google.firebase.perf;

import com.google.firebase.perf.metrics.HttpMetric;
import com.google.firebase.perf.metrics.Trace;

// com.google.firebase.perf.FirebasePerformance -- entry point for Firebase
// Performance Monitoring. On a de-Googled device there is no upload backend, so
// traces/HTTP metrics are created and run locally but report nothing (NEVER
// fabricates a metric). The collection toggle is honored locally. Mirrors the
// published static + instance surface.
// Ref: firebase.google.com/docs/reference/android/.../perf/FirebasePerformance
public class FirebasePerformance {

    private static final FirebasePerformance INSTANCE = new FirebasePerformance();

    private boolean performanceCollectionEnabled = true;

    private FirebasePerformance() {}

    public static FirebasePerformance getInstance() { return INSTANCE; }

    public Trace newTrace(String traceName) { return new Trace(traceName); }

    public HttpMetric newHttpMetric(String url, String httpMethod) {
        return new HttpMetric(url, httpMethod);
    }

    public HttpMetric newHttpMetric(java.net.URL url, String httpMethod) {
        return new HttpMetric(url == null ? null : url.toString(), httpMethod);
    }

    public void setPerformanceCollectionEnabled(boolean enable) {
        this.performanceCollectionEnabled = enable;
    }
    public boolean isPerformanceCollectionEnabled() { return performanceCollectionEnabled; }

    // Static convenience matching the published helper used by some apps.
    public static Trace startTrace(String traceName) {
        Trace t = new Trace(traceName);
        t.start();
        return t;
    }
}
