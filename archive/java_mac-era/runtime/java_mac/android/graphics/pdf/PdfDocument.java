package android.graphics.pdf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a PDF from a sequence of pages that the app draws into with a Canvas.
 *
 * This writes a REAL, openable PDF rather than an empty file. Each page is
 * rasterized into a Bitmap-backed Canvas at the page's declared size and
 * embedded as a DCTDecode (JPEG) image covering the page. That is a genuine
 * tradeoff worth stating plainly: real Android records the Canvas ops as PDF
 * vector operators, so its output has selectable text and scales losslessly,
 * while ours is a faithful raster of the same drawing. The app's draw code runs
 * unchanged and the resulting file opens in any viewer, prints, and has the
 * right page count and dimensions -- which is what the "export/share as PDF"
 * flows in the corpus actually need. Emitting nothing, or an empty valid PDF,
 * would be the silent-no-op failure this framework audit exists to remove.
 *
 * PDF structure emitted (PDF 1.4, ISO 32000-1): header, one XObject image plus
 * one content stream per page, a Pages tree, a Catalog, then a classic xref
 * table and trailer. Offsets are byte-accurate, which is what makes it load.
 *
 * Source (API surface): frameworks/base/graphics/java/android/graphics/pdf/PdfDocument.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/pdf/PdfDocument.java
 */
public class PdfDocument {

    private final List<PageInfo> mPages = new ArrayList<>();
    private final List<Bitmap> mPageBitmaps = new ArrayList<>();
    private Page mCurrentPage;
    private boolean mClosed;

    public PdfDocument() { }

    /** Begin a page; draw into the returned Page's Canvas, then finishPage(). */
    public Page startPage(PageInfo pageInfo) {
        throwIfClosed();
        if (pageInfo == null) throw new IllegalArgumentException("page info cannot be null");
        if (mCurrentPage != null) {
            throw new IllegalStateException("Previous page not finished!");
        }
        int w = Math.max(1, pageInfo.getPageWidth());
        int h = Math.max(1, pageInfo.getPageHeight());
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        // PDF pages are white by default; without this the raster would be
        // transparent-black and print as a solid dark page.
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(android.graphics.Color.WHITE);
        mCurrentPage = new Page(canvas, pageInfo, bmp);
        return mCurrentPage;
    }

    public void finishPage(Page page) {
        throwIfClosed();
        if (page == null) throw new IllegalArgumentException("page cannot be null");
        if (page != mCurrentPage) {
            throw new IllegalStateException("invalid page");
        }
        mPages.add(page.getInfo());
        mPageBitmaps.add(page.mBitmap);
        page.finish();
        mCurrentPage = null;
    }

    public void writeTo(OutputStream out) throws IOException {
        throwIfClosed();
        if (mCurrentPage != null) {
            throw new IllegalStateException("Current page not finished!");
        }
        if (out == null) throw new IllegalArgumentException("out cannot be null");
        new PdfWriter(mPages, mPageBitmaps).write(out);
    }

    public List<PageInfo> getPages() { return new ArrayList<>(mPages); }

    public void close() {
        if (mCurrentPage != null) { mCurrentPage.finish(); mCurrentPage = null; }
        mClosed = true;
    }

    private void throwIfClosed() {
        if (mClosed) throw new IllegalStateException("document is closed!");
    }

    /** One page: its metadata plus the Canvas the app draws into. */
    public static final class Page {
        private Canvas mCanvas;
        private final PageInfo mPageInfo;
        final Bitmap mBitmap;

        Page(Canvas canvas, PageInfo pageInfo, Bitmap bitmap) {
            mCanvas = canvas;
            mPageInfo = pageInfo;
            mBitmap = bitmap;
        }

        /** AOSP returns null once the page is finished. */
        public Canvas getCanvas() { return mCanvas; }

        public PageInfo getInfo() { return mPageInfo; }

        void finish() { mCanvas = null; }
    }

    /** Page dimensions in PostScript points (1/72 inch) plus the content rect. */
    public static final class PageInfo {
        private int mPageWidth;
        private int mPageHeight;
        private Rect mContentRect;
        private int mPageNumber;

        private PageInfo() { }

        public int getPageWidth() { return mPageWidth; }
        public int getPageHeight() { return mPageHeight; }
        public Rect getContentRect() { return mContentRect; }
        public int getPageNumber() { return mPageNumber; }

        public static final class Builder {
            private final PageInfo mPageInfo = new PageInfo();

