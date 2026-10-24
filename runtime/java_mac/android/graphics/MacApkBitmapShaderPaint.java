// android.graphics.MacApkBitmapShaderPaint -- a BitmapShader fill as Skia samples
// it: through the shader's LOCAL MATRIX and the canvas matrix, with its TILE MODES.
//
// WHY (v1.0.2271). Canvas.awtPaintForShader used to hand every BitmapShader to a
// java.awt.TexturePaint anchored at (0,0) at the bitmap's own size. TexturePaint
// only repeats and has no transform, so two things a BitmapShader carries were
// lost: setLocalMatrix (ignored outright) and CLAMP / DECAL (drawn as REPEAT).
// The common "draw a bitmap into a rounded rect" idiom is exactly a CLAMP shader
// whose local matrix maps the source crop onto the destination
// (translate(-src) . scale(dst/src) . translate(dst)), followed by
// drawRoundRect. iiSU draws every app tile that way, and each icon came out at
// its native size, unscaled and repeated across the tile ("tiled icons", owner
// report 2026-10-19 on v1.0.2267).
//
// SEMANTICS (Skia, src/shaders/SkImageShader.cpp + SkBitmapProcState): the
// shader's total matrix is CTM x localMatrix; a device pixel is sampled at its
// CENTRE, mapped back through the inverse of that matrix into image space; each
// axis then applies its tile mode -- CLAMP repeats the edge texel, REPEAT wraps,
// MIRROR reflects on every other tile, DECAL is transparent outside the image
// (frameworks/base Shader.TileMode javadoc). Filtering: LINEAR (bilinear) when
// BitmapShader.setFilterMode(LINEAR) or, at the default mode, when the Paint's
// FILTER_BITMAP flag is set (AOSP BitmapShader.getFilterMode doc: the default
// "defers to Paint#isFilterBitmap"); NEAREST otherwise.
package android.graphics;

import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

final class MacApkBitmapShaderPaint implements java.awt.Paint {
    private final int[] argb;     // non-premultiplied 0xAARRGGBB, row-major
    private final int w, h;
    private final AffineTransform local;   // null = identity
    private final Shader.TileMode tx, ty;
    private final boolean bilinear;

    MacApkBitmapShaderPaint(BufferedImage img, AffineTransform local,
                            Shader.TileMode tx, Shader.TileMode ty, boolean bilinear) {
        this.w = img.getWidth();
        this.h = img.getHeight();
        this.argb = pixels(img);
        this.local = local;
        this.tx = tx != null ? tx : Shader.TileMode.CLAMP;
        this.ty = ty != null ? ty : Shader.TileMode.CLAMP;
        this.bilinear = bilinear;
    }

    /** The image's pixels as non-premultiplied ARGB: the backing array itself for
     *  a TYPE_INT_ARGB image (no copy; Bitmap's zero-copy view is one), else one
     *  conversion. */
    private static int[] pixels(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB
                && img.getRaster().getDataBuffer() instanceof DataBufferInt
                && img.getRaster().getDataBuffer().getNumBanks() == 1) {
            final int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            if (data.length >= img.getWidth() * img.getHeight()
                    && img.getRaster().getSampleModelTranslateX() == 0
                    && img.getRaster().getSampleModelTranslateY() == 0
                    && img.getRaster().getSampleModel().getWidth() == img.getWidth()) {
                return data;
            }
        }
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    @Override public int getTransparency() { return Transparency.TRANSLUCENT; }

    @Override
    public PaintContext createContext(ColorModel cm, Rectangle deviceBounds,
                                      Rectangle2D userBounds, AffineTransform xform,
                                      RenderingHints hints) {
        AffineTransform total = new AffineTransform(xform);
        if (local != null) total.concatenate(local);
        AffineTransform inv;
        try {
            inv = total.createInverse();
        } catch (NoninvertibleTransformException e) {
            inv = null;   // a collapsed matrix draws nothing (Skia: an empty shader)
        }
        return new Ctx(inv);
    }

    // ---- sampling, also used directly by the offline gate ----

