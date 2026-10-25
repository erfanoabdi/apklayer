package android.icu.text;
import java.util.Collection;
/** Formats a list as "a, b, and c". */
public final class ListFormatter {
    private ListFormatter() {}
    public static ListFormatter getInstance() { return new ListFormatter(); }
    public static ListFormatter getInstance(java.util.Locale locale) { return new ListFormatter(); }
    public static ListFormatter getInstance(android.icu.util.ULocale locale) { return new ListFormatter(); }
    public String format(Collection<?> items) {
        if (items == null) return "";
        StringBuilder sb = new StringBuilder(); boolean first = true;
        for (Object o : items) { if (!first) sb.append(", "); sb.append(o); first = false; }
        return sb.toString();
    }
    public String format(Object... items) { return format(java.util.Arrays.asList(items)); }
}
