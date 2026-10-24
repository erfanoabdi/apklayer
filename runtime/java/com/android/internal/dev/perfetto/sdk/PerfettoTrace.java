package com.android.internal.dev.perfetto.sdk;

// apklayer patch: generated Perfetto SDK stand-in; no-op fluent builder.
public final class PerfettoTrace {
    public static final class Builder {
        public Builder beginProto() { return this; }
        public Builder endProto() { return this; }
        public Builder beginNested(long fieldId) { return this; }
        public Builder endNested() { return this; }
        public Builder addField(long fieldId, String value) { return this; }
        public Builder addField(long fieldId, long value) { return this; }
        public Builder setTerminatingFlow(long id) { return this; }
        public void emit() {}
    }
    private static final Builder B = new Builder();
    public static Builder begin(Object category, String name) { return B; }
    public static Builder end(Object category) { return B; }
}
