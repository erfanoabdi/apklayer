package com.google.firebase.firestore;

import java.util.Arrays;
import java.util.List;

// com.google.firebase.firestore.FieldValue -- sentinel values placed into the
// data map of a set()/update() to request a server-side transform (timestamp,
// numeric increment, array union/remove, delete). On a real backend these are
// resolved by the server; on our offline-only shim writes complete locally and
// the sentinels are simply stored opaquely (never resolved against a backend),
// which matches the SDK's offline semantics: the write is acknowledged locally
// and the transform would resolve when connectivity returns. Apps construct
// these as opaque tokens and pass them straight into a write map, so the
// factory surface is the entire app-facing contract.
public abstract class FieldValue {

    // Hidden ctor: the SDK never lets apps subclass this directly.
    FieldValue() {}

    abstract String getMethodName();

    // ---- Concrete sentinel implementations (package-private) --------------

    static final class DeleteFieldValue extends FieldValue {
        private static final DeleteFieldValue INSTANCE = new DeleteFieldValue();
        @Override String getMethodName() { return "FieldValue.delete"; }
    }

    static final class ServerTimestampFieldValue extends FieldValue {
        private static final ServerTimestampFieldValue INSTANCE = new ServerTimestampFieldValue();
        @Override String getMethodName() { return "FieldValue.serverTimestamp"; }
    }

    static final class ArrayUnionFieldValue extends FieldValue {
        final List<Object> elements;
        ArrayUnionFieldValue(List<Object> elements) { this.elements = elements; }
        @Override String getMethodName() { return "FieldValue.arrayUnion"; }
    }

    static final class ArrayRemoveFieldValue extends FieldValue {
        final List<Object> elements;
        ArrayRemoveFieldValue(List<Object> elements) { this.elements = elements; }
        @Override String getMethodName() { return "FieldValue.arrayRemove"; }
    }

    static final class NumericIncrementFieldValue extends FieldValue {
        final Number operand;
        NumericIncrementFieldValue(Number operand) { this.operand = operand; }
        @Override String getMethodName() { return "FieldValue.increment"; }
    }

    // ---- Public factory surface (exact published signatures) --------------

    public static FieldValue delete() {
        return DeleteFieldValue.INSTANCE;
    }

    public static FieldValue serverTimestamp() {
        return ServerTimestampFieldValue.INSTANCE;
    }

    public static FieldValue arrayUnion(Object... elements) {
        return new ArrayUnionFieldValue(Arrays.asList(elements));
    }

    public static FieldValue arrayRemove(Object... elements) {
        return new ArrayRemoveFieldValue(Arrays.asList(elements));
    }

    public static FieldValue increment(long l) {
        return new NumericIncrementFieldValue(Long.valueOf(l));
    }

    public static FieldValue increment(double l) {
        return new NumericIncrementFieldValue(Double.valueOf(l));
    }
}
