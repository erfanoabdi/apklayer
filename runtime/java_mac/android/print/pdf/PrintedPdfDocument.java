package android.print.pdf;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.print.PrintAttributes;

/**
 * A PdfDocument sized from PrintAttributes: it converts the print job's media
 * size and margins into page dimensions in PostScript points, so the app just
 * calls startPage(pageNumber) instead of computing geometry itself.
 *
 * Source: frameworks/base/core/java/android/print/pdf/PrintedPdfDocument.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/print/pdf/PrintedPdfDocument.java
 */
public class PrintedPdfDocument extends PdfDocument {

    private static final int MILS_PER_INCH = 1000;
    private static final int POINTS_IN_INCH = 72;

    private final int mPageWidth;
    private final int mPageHeight;
    private final Rect mContentRect;

    public PrintedPdfDocument(Context context, PrintAttributes attributes) {
        PrintAttributes.MediaSize mediaSize = attributes != null
                ? attributes.getMediaSize() : null;
        if (mediaSize == null) mediaSize = PrintAttributes.MediaSize.ISO_A4;

        // NO ROTATION HERE. A previous revision forced the media to portrait
        // with asPortrait() when the job was landscape, on the belief that AOSP
        // does so. AOSP does NOT: PrintedPdfDocument's constructor reads
        // getWidthMils()/getHeightMils() straight off the supplied MediaSize
        // (frameworks/base/core/java/android/print/pdf/PrintedPdfDocument.java,
        // android16-release). Rotating meant every landscape print job got a
        // portrait page -- wrong geometry, and silent, since nothing throws.

        mPageWidth = milsToPoints(mediaSize.getWidthMils());
        mPageHeight = milsToPoints(mediaSize.getHeightMils());

        PrintAttributes.Margins minMargins = attributes != null
                ? attributes.getMinMargins() : null;
        if (minMargins == null) minMargins = PrintAttributes.Margins.NO_MARGINS;

        int marginLeft = milsToPoints(minMargins.getLeftMils());
        int marginTop = milsToPoints(minMargins.getTopMils());
        int marginRight = milsToPoints(minMargins.getRightMils());
        int marginBottom = milsToPoints(minMargins.getBottomMils());
        mContentRect = new Rect(marginLeft, marginTop,
                mPageWidth - marginRight, mPageHeight - marginBottom);
    }

    private static int milsToPoints(int mils) {
        return (int) (((float) mils / MILS_PER_INCH) * POINTS_IN_INCH);
    }

    /** Start a page using the geometry derived from the PrintAttributes. */
    public Page startPage(int pageNumber) {
        PageInfo pageInfo = new PageInfo.Builder(mPageWidth, mPageHeight, pageNumber)
                .setContentRect(mContentRect)
                .create();
        return startPage(pageInfo);
    }

    public int getPageWidth() { return mPageWidth; }

    public int getPageHeight() { return mPageHeight; }

    public Rect getPageContentRect() { return mContentRect; }
}
