// android.util.PrintStreamPrinter -- a Printer that forwards to a PrintStream.
// AOSP: frameworks/base/core/java/android/util/PrintStreamPrinter.java
package android.util;

import java.io.PrintStream;

public class PrintStreamPrinter implements Printer {
    private final PrintStream mPS;

    public PrintStreamPrinter(PrintStream pw) {
        mPS = pw;
    }

    public void println(String x) {
        mPS.println(x);
    }
}
