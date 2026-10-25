// android.util.StringBuilderPrinter -- a Printer that appends to a
// StringBuilder. AOSP:
// frameworks/base/core/java/android/util/StringBuilderPrinter.java
//
// AOSP appends the line then a '\n'; it does NOT use the platform line
// separator, so a dump built here matches one built on a device byte for byte.
package android.util;

public class StringBuilderPrinter implements Printer {
    private final StringBuilder mBuilder;

    public StringBuilderPrinter(StringBuilder builder) {
        mBuilder = builder;
    }

    public void println(String x) {
        mBuilder.append(x);
        int len = x.length();
        if (len <= 0 || x.charAt(len - 1) != '\n') {
            mBuilder.append('\n');
        }
    }
}