            public Builder(int pageWidth, int pageHeight, int pageNumber) {
                if (pageWidth <= 0) throw new IllegalArgumentException("page width must be positive");
                if (pageHeight <= 0) throw new IllegalArgumentException("page height must be positive");
                if (pageNumber < 0) throw new IllegalArgumentException("page number must be non negative");
                mPageInfo.mPageWidth = pageWidth;
                mPageInfo.mPageHeight = pageHeight;
                mPageInfo.mPageNumber = pageNumber;
            }

            public Builder setContentRect(Rect contentRect) {
                mPageInfo.mContentRect = contentRect;
                return this;
            }

            public PageInfo create() {
                if (mPageInfo.mContentRect == null) {
                    // AOSP defaults the content rect to the whole page.
                    mPageInfo.mContentRect =
                            new Rect(0, 0, mPageInfo.mPageWidth, mPageInfo.mPageHeight);
                }
                return mPageInfo;
            }
        }
    }

    /**
     * Minimal but standards-correct PDF serializer. Kept private to the package
     * so the emitted structure can change without touching the public API.
     */
    private static final class PdfWriter {
        private final List<PageInfo> mInfos;
        private final List<Bitmap> mBitmaps;
        private final List<Integer> mOffsets = new ArrayList<>();
        private ByteArrayOutputStream mBuf;

        PdfWriter(List<PageInfo> infos, List<Bitmap> bitmaps) {
            mInfos = infos;
            mBitmaps = bitmaps;
        }

        void write(OutputStream out) throws IOException {
            mBuf = new ByteArrayOutputStream();
            final int n = mInfos.size();
            // Object numbering: 1 = Catalog, 2 = Pages, then per page a Page
            // object, a content stream and an image XObject.
            final int firstPageObj = 3;

            raw("%PDF-1.4\n");
            // A binary comment marks the file as containing binary data, which
            // viewers and transfer tools rely on.
            raw("%âãÏÓ\n");

            startObj(1);
            raw("<< /Type /Catalog /Pages 2 0 R >>\n");
            endObj();

            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < n; i++) kids.append(firstPageObj + i * 3).append(" 0 R ");
            startObj(2);
            raw("<< /Type /Pages /Count " + n + " /Kids [" + kids.toString().trim() + "] >>\n");
            endObj();

            for (int i = 0; i < n; i++) {
                PageInfo info = mInfos.get(i);
                int pageObj = firstPageObj + i * 3;
                int contentObj = pageObj + 1;
                int imageObj = pageObj + 2;
                int w = info.getPageWidth();
                int h = info.getPageHeight();

                startObj(pageObj);
                raw("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "]"
                        + " /Resources << /XObject << /Im0 " + imageObj + " 0 R >> >>"
                        + " /Contents " + contentObj + " 0 R >>\n");
                endObj();

                // Place the page image to exactly cover the MediaBox.
                byte[] content = ("q\n" + w + " 0 0 " + h + " 0 0 cm\n/Im0 Do\nQ\n")
                        .getBytes(StandardCharsets.US_ASCII);
                startObj(contentObj);
                raw("<< /Length " + content.length + " >>\nstream\n");
                mBuf.write(content);
                raw("endstream\n");
                endObj();

                byte[] jpeg = encodeJpeg(mBitmaps.get(i));
                startObj(imageObj);
                raw("<< /Type /XObject /Subtype /Image /Width " + mBitmaps.get(i).getWidth()
                        + " /Height " + mBitmaps.get(i).getHeight()
                        + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                        + " /Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n");
                mBuf.write(jpeg);
                raw("\nendstream\n");
                endObj();
            }

            int xrefPos = mBuf.size();
            int total = 1 + mOffsets.size();
            raw("xref\n0 " + total + "\n");
            raw("0000000000 65535 f \n");
            for (int off : mOffsets) raw(String.format("%010d 00000 n \n", off));
            raw("trailer\n<< /Size " + total + " /Root 1 0 R >>\n");
            raw("startxref\n" + xrefPos + "\n%%EOF\n");

            mBuf.writeTo(out);
            out.flush();
        }

        private byte[] encodeJpeg(Bitmap b) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 90, bos);
            return bos.toByteArray();
        }

        private void startObj(int num) {
            // xref offsets must be recorded in object-number order.
            while (mOffsets.size() < num) mOffsets.add(0);
            mOffsets.set(num - 1, mBuf.size());
            raw(num + " 0 obj\n");
        }

        private void endObj() { raw("endobj\n"); }

        private void raw(String s) {
            byte[] b = new byte[s.length()];
            for (int i = 0; i < s.length(); i++) b[i] = (byte) s.charAt(i);
            mBuf.write(b, 0, b.length);
        }
    }
}
