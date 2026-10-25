// android.util.PrintWriterPrinter -- a Printer that forwards to a PrintWriter.
// AOSP: frameworks/base/core/java/android/util/PrintWriterPrinter.java
package android.util;

import java.io.PrintWriter;

public class PrintWriterPrinter implements Printer {
    private final PrintWriter mPW;

    public PrintWriterPrinter(PrintWriter pw) {
        mPW = pw;
    }

    public void println(String x) {
        mPW.println(x);
    }
}
