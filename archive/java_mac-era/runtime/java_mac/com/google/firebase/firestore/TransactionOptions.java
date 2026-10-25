package com.google.firebase.firestore;

import java.util.concurrent.Executor;

// com.google.firebase.firestore.TransactionOptions -- max-attempts + executor
// for runTransaction(). Faithful value/builder type; on the offline shim a
// transaction never contends so the attempt count is unused, but apps build
// these and pass them, so the surface must match exactly.
public final class TransactionOptions {

    // The published default maximum number of transaction attempts.
    static final int DEFAULT_MAX_ATTEMPTS_COUNT = 5;

    private final int maxAttempts;
    private final Executor executor;

    private TransactionOptions(int maxAttempts, Executor executor) {
        this.maxAttempts = maxAttempts;
        this.executor = executor;
    }

    public int getMaxAttempts() { return maxAttempts; }

    public Executor getExecutor() { return executor; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionOptions)) return false;
        TransactionOptions t = (TransactionOptions) o;
        return maxAttempts == t.maxAttempts
            && (executor == null ? t.executor == null : executor.equals(t.executor));
    }

    @Override
    public int hashCode() {
        return 31 * maxAttempts + (executor != null ? executor.hashCode() : 0);
    }

    public static final class Builder {
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS_COUNT;
        private Executor executor;

        public Builder() {}

        public Builder setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("Max attempts must be at least 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder setExecutor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public int getMaxAttempts() { return maxAttempts; }

        public Executor getExecutor() { return executor; }

        public TransactionOptions build() {
            return new TransactionOptions(maxAttempts, executor);
        }
    }
}