    /** One tile-mode lookup on an axis: the texel index for coordinate i, or -1
     *  when DECAL puts it outside the image. */
    static int tile(int i, int n, Shader.TileMode m) {
        if (n <= 0) return -1;
        switch (m) {
            case REPEAT: {
                int r = i % n;
                return r < 0 ? r + n : r;
            }
            case MIRROR: {
                int p = 2 * n;
                int r = i % p;
                if (r < 0) r += p;
                return r < n ? r : p - 1 - r;
            }
            case DECAL:
                return (i < 0 || i >= n) ? -1 : i;
            case CLAMP:
            default:
                return i < 0 ? 0 : (i >= n ? n - 1 : i);
        }
    }

    private int texel(int ix, int iy) {
        final int x = tile(ix, w, tx), y = tile(iy, h, ty);
        if (x < 0 || y < 0) return 0;
        return argb[y * w + x];
    }

    /** The colour at image-space point (u, v), non-premultiplied ARGB. */
    int sample(double u, double v) {
        if (!bilinear) {
            return texel((int) Math.floor(u), (int) Math.floor(v));
        }
        // Bilinear on texel centres, blended premultiplied (as Skia filters).
        final double fu = u - 0.5, fv = v - 0.5;
        final int x0 = (int) Math.floor(fu), y0 = (int) Math.floor(fv);
        final double ax = fu - x0, ay = fv - y0;
        final int c00 = texel(x0, y0), c10 = texel(x0 + 1, y0);
        final int c01 = texel(x0, y0 + 1), c11 = texel(x0 + 1, y0 + 1);
        final double w00 = (1 - ax) * (1 - ay), w10 = ax * (1 - ay);
        final double w01 = (1 - ax) * ay, w11 = ax * ay;
        double a = 0, r = 0, g = 0, b = 0;
        final int[] cs = { c00, c10, c01, c11 };
        final double[] ws = { w00, w10, w01, w11 };
        for (int k = 0; k < 4; k++) {
            final int c = cs[k];
            final double ca = ((c >>> 24) & 0xff) * ws[k];
            a += ca;
            r += ((c >>> 16) & 0xff) * ca;
            g += ((c >>> 8) & 0xff) * ca;
            b += (c & 0xff) * ca;
        }
        if (a <= 0) return 0;
        final int ia = (int) Math.round(a);
        final int ir = (int) Math.round(r / a), ig = (int) Math.round(g / a), ib = (int) Math.round(b / a);
        return (clamp255(ia) << 24) | (clamp255(ir) << 16) | (clamp255(ig) << 8) | clamp255(ib);
    }

    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /** The colour at device pixel (dx, dy) under device->user transform xform. */
    int sampleDevice(AffineTransform inv, int dx, int dy) {
        if (inv == null) return 0;
        final double[] pt = { dx + 0.5, dy + 0.5 };
        inv.transform(pt, 0, pt, 0, 1);
        return sample(pt[0], pt[1]);
    }

    private final class Ctx implements PaintContext {
        private final AffineTransform inv;
        private final double[] pt = new double[2];
        Ctx(AffineTransform inv) { this.inv = inv; }
        @Override public void dispose() {}
        @Override public ColorModel getColorModel() { return ColorModel.getRGBdefault(); }
        @Override public Raster getRaster(int x, int y, int rw, int rh) {
            final WritableRaster wr = getColorModel().createCompatibleWritableRaster(rw, rh);
            final int[] out = ((DataBufferInt) wr.getDataBuffer()).getData();
            if (inv == null) return wr;   // all transparent
            // Walk each row incrementally: one inverse-map per row start, then
            // add the inverse matrix's x column per pixel.
            final double sx = inv.getScaleX(), shy = inv.getShearY();
            for (int j = 0; j < rh; j++) {
                pt[0] = x + 0.5; pt[1] = y + j + 0.5;
                inv.transform(pt, 0, pt, 0, 1);
                double u = pt[0], v = pt[1];
                final int row = j * rw;
                for (int i = 0; i < rw; i++) {
                    out[row + i] = sample(u, v);
                    u += sx;
                    v += shy;
                }
            }
            return wr;
        }
    }
}
