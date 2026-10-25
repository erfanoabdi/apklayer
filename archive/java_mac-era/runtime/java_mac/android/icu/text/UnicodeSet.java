// android.icu.text.UnicodeSet -- a mutable set of Unicode code points and
// strings. ICU4J:
//   developer.android.com/reference/android/icu/text/UnicodeSet
//   public class UnicodeSet extends UnicodeFilter implements Iterable<String>
//
// Used for character-class membership tests (e.g. "is this char in [a-z0-9]").
// The JDK has no public equivalent, so this is a REAL implementation backed by
// a sorted set of code points. We support the constructors + add/remove/
// contains/size + the common bracketed applyPattern grammar ([abc], [a-z],
// [^...] negation over the BMP) that apps build sets from. Property-name
// patterns (\p{L}) and the full ICU range syntax are not parsed; an
// unrecognized pattern yields an empty set rather than a wrong membership
// answer. The string-valued elements ICU allows ({ "ch" }) are supported via
// addAll(String...) but the bracket parser only emits single code points.
package android.icu.text;

import java.util.Iterator;
import java.util.TreeSet;

public class UnicodeSet extends UnicodeFilter implements Iterable<String> {
    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 0x10FFFF;

    private final TreeSet<Integer> codePoints = new TreeSet<>();
    private final TreeSet<String> strings = new TreeSet<>();

    public UnicodeSet() {}

    public UnicodeSet(String pattern) {
        applyPattern(pattern);
    }

    public UnicodeSet(int start, int end) {
        add(start, end);
    }

    public UnicodeSet(UnicodeSet other) {
        codePoints.addAll(other.codePoints);
        strings.addAll(other.strings);
    }

    public UnicodeSet add(int c) {
        codePoints.add(c);
        return this;
    }

    public UnicodeSet add(int start, int end) {
        for (int c = start; c <= end; c++) codePoints.add(c);
        return this;
    }

    public UnicodeSet add(CharSequence s) {
        if (s == null) return this;
        if (s.length() == 1 || Character.charCount(Character.codePointAt(s, 0)) == s.length()) {
            codePoints.add(Character.codePointAt(s, 0));
        } else {
            strings.add(s.toString());
        }
        return this;
    }

    public UnicodeSet addAll(CharSequence s) {
        if (s == null) return this;
        s.codePoints().forEach(codePoints::add);
        return this;
    }

    public UnicodeSet addAll(UnicodeSet other) {
        codePoints.addAll(other.codePoints);
        strings.addAll(other.strings);
        return this;
    }

    public UnicodeSet remove(int c) {
        codePoints.remove(c);
        return this;
    }

    public UnicodeSet clear() {
        codePoints.clear();
        strings.clear();
        return this;
    }

    @Override
    public boolean contains(int c) {
        return codePoints.contains(c);
    }

    public boolean contains(int start, int end) {
        for (int c = start; c <= end; c++) {
            if (!codePoints.contains(c)) return false;
        }
        return true;
    }

    public boolean contains(CharSequence s) {
        if (s == null) return false;
        if (strings.contains(s.toString())) return true;
        return s.codePoints().allMatch(codePoints::contains);
    }

    public boolean containsAll(CharSequence s) {
        return contains(s);
    }

    public boolean containsNone(CharSequence s) {
        if (s == null) return true;
        return s.codePoints().noneMatch(codePoints::contains);
    }

    public boolean containsSome(CharSequence s) {
        return !containsNone(s);
    }

    public int size() {
        return codePoints.size() + strings.size();
    }

    public boolean isEmpty() {
        return codePoints.isEmpty() && strings.isEmpty();
    }

    public UnicodeSet complement() {
        TreeSet<Integer> inverse = new TreeSet<>();
        for (int c = MIN_VALUE; c <= MAX_VALUE; c++) {
            if (!codePoints.contains(c)) inverse.add(c);
        }
        codePoints.clear();
        codePoints.addAll(inverse);
        return this;
    }

    public UnicodeSet freeze() { return this; }
    public boolean isFrozen() { return false; }

    public final UnicodeSet applyPattern(String pattern) {
        codePoints.clear();
        strings.clear();
        if (pattern == null) return this;
        String p = pattern.trim();
        if (!p.startsWith("[") || !p.endsWith("]")) return this;
        String body = p.substring(1, p.length() - 1);
        boolean negate = false;
        if (body.startsWith("^")) { negate = true; body = body.substring(1); }
        int idx = 0;
        while (idx < body.length()) {
            char ch = body.charAt(idx);
            if (ch == ' ') { idx++; continue; }
            // escaped char
            if (ch == '\\' && idx + 1 < body.length()) {
                codePoints.add((int) body.charAt(idx + 1));
                idx += 2;
                continue;
            }
            // range a-z
            if (idx + 2 < body.length() && body.charAt(idx + 1) == '-') {
                char hi = body.charAt(idx + 2);
                add(ch, hi);
                idx += 3;
                continue;
            }
            codePoints.add((int) ch);
            idx++;
        }
        if (negate) complement();
        return this;
    }

    @Override
    public Iterator<String> iterator() {
        java.util.ArrayList<String> all = new java.util.ArrayList<>();
        for (int c : codePoints) all.add(new String(Character.toChars(c)));
        all.addAll(strings);
        return all.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int c : codePoints) sb.appendCodePoint(c);
        for (String s : strings) sb.append('{').append(s).append('}');
        sb.append(']');
        return sb.toString();
    }
}
