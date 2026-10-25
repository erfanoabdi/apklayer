// android.util.Printer
//
// AOSP frameworks/base/core/java/android/util/Printer.java: a simple
// interface for printing formatted text, line by line. It is the type
// Looper.setMessageLogging(Printer) / Looper.dump(Printer,String) /
// Handler.dump(Printer,String) / View.dump and many AOSP "debug dump"
// methods accept, AND the type LogPrinter / StringBuilderPrinter /
// PrintWriterPrinter implement.
//
// v1.0.464 (subsystem-04 close-list): this used to be an EMPTY CLASS
// stub. That broke two contracts: (1) any app/framework class that did
// `class Foo implements Printer { ... }` failed to link (Printer was a
// class, not an interface), and (2) callers that invoked println on a
// Printer got NoSuchMethodError. Making it the real one-method interface
// restores the published API; LogPrinter (same package) now implements it.
package android.util;

public interface Printer {
    /** Write a line of text to the output. There is no need to terminate
     *  the line with a newline; the implementation adds it. */
    void println(String x);
}
