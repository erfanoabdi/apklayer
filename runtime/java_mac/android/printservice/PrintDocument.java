// android.printservice.PrintDocument
//
// The document behind a queued print job, as seen by a print service: its
// PrintDocumentInfo plus a readable descriptor for the bytes.
//
// getInfo() is fully real. getData() is not, and cannot be: AOSP opens a pipe
// and asks the SPOOLER to pump the job's data into it, and Mac.apk has no
// spooler that owns any print-job data (see PrintService). AOSP itself returns
// null from getData() when that call fails, so null is the faithful "no data"
// answer here rather than an empty pipe that would read as a zero-byte
// document -- a service must not be told it received an empty file.
//
// Mirrors frameworks/base/core/java/android/printservice/PrintDocument.java
// (android16-release).
package android.printservice;

import android.os.ParcelFileDescriptor;
import android.print.PrintDocumentInfo;
import android.print.PrintJobId;

public final class PrintDocument {

    private static final String LOG_TAG = "PrintDocument";

    private final PrintJobId mPrintJobId;
    private final PrintDocumentInfo mInfo;

    PrintDocument(PrintJobId printJobId, PrintDocumentInfo info) {
        mPrintJobId = printJobId;
        mInfo = info;
    }

    public PrintDocumentInfo getInfo() {
        PrintService.throwIfNotCalledOnMainThread();
        return mInfo;
    }

    public ParcelFileDescriptor getData() {
        PrintService.throwIfNotCalledOnMainThread();
        android.util.Log.w(LOG_TAG, "no print spooler holds data for job " + mPrintJobId
                + "; returning null (AOSP's own failure answer) rather than an empty stream");
        return null;
    }
}
