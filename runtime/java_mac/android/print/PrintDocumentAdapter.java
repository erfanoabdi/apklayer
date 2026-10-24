// android.print.PrintDocumentAdapter
//
// The abstract adapter an app implements to render its content into a print
// document. Mac.apk has no print transport, so the adapter is never driven by
// PrintManager.print, but the abstract type + the nested LayoutResultCallback /
// WriteResultCallback + PrintDocumentInfo must resolve for the app's adapter
// subclass to compile. Mirrors
// frameworks/base/core/java/android/print/PrintDocumentAdapter.java.
package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

public abstract class PrintDocumentAdapter {

    // AOSP really does use the bare literal "EXTRA_PRINT_PREVIEW" as the value -- an
    // upstream oddity, but it is the value javac inlines into every app compiled
    // against the SDK, so we must match it rather than "correct" it.
    // frameworks/base/core/java/android/print/PrintDocumentAdapter.java (android-11.0.0_r48)
    public static final String EXTRA_PRINT_PREVIEW = "EXTRA_PRINT_PREVIEW";

    public PrintDocumentAdapter() {}

    public void onStart() {}

    public abstract void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
            CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras);

    public abstract void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal, WriteResultCallback callback);

    public void onFinish() {}

    public static abstract class LayoutResultCallback {
        public LayoutResultCallback() {}
        public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {}
        public void onLayoutFailed(CharSequence error) {}
        public void onLayoutCancelled() {}
    }

    public static abstract class WriteResultCallback {
        public WriteResultCallback() {}
        public void onWriteFinished(PageRange[] pages) {}
        public void onWriteFailed(CharSequence error) {}
        public void onWriteCancelled() {}
    }
}
