package com.android.internal.util;

// apklayer patch: minimal stand-in for AOSP Preconditions (upstream pulls in Build,
// TextUtils and errorprone). Signatures mirror upstream for the subset our seed uses.

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public final class Preconditions {
    private Preconditions() {}

    public static void checkArgument(boolean expression) {
        if (!expression) throw new IllegalArgumentException();
    }
    public static void checkArgument(boolean expression, Object errorMessage) {
        if (!expression) throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    public static void checkArgument(boolean expression, String template, Object... args) {
        if (!expression) throw new IllegalArgumentException(format(template, args));
    }
    public static int checkArgumentIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                    "Index out of bound: " + index + " (size is " + size + ")");
        }
        return index;
    }
    public static void checkArgumentIndexes(int from, int to, int size) {
        if (from < 0 || to > size || from > to) {
            throw new IndexOutOfBoundsException(
                    "from=" + from + ", to=" + to + ", size=" + size);
        }
    }
    public static void checkState(boolean expression) {
        if (!expression) throw new IllegalStateException();
    }
    public static void checkState(boolean expression, Object errorMessage) {
        if (!expression) throw new IllegalStateException(String.valueOf(errorMessage));
    }
    public static void checkState(boolean expression, String template, Object... args) {
        if (!expression) throw new IllegalStateException(format(template, args));
    }
    public static int checkElementIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " for size " + size);
        }
        return index;
    }
    public static void checkOffsetAndCount(long arrayLength, long offset, long count) {
        if ((offset | count) < 0 || offset + count > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset=" + offset + ", count=" + count + ", length=" + arrayLength);
        }
    }
    public static <T> T checkNotNull(T reference) {
        if (reference == null) throw new NullPointerException();
        return reference;
    }
    public static void checkCollectionElementsAreNonNull(Collection<?> c) {
        for (Object o : c) Objects.requireNonNull(o);
    }
    public static void checkArrayElementsAreNonNull(Object[] a) {
        for (Object o : a) Objects.requireNonNull(o);
    }
    public static int checkArgumentNonnegative(int value) {
        if (value < 0) throw new IllegalArgumentException();
        return value;
    }
    public static int checkArgumentNonnegative(int value, String errorMessage) {
        if (value < 0) throw new IllegalArgumentException(errorMessage);
        return value;
    }
    private static String format(String template, Object... args) {
        if (template == null) return "NULL";
        StringBuilder sb = new StringBuilder();
        int arg = 0, i = 0;
        while (i < template.length()) {
            int p = template.indexOf("%s", i);
            if (p < 0 || arg >= args.length) { sb.append(template, i, template.length()); break; }
            sb.append(template, i, p).append(args[arg++]);
            i = p + 2;
        }
        return sb.toString();
    }
}
