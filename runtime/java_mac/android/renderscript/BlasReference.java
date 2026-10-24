package android.renderscript;

import java.nio.ByteBuffer;

/**
 * MAC.APK INTERNAL, NOT PART OF THE ANDROID API. Package-private.
 *
 * A reference BLAS over {@link Allocation} storage, backing
 * {@link ScriptIntrinsicBLAS}.
 *
 * WHY THIS EXISTS RATHER THAN A CALL INTO ACCELERATE. The decision ladder says
 * route to a macOS-native facility first, and macOS does ship a BLAS (vecLib,
 * inside Accelerate.framework). Reaching it needs a JNI binding in
 * runtime/src, which is outside this package; a straightforward reference
 * implementation here is correct today and can be re-pointed at Accelerate
 * later without any API change. Nothing about the contract below would move.
 *
 * CONVENTIONS, all of them the platform's:
 *
 *  - Matrices are ROW MAJOR 2D Allocations. dimY is the row count (M), dimX the
 *    column count (N). This is CblasRowMajor, not the column-major layout the
 *    Fortran reference uses, so an index derived from netlib documentation must
 *    be transposed before it is used here.
 *  - Vectors are 1D Allocations of length 1 + (n - 1) * inc, and inc must be
 *    positive (the platform rejects the negative strides Fortran allows).
 *  - A complex value is a two-component Element (F32_2 / F64_2), real part
 *    first.
 *  - Packed triangular storage: UPPER is row by row from the diagonal
 *    rightward, LOWER is row by row from the left up to the diagonal.
 *  - Banded storage is the platform's ROW-BASED form, described in each
 *    ScriptIntrinsicBLAS method's own comment, not the LAPACK column form.
 *
 * PRECISION. Everything is computed in double and narrowed on store, so an
 * F32 routine accumulates more accurately than a device's single-precision
 * BLAS would. Results can therefore differ from a device in the last bits of a
 * float, always in the direction of being closer to the exact answer.
 */
final class BlasReference {

    private BlasReference() {
    }

    // Transpose / uplo / diag / side codes, mirrored from ScriptIntrinsicBLAS
    // so the kernels below do not depend on the public class's constants being
    // loaded first.
    static final int NO_TRANSPOSE = 111;
    static final int TRANSPOSE = 112;
    static final int CONJ_TRANSPOSE = 113;
    static final int UPPER = 121;
    static final int LOWER = 122;
    static final int NON_UNIT = 131;
    static final int UNIT = 132;
    static final int LEFT = 141;
    static final int RIGHT = 142;

    // ---- storage -------------------------------------------------------------

    static int rows(Allocation a) {
        return Math.max(1, a.getType().getY());
    }

    static int cols(Allocation a) {
        return a.getType().getX();
    }

    private static boolean isDouble(Allocation a) {
        return a.getElement().mType == Element.DataType.FLOAT_64;
    }

    private static boolean isComplex(Allocation a) {
        return a.getElement().getVectorSize() >= 2;
    }

    /**
     * Loads a matrix as interleaved (re, im) doubles, row major and tightly
     * packed, regardless of the Allocation's own row stride.
     */
    static double[] loadMat(Allocation a) {
        final int m = rows(a);
        final int n = cols(a);
        final double[] out = new double[m * n * 2];
        final ByteBuffer bb = IntrinsicSupport.bufferOf(a);
        final int cell = a.getElement().getBytesSize();
        final int base = a.baseOffset();
        final int stride = a.rowStride();
        final boolean dbl = isDouble(a);
        final boolean cplx = isComplex(a);
        final int csz = dbl ? 8 : 4;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                final int p = base + (i * stride + j) * cell;
                final int o = (i * n + j) * 2;
                out[o] = dbl ? bb.getDouble(p) : bb.getFloat(p);
                if (cplx) {
                    out[o + 1] = dbl ? bb.getDouble(p + csz) : bb.getFloat(p + csz);
                }
            }
        }
        return out;
    }

    static void storeMat(Allocation a, double[] d) {
        final int m = rows(a);
        final int n = cols(a);
        final ByteBuffer bb = IntrinsicSupport.bufferOf(a);
        final int cell = a.getElement().getBytesSize();
        final int base = a.baseOffset();
        final int stride = a.rowStride();
        final boolean dbl = isDouble(a);
        final boolean cplx = isComplex(a);
        final int csz = dbl ? 8 : 4;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                final int p = base + (i * stride + j) * cell;
                final int o = (i * n + j) * 2;
                if (dbl) {
                    bb.putDouble(p, d[o]);
                    if (cplx) {
                        bb.putDouble(p + csz, d[o + 1]);
                    }
                } else {
                    bb.putFloat(p, (float) d[o]);
                    if (cplx) {
                        bb.putFloat(p + csz, (float) d[o + 1]);
                    }
                }
            }
        }
    }

    /** Loads n vector entries, honouring the increment. */
    static double[] loadVec(Allocation a, int inc, int n) {
        final double[] out = new double[n * 2];
        final ByteBuffer bb = IntrinsicSupport.bufferOf(a);
        final int cell = a.getElement().getBytesSize();
        final int base = a.baseOffset();
        final boolean dbl = isDouble(a);
        final boolean cplx = isComplex(a);
        final int csz = dbl ? 8 : 4;
        for (int k = 0; k < n; k++) {
            final int p = base + (k * inc) * cell;
            out[k * 2] = dbl ? bb.getDouble(p) : bb.getFloat(p);
            if (cplx) {
                out[k * 2 + 1] = dbl ? bb.getDouble(p + csz) : bb.getFloat(p + csz);
            }
        }
        return out;
    }

    static void storeVec(Allocation a, int inc, int n, double[] d) {
        final ByteBuffer bb = IntrinsicSupport.bufferOf(a);
        final int cell = a.getElement().getBytesSize();
        final int base = a.baseOffset();
        final boolean dbl = isDouble(a);
        final boolean cplx = isComplex(a);
        final int csz = dbl ? 8 : 4;
        for (int k = 0; k < n; k++) {
            final int p = base + (k * inc) * cell;
            if (dbl) {
                bb.putDouble(p, d[k * 2]);
                if (cplx) {
                    bb.putDouble(p + csz, d[k * 2 + 1]);
                }
            } else {
                bb.putFloat(p, (float) d[k * 2]);
                if (cplx) {
                    bb.putFloat(p + csz, (float) d[k * 2 + 1]);
                }
            }
        }
    }

    /**
     * Index of (i, j) in row-major packed triangular storage.
     *
     * UPPER walks each row from the diagonal to the right; LOWER walks each row
     * from column 0 to the diagonal. Only the referenced triangle is stored, so
     * asking for the other one is a caller error and the callers below never
     * do it.
     */
    static int packedIndex(int uplo, int n, int i, int j) {
        if (uplo == UPPER) {
            // Rows above i contribute (n - r) entries each.
            return i * n - (i * (i - 1)) / 2 + (j - i);
        }
        return (i * (i + 1)) / 2 + j;
    }

    // ---- small complex helpers ----------------------------------------------

    private static void cmulAcc(double[] acc, double ar, double ai, double br, double bi) {
        acc[0] += ar * br - ai * bi;
        acc[1] += ar * bi + ai * br;
    }

    private static void cmulInto(double[] out, double ar, double ai, double br, double bi) {
        out[0] = ar * br - ai * bi;
        out[1] = ar * bi + ai * br;
    }

    /** (ar + i ai) / (br + i bi), used only by the triangular solvers. */
    private static void cdivInto(double[] out, double ar, double ai, double br, double bi) {
        if (bi == 0.0) {
            out[0] = ar / br;
            out[1] = ai / br;
            return;
        }
        final double den = br * br + bi * bi;
        out[0] = (ar * br + ai * bi) / den;
        out[1] = (ai * br - ar * bi) / den;
    }

    /** op(A) at (i, j), where A is m x n in row-major interleaved storage. */
    private static void opAt(double[] a, int n, int trans, int i, int j, double[] out) {
        final int o = (trans == NO_TRANSPOSE) ? (i * n + j) * 2 : (j * n + i) * 2;
        out[0] = a[o];
        out[1] = (trans == CONJ_TRANSPOSE) ? -a[o + 1] : a[o + 1];
    }

    // ---- Level 3 -------------------------------------------------------------

    /** C := alpha * op(A) * op(B) + beta * C. */
    static void gemm(int transA, int transB, double[] alpha, Allocation A, Allocation B,
                     double[] beta, Allocation C) {
        final int m = rows(C);
        final int n = cols(C);
        final int k = (transA == NO_TRANSPOSE) ? cols(A) : rows(A);
        final int aCols = cols(A);
        final int bCols = cols(B);
        final double[] a = loadMat(A);
        final double[] b = loadMat(B);
        final double[] c = loadMat(C);
        final double[] av = new double[2];
        final double[] bv = new double[2];
        final double[] acc = new double[2];
        final double[] t = new double[2];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                acc[0] = 0;
                acc[1] = 0;
                for (int p = 0; p < k; p++) {
                    opAt(a, aCols, transA, i, p, av);
                    opAt(b, bCols, transB, p, j, bv);
                    cmulAcc(acc, av[0], av[1], bv[0], bv[1]);
                }
                final int o = (i * n + j) * 2;
                cmulInto(t, beta[0], beta[1], c[o], c[o + 1]);
                double rr = t[0];
                double ri = t[1];
                cmulInto(t, alpha[0], alpha[1], acc[0], acc[1]);
                c[o] = rr + t[0];
                c[o + 1] = ri + t[1];
            }
        }
        storeMat(C, c);
    }

    /**
     * C := alpha * A * B + beta * C (LEFT) or alpha * B * A + beta * C (RIGHT),
     * with A symmetric (hermitian == false) or Hermitian, stored in one
     * triangle.
     *
     * The Hermitian case takes the diagonal's REAL part only, which is what the
     * reference BLAS does: a Hermitian matrix has a real diagonal by
     * definition, and any imaginary part stored there is garbage to be ignored
     * rather than trusted.
     */
    static void symm(int side, int uplo, double[] alpha, Allocation A, Allocation B,
                     double[] beta, Allocation C, boolean hermitian) {
        final int m = rows(C);
        final int n = cols(C);
        final int an = cols(A);
        final double[] a = loadMat(A);
        final double[] b = loadMat(B);
        final double[] c = loadMat(C);
        final double[] av = new double[2];
        final double[] acc = new double[2];
        final double[] t = new double[2];
        final int k = (side == LEFT) ? m : n;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                acc[0] = 0;
                acc[1] = 0;
                for (int p = 0; p < k; p++) {
                    if (side == LEFT) {
                        symAt(a, an, uplo, hermitian, i, p, av);
                        cmulAcc(acc, av[0], av[1], b[(p * n + j) * 2], b[(p * n + j) * 2 + 1]);
                    } else {
                        symAt(a, an, uplo, hermitian, p, j, av);
                        cmulAcc(acc, b[(i * cols(B) + p) * 2], b[(i * cols(B) + p) * 2 + 1],
                                av[0], av[1]);
                    }
                }
                final int o = (i * n + j) * 2;
                cmulInto(t, beta[0], beta[1], c[o], c[o + 1]);
                double rr = t[0];
                double ri = t[1];
                cmulInto(t, alpha[0], alpha[1], acc[0], acc[1]);
                c[o] = rr + t[0];
                c[o + 1] = ri + t[1];
            }
        }
        storeMat(C, c);
    }

    /** Reads a symmetric or Hermitian matrix element from its stored triangle. */
    private static void symAt(double[] a, int n, int uplo, boolean hermitian,
                              int i, int j, double[] out) {
        final boolean inStored = (uplo == UPPER) ? (j >= i) : (j <= i);
        final int o = inStored ? (i * n + j) * 2 : (j * n + i) * 2;
        out[0] = a[o];
        if (i == j) {
            out[1] = hermitian ? 0.0 : a[o + 1];
        } else if (inStored) {
            out[1] = a[o + 1];
        } else {
            out[1] = hermitian ? -a[o + 1] : a[o + 1];
        }
    }

    /**
     * C := alpha * op(A) * op(A)' + beta * C, updating only C's Uplo triangle.
     * For HERK the transpose is conjugate and alpha and beta are real.
     */
    static void syrk(int uplo, int trans, double[] alpha, Allocation A, double[] beta,
                     Allocation C, boolean hermitian) {
        final int n = cols(C);
        final int aCols = cols(A);
        final int k = (trans == NO_TRANSPOSE) ? aCols : rows(A);
        final double[] a = loadMat(A);
        final double[] c = loadMat(C);
        final double[] av = new double[2];
        final double[] bv = new double[2];
        final double[] acc = new double[2];
        final double[] t = new double[2];
        // The second factor is transposed relative to the first.
        final int transB = (trans == NO_TRANSPOSE)
                ? (hermitian ? CONJ_TRANSPOSE : TRANSPOSE) : NO_TRANSPOSE;
        final int transA = (trans == NO_TRANSPOSE)
                ? NO_TRANSPOSE : (hermitian ? CONJ_TRANSPOSE : TRANSPOSE);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (uplo == UPPER ? (j < i) : (j > i)) {
                    continue;
                }
                acc[0] = 0;
                acc[1] = 0;
                for (int p = 0; p < k; p++) {
                    opAt(a, aCols, transA, i, p, av);
                    opAt(a, aCols, transB, p, j, bv);
                    cmulAcc(acc, av[0], av[1], bv[0], bv[1]);
                }
                final int o = (i * n + j) * 2;
                cmulInto(t, beta[0], beta[1], c[o], c[o + 1]);
                double rr = t[0];
                double ri = t[1];
                cmulInto(t, alpha[0], alpha[1], acc[0], acc[1]);
                c[o] = rr + t[0];
                c[o + 1] = ri + t[1];
                if (hermitian && i == j) {
                    c[o + 1] = 0.0;
                }
            }
        }
        storeMat(C, c);
    }

    /**
     * C := alpha * op(A) * op(B)' + conj?(alpha) * op(B) * op(A)' + beta * C,
     * updating only C's Uplo triangle. HER2K conjugates the second alpha and
     * forces a real diagonal.
     */
    static void syr2k(int uplo, int trans, double[] alpha, Allocation A, Allocation B,
                      double[] beta, Allocation C, boolean hermitian) {
        final int n = cols(C);
        final int aCols = cols(A);
        final int bCols = cols(B);
        final int k = (trans == NO_TRANSPOSE) ? aCols : rows(A);
        final double[] a = loadMat(A);
        final double[] b = loadMat(B);
        final double[] c = loadMat(C);
        final double[] u = new double[2];
        final double[] v = new double[2];
        final double[] acc1 = new double[2];
        final double[] acc2 = new double[2];
        final double[] t = new double[2];
        final int tr = (trans == NO_TRANSPOSE)
                ? (hermitian ? CONJ_TRANSPOSE : TRANSPOSE) : NO_TRANSPOSE;
        final int nt = (trans == NO_TRANSPOSE)
                ? NO_TRANSPOSE : (hermitian ? CONJ_TRANSPOSE : TRANSPOSE);
        final double a2r = alpha[0];
        final double a2i = hermitian ? -alpha[1] : alpha[1];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (uplo == UPPER ? (j < i) : (j > i)) {
                    continue;
                }
                acc1[0] = 0;
                acc1[1] = 0;
                acc2[0] = 0;
                acc2[1] = 0;
                for (int p = 0; p < k; p++) {
                    opAt(a, aCols, nt, i, p, u);
                    opAt(b, bCols, tr, p, j, v);
                    cmulAcc(acc1, u[0], u[1], v[0], v[1]);

                    opAt(b, bCols, nt, i, p, u);
                    opAt(a, aCols, tr, p, j, v);
                    cmulAcc(acc2, u[0], u[1], v[0], v[1]);
                }
                final int o = (i * n + j) * 2;
                cmulInto(t, beta[0], beta[1], c[o], c[o + 1]);
                double rr = t[0];
                double ri = t[1];
                cmulInto(t, alpha[0], alpha[1], acc1[0], acc1[1]);
                rr += t[0];
                ri += t[1];
                cmulInto(t, a2r, a2i, acc2[0], acc2[1]);
                c[o] = rr + t[0];
                c[o + 1] = ri + t[1];
                if (hermitian && i == j) {
                    c[o + 1] = 0.0;
                }
            }
        }
        storeMat(C, c);
    }

    /** B := alpha * op(A) * B (LEFT) or alpha * B * op(A) (RIGHT), A triangular. */
    static void trmm(int side, int uplo, int transA, int diag, double[] alpha,
                     Allocation A, Allocation B) {
        final int m = rows(B);
        final int n = cols(B);
        final int an = cols(A);
        final double[] a = loadMat(A);
        final double[] b = loadMat(B);
        final double[] out = new double[b.length];
        final double[] av = new double[2];
        final double[] acc = new double[2];
        final double[] t = new double[2];
        final int k = (side == LEFT) ? m : n;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                acc[0] = 0;
                acc[1] = 0;
                for (int p = 0; p < k; p++) {
                    if (side == LEFT) {
                        triAt(a, an, uplo, transA, diag, i, p, av);
                        cmulAcc(acc, av[0], av[1], b[(p * n + j) * 2], b[(p * n + j) * 2 + 1]);
                    } else {
                        triAt(a, an, uplo, transA, diag, p, j, av);
                        cmulAcc(acc, b[(i * n + p) * 2], b[(i * n + p) * 2 + 1], av[0], av[1]);
                    }
                }
                cmulInto(t, alpha[0], alpha[1], acc[0], acc[1]);
                out[(i * n + j) * 2] = t[0];
                out[(i * n + j) * 2 + 1] = t[1];
            }
        }
        storeMat(B, out);
    }

    /**
     * op(A) at (i, j) for a triangular A, honouring Uplo, the transpose and the
     * unit-diagonal flag. Everything outside the referenced triangle reads as
     * zero, which is what makes a triangular matrix expressible as a full one.
     */
    private static void triAt(double[] a, int n, int uplo, int trans, int diag,
                              int i, int j, double[] out) {
        int r = i;
        int c = j;
        if (trans != NO_TRANSPOSE) {
            r = j;
            c = i;
        }
        if (r == c) {
            if (diag == UNIT) {
                out[0] = 1.0;
                out[1] = 0.0;
                return;
            }
        }
        final boolean stored = (uplo == UPPER) ? (c >= r) : (c <= r);
        if (!stored) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        final int o = (r * n + c) * 2;
        out[0] = a[o];
        out[1] = (trans == CONJ_TRANSPOSE) ? -a[o + 1] : a[o + 1];
    }

    /**
     * Solves op(A) * X = alpha * B (LEFT) or X * op(A) = alpha * B (RIGHT) for
     * X, in place in B. A is triangular, so this is forward or back
     * substitution, not a factorisation.
     */
    static void trsm(int side, int uplo, int transA, int diag, double[] alpha,
                     Allocation A, Allocation B) {
        final int m = rows(B);
        final int n = cols(B);
        final int an = cols(A);
        final double[] a = loadMat(A);
        final double[] b = loadMat(B);
        final double[] t = new double[2];
        final double[] av = new double[2];
        final double[] q = new double[2];

        // Scale by alpha first, exactly as the reference does.
        for (int i = 0; i < m * n; i++) {
            cmulInto(t, alpha[0], alpha[1], b[i * 2], b[i * 2 + 1]);
            b[i * 2] = t[0];
            b[i * 2 + 1] = t[1];
        }

        // Whether op(A) is upper or lower after the transpose.
        final boolean opUpper = (transA == NO_TRANSPOSE) == (uplo == UPPER);

        if (side == LEFT) {
            // op(A) is m x m; solve column by column.
            for (int j = 0; j < n; j++) {
                if (opUpper) {
                    for (int i = m - 1; i >= 0; i--) {
                        double sr = b[(i * n + j) * 2];
                        double si = b[(i * n + j) * 2 + 1];
                        for (int p = i + 1; p < m; p++) {
                            triAt(a, an, uplo, transA, diag, i, p, av);
                            cmulInto(t, av[0], av[1], b[(p * n + j) * 2], b[(p * n + j) * 2 + 1]);
                            sr -= t[0];
                            si -= t[1];
                        }
                        triAt(a, an, uplo, transA, diag, i, i, av);
                        cdivInto(q, sr, si, av[0], av[1]);
                        b[(i * n + j) * 2] = q[0];
                        b[(i * n + j) * 2 + 1] = q[1];
                    }
                } else {
                    for (int i = 0; i < m; i++) {
                        double sr = b[(i * n + j) * 2];
                        double si = b[(i * n + j) * 2 + 1];
                        for (int p = 0; p < i; p++) {
                            triAt(a, an, uplo, transA, diag, i, p, av);
                            cmulInto(t, av[0], av[1], b[(p * n + j) * 2], b[(p * n + j) * 2 + 1]);
                            sr -= t[0];
                            si -= t[1];
                        }
                        triAt(a, an, uplo, transA, diag, i, i, av);
                        cdivInto(q, sr, si, av[0], av[1]);
                        b[(i * n + j) * 2] = q[0];
                        b[(i * n + j) * 2 + 1] = q[1];
                    }
                }
            }
        } else {
            // op(A) is n x n; solve row by row.
            for (int i = 0; i < m; i++) {
                if (opUpper) {
                    for (int j = 0; j < n; j++) {
                        double sr = b[(i * n + j) * 2];
                        double si = b[(i * n + j) * 2 + 1];
                        for (int p = 0; p < j; p++) {
                            triAt(a, an, uplo, transA, diag, p, j, av);
                            cmulInto(t, b[(i * n + p) * 2], b[(i * n + p) * 2 + 1], av[0], av[1]);
                            sr -= t[0];
                            si -= t[1];
                        }
                        triAt(a, an, uplo, transA, diag, j, j, av);
                        cdivInto(q, sr, si, av[0], av[1]);
                        b[(i * n + j) * 2] = q[0];
                        b[(i * n + j) * 2 + 1] = q[1];
                    }
                } else {
                    for (int j = n - 1; j >= 0; j--) {
                        double sr = b[(i * n + j) * 2];
                        double si = b[(i * n + j) * 2 + 1];
                        for (int p = j + 1; p < n; p++) {
                            triAt(a, an, uplo, transA, diag, p, j, av);
                            cmulInto(t, b[(i * n + p) * 2], b[(i * n + p) * 2 + 1], av[0], av[1]);
                            sr -= t[0];
                            si -= t[1];
                        }
                        triAt(a, an, uplo, transA, diag, j, j, av);
                        cdivInto(q, sr, si, av[0], av[1]);
                        b[(i * n + j) * 2] = q[0];
                        b[(i * n + j) * 2 + 1] = q[1];
                    }
                }
            }
        }
        storeMat(B, b);
    }

    // ---- Level 2 -------------------------------------------------------------

    /**
     * y := alpha * op(A) * x + beta * y, where A is general, banded or
     * symmetric depending on the accessor passed in.
     *
     * @param get supplies A(i, j) for the shape in question
     */
    private interface Elem {
        void at(int i, int j, double[] out);
    }

    private static void mv(Elem get, int rowsOut, int inner, double[] alpha, double[] beta,
                           double[] x, double[] y) {
        final double[] av = new double[2];
        final double[] acc = new double[2];
        final double[] t = new double[2];
        for (int i = 0; i < rowsOut; i++) {
            acc[0] = 0;
            acc[1] = 0;
            for (int j = 0; j < inner; j++) {
                get.at(i, j, av);
                cmulAcc(acc, av[0], av[1], x[j * 2], x[j * 2 + 1]);
            }
            cmulInto(t, beta[0], beta[1], y[i * 2], y[i * 2 + 1]);
            double rr = t[0];
            double ri = t[1];
            cmulInto(t, alpha[0], alpha[1], acc[0], acc[1]);
            y[i * 2] = rr + t[0];
            y[i * 2 + 1] = ri + t[1];
        }
    }

    /** y := alpha * op(A) * x + beta * y for a general matrix. */
    static void gemv(int transA, double[] alpha, Allocation A, Allocation X, int incX,
                     double[] beta, Allocation Y, int incY) {
        final int m = rows(A);
        final int n = cols(A);
        final int xn = (transA == NO_TRANSPOSE) ? n : m;
        final int yn = (transA == NO_TRANSPOSE) ? m : n;
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, xn);
        final double[] y = loadVec(Y, incY, yn);
        mv((i, j, out) -> opAt(a, n, transA, i, j, out), yn, xn, alpha, beta, x, y);
        storeVec(Y, incY, yn, y);
    }

    /**
     * Banded general matrix-vector product.
     *
     * The band is stored ROW BASED: entry (i, j) of the logical matrix lives at
     * A(i, j - i + KL) and exists only for i - KL <= j <= i + KU. Everything
     * else is structurally zero.
     */
    static void gbmv(int transA, int kl, int ku, double[] alpha, Allocation A, Allocation X,
                     int incX, double[] beta, Allocation Y, int incY) {
        final int m = rows(A);
        final int n = cols(A);
        final int xn = (transA == NO_TRANSPOSE) ? n : m;
        final int yn = (transA == NO_TRANSPOSE) ? m : n;
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, xn);
        final double[] y = loadVec(Y, incY, yn);

        final Elem band = (i, j, out) -> {
            int r = i;
            int c = j;
            if (transA != NO_TRANSPOSE) {
                r = j;
                c = i;
            }
            final int bandCol = c - r + kl;
            if (bandCol < 0 || bandCol > kl + ku || bandCol >= n || r >= m) {
                out[0] = 0;
                out[1] = 0;
                return;
            }
            final int o = (r * n + bandCol) * 2;
            out[0] = a[o];
            out[1] = (transA == CONJ_TRANSPOSE) ? -a[o + 1] : a[o + 1];
        };
        mv(band, yn, xn, alpha, beta, x, y);
        storeVec(Y, incY, yn, y);
    }

    /** y := alpha * A * x + beta * y for a symmetric or Hermitian A. */
    static void symv(int uplo, double[] alpha, Allocation A, Allocation X, int incX,
                     double[] beta, Allocation Y, int incY, boolean hermitian) {
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, n);
        final double[] y = loadVec(Y, incY, n);
        mv((i, j, out) -> symAt(a, n, uplo, hermitian, i, j, out), n, n, alpha, beta, x, y);
        storeVec(Y, incY, n, y);
    }

    /**
     * Symmetric or Hermitian BANDED matrix-vector product.
     *
     * Storage is the platform's row-based form: for UPPER, entry (i, j) with
     * j >= i lives at A(i, j - i); for LOWER, entry (i, j) with j <= i lives at
     * A(i, j - i + K). The other triangle is reconstructed by symmetry.
     */
    static void sbmv(int uplo, int k, double[] alpha, Allocation A, Allocation X, int incX,
                     double[] beta, Allocation Y, int incY, boolean hermitian) {
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, n);
        final double[] y = loadVec(Y, incY, n);

        final Elem band = (i, j, out) -> {
            int r = i;
            int c = j;
            boolean conj = false;
            final boolean stored = (uplo == UPPER) ? (c >= r) : (c <= r);
            if (!stored) {
                r = j;
                c = i;
                conj = hermitian;
            }
            if (Math.abs(c - r) > k) {
                out[0] = 0;
                out[1] = 0;
                return;
            }
            final int bandCol = (uplo == UPPER) ? (c - r) : (c - r + k);
            if (bandCol < 0 || bandCol >= n) {
                out[0] = 0;
                out[1] = 0;
                return;
            }
            final int o = (r * n + bandCol) * 2;
            out[0] = a[o];
            if (i == j) {
                out[1] = hermitian ? 0.0 : a[o + 1];
            } else {
                out[1] = conj ? -a[o + 1] : a[o + 1];
            }
        };
        mv(band, n, n, alpha, beta, x, y);
        storeVec(Y, incY, n, y);
    }

    /** y := alpha * A * x + beta * y for a PACKED symmetric or Hermitian A. */
    static void spmv(int uplo, int n, double[] alpha, Allocation Ap, Allocation X, int incX,
                     double[] beta, Allocation Y, int incY, boolean hermitian) {
        final double[] ap = loadVec(Ap, 1, cols(Ap));
        final double[] x = loadVec(X, incX, n);
        final double[] y = loadVec(Y, incY, n);

        final Elem get = (i, j, out) -> {
            final boolean stored = (uplo == UPPER) ? (j >= i) : (j <= i);
            final int r = stored ? i : j;
            final int c = stored ? j : i;
            final int o = packedIndex(uplo, n, r, c) * 2;
            out[0] = ap[o];
            if (i == j) {
                out[1] = hermitian ? 0.0 : ap[o + 1];
            } else {
                out[1] = (!stored && hermitian) ? -ap[o + 1] : ap[o + 1];
            }
        };
        mv(get, n, n, alpha, beta, x, y);
        storeVec(Y, incY, n, y);
    }

    /** x := op(A) * x for a triangular A supplied by the accessor. */
    private static void trmvCore(Elem get, int n, double[] x) {
        final double[] res = new double[n * 2];
        final double[] av = new double[2];
        final double[] acc = new double[2];
        for (int i = 0; i < n; i++) {
            acc[0] = 0;
            acc[1] = 0;
            for (int j = 0; j < n; j++) {
                get.at(i, j, av);
                cmulAcc(acc, av[0], av[1], x[j * 2], x[j * 2 + 1]);
            }
            res[i * 2] = acc[0];
            res[i * 2 + 1] = acc[1];
        }
        System.arraycopy(res, 0, x, 0, res.length);
    }

    /**
     * Solves op(A) * x = b in place. opUpper says which triangle op(A) has
     * AFTER the transpose, which decides whether this is back or forward
     * substitution.
     */
    private static void trsvCore(Elem get, int n, double[] x, boolean opUpper) {
        final double[] av = new double[2];
        final double[] t = new double[2];
        final double[] q = new double[2];
        if (opUpper) {
            for (int i = n - 1; i >= 0; i--) {
                double sr = x[i * 2];
                double si = x[i * 2 + 1];
                for (int j = i + 1; j < n; j++) {
                    get.at(i, j, av);
                    cmulInto(t, av[0], av[1], x[j * 2], x[j * 2 + 1]);
                    sr -= t[0];
                    si -= t[1];
                }
                get.at(i, i, av);
                cdivInto(q, sr, si, av[0], av[1]);
                x[i * 2] = q[0];
                x[i * 2 + 1] = q[1];
            }
        } else {
            for (int i = 0; i < n; i++) {
                double sr = x[i * 2];
                double si = x[i * 2 + 1];
                for (int j = 0; j < i; j++) {
                    get.at(i, j, av);
                    cmulInto(t, av[0], av[1], x[j * 2], x[j * 2 + 1]);
                    sr -= t[0];
                    si -= t[1];
                }
                get.at(i, i, av);
                cdivInto(q, sr, si, av[0], av[1]);
                x[i * 2] = q[0];
                x[i * 2 + 1] = q[1];
            }
        }
    }

    static void trmv(int uplo, int transA, int diag, Allocation A, Allocation X, int incX,
                     boolean solve) {
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, n);
        final Elem get = (i, j, out) -> triAt(a, n, uplo, transA, diag, i, j, out);
        if (solve) {
            trsvCore(get, n, x, (transA == NO_TRANSPOSE) == (uplo == UPPER));
        } else {
            trmvCore(get, n, x);
        }
        storeVec(X, incX, n, x);
    }

    /** Triangular BANDED, same row-based storage as {@link #sbmv}. */
    static void tbmv(int uplo, int transA, int diag, int k, Allocation A, Allocation X,
                     int incX, boolean solve) {
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, n);

        final Elem get = (i, j, out) -> {
            int r = i;
            int c = j;
            if (transA != NO_TRANSPOSE) {
                r = j;
                c = i;
            }
            if (r == c && diag == UNIT) {
                out[0] = 1.0;
                out[1] = 0.0;
                return;
            }
            final boolean stored = (uplo == UPPER) ? (c >= r) : (c <= r);
            if (!stored || Math.abs(c - r) > k) {
                out[0] = 0;
                out[1] = 0;
                return;
            }
            final int bandCol = (uplo == UPPER) ? (c - r) : (c - r + k);
            if (bandCol < 0 || bandCol >= n) {
                out[0] = 0;
                out[1] = 0;
                return;
            }
            final int o = (r * n + bandCol) * 2;
            out[0] = a[o];
            out[1] = (transA == CONJ_TRANSPOSE) ? -a[o + 1] : a[o + 1];
        };

        if (solve) {
            trsvCore(get, n, x, (transA == NO_TRANSPOSE) == (uplo == UPPER));
        } else {
            trmvCore(get, n, x);
        }
        storeVec(X, incX, n, x);
    }

    /** Triangular PACKED. */
    static void tpmv(int uplo, int transA, int diag, int n, Allocation Ap, Allocation X,
                     int incX, boolean solve) {
        final double[] ap = loadVec(Ap, 1, cols(Ap));
        final double[] x = loadVec(X, incX, n);

        final Elem get = (i, j, out) -> {
            int r = i;
            int c = j;
            if (transA != NO_TRANSPOSE) {
                r = j;
                c = i;
            }
            if (r == c && diag == UNIT) {
                out[0] = 1.0;
                out[1] = 0.0;
                return;
            }
            final boolean stored = (uplo == UPPER) ? (c >= r) : (c <= r);
            if (!stored) {
                out[0] = 0;
                out[1] = 0;
                return;
            }
            final int o = packedIndex(uplo, n, r, c) * 2;
            out[0] = ap[o];
            out[1] = (transA == CONJ_TRANSPOSE) ? -ap[o + 1] : ap[o + 1];
        };

        if (solve) {
            trsvCore(get, n, x, (transA == NO_TRANSPOSE) == (uplo == UPPER));
        } else {
            trmvCore(get, n, x);
        }
        storeVec(X, incX, n, x);
    }

    /**
     * A := alpha * x * y' + A. conjY selects GERC (conjugate y) over GER/GERU.
     */
    static void ger(double[] alpha, Allocation X, int incX, Allocation Y, int incY,
                    Allocation A, boolean conjY) {
        final int m = rows(A);
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, m);
        final double[] y = loadVec(Y, incY, n);
        final double[] t = new double[2];
        final double[] u = new double[2];
        for (int i = 0; i < m; i++) {
            cmulInto(u, alpha[0], alpha[1], x[i * 2], x[i * 2 + 1]);
            for (int j = 0; j < n; j++) {
                final double yr = y[j * 2];
                final double yi = conjY ? -y[j * 2 + 1] : y[j * 2 + 1];
                cmulInto(t, u[0], u[1], yr, yi);
                final int o = (i * n + j) * 2;
                a[o] += t[0];
                a[o + 1] += t[1];
            }
        }
        storeMat(A, a);
    }

    /**
     * A := alpha * x * x' + A for a symmetric or Hermitian A, touching only the
     * Uplo triangle. For HER alpha is real and the diagonal stays real.
     */
    static void syr(int uplo, double[] alpha, Allocation X, int incX, Allocation A,
                    boolean hermitian) {
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, n);
        final double[] t = new double[2];
        final double[] u = new double[2];
        for (int i = 0; i < n; i++) {
            cmulInto(u, alpha[0], alpha[1], x[i * 2], x[i * 2 + 1]);
            for (int j = 0; j < n; j++) {
                if (uplo == UPPER ? (j < i) : (j > i)) {
                    continue;
                }
                final double xr = x[j * 2];
                final double xi = hermitian ? -x[j * 2 + 1] : x[j * 2 + 1];
                cmulInto(t, u[0], u[1], xr, xi);
                final int o = (i * n + j) * 2;
                a[o] += t[0];
                a[o + 1] += t[1];
                if (hermitian && i == j) {
                    a[o + 1] = 0.0;
                }
            }
        }
        storeMat(A, a);
    }

    /** The packed form of {@link #syr}. */
    static void spr(int uplo, int n, double[] alpha, Allocation X, int incX, Allocation Ap,
                    boolean hermitian) {
        final double[] ap = loadVec(Ap, 1, cols(Ap));
        final double[] x = loadVec(X, incX, n);
        final double[] t = new double[2];
        final double[] u = new double[2];
        for (int i = 0; i < n; i++) {
            cmulInto(u, alpha[0], alpha[1], x[i * 2], x[i * 2 + 1]);
            for (int j = 0; j < n; j++) {
                if (uplo == UPPER ? (j < i) : (j > i)) {
                    continue;
                }
                final double xr = x[j * 2];
                final double xi = hermitian ? -x[j * 2 + 1] : x[j * 2 + 1];
                cmulInto(t, u[0], u[1], xr, xi);
                final int o = packedIndex(uplo, n, i, j) * 2;
                ap[o] += t[0];
                ap[o + 1] += t[1];
                if (hermitian && i == j) {
                    ap[o + 1] = 0.0;
                }
            }
        }
        storeVec(Ap, 1, cols(Ap), ap);
    }

    /**
     * A := alpha * x * y' + conj?(alpha) * y * x' + A, Uplo triangle only.
     * HER2 conjugates the second alpha and forces a real diagonal.
     */
    static void syr2(int uplo, double[] alpha, Allocation X, int incX, Allocation Y, int incY,
                     Allocation A, boolean hermitian) {
        final int n = cols(A);
        final double[] a = loadMat(A);
        final double[] x = loadVec(X, incX, n);
        final double[] y = loadVec(Y, incY, n);
        rank2(uplo, n, alpha, x, y, hermitian, (i, j, dr, di) -> {
            final int o = (i * n + j) * 2;
            a[o] += dr;
            a[o + 1] += di;
        });
        if (hermitian) {
            for (int i = 0; i < n; i++) {
                a[(i * n + i) * 2 + 1] = 0.0;
            }
        }
        storeMat(A, a);
    }

    /** The packed form of {@link #syr2}. */
    static void spr2(int uplo, int n, double[] alpha, Allocation X, int incX, Allocation Y,
                     int incY, Allocation Ap, boolean hermitian) {
        final double[] ap = loadVec(Ap, 1, cols(Ap));
        final double[] x = loadVec(X, incX, n);
        final double[] y = loadVec(Y, incY, n);
        rank2(uplo, n, alpha, x, y, hermitian, (i, j, dr, di) -> {
            final int o = packedIndex(uplo, n, i, j) * 2;
            ap[o] += dr;
            ap[o + 1] += di;
        });
        if (hermitian) {
            for (int i = 0; i < n; i++) {
                final int o = packedIndex(uplo, n, i, i) * 2;
                ap[o + 1] = 0.0;
            }
        }
        storeVec(Ap, 1, cols(Ap), ap);
    }

    private interface Acc {
        void add(int i, int j, double dr, double di);
    }

    private static void rank2(int uplo, int n, double[] alpha, double[] x, double[] y,
                              boolean hermitian, Acc acc) {
        final double a2r = alpha[0];
        final double a2i = hermitian ? -alpha[1] : alpha[1];
        final double[] t = new double[2];
        final double[] u = new double[2];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (uplo == UPPER ? (j < i) : (j > i)) {
                    continue;
                }
                double dr = 0;
                double di = 0;

                cmulInto(u, alpha[0], alpha[1], x[i * 2], x[i * 2 + 1]);
                cmulInto(t, u[0], u[1], y[j * 2],
                        hermitian ? -y[j * 2 + 1] : y[j * 2 + 1]);
                dr += t[0];
                di += t[1];

                cmulInto(u, a2r, a2i, y[i * 2], y[i * 2 + 1]);
                cmulInto(t, u[0], u[1], x[j * 2],
                        hermitian ? -x[j * 2 + 1] : x[j * 2 + 1]);
                dr += t[0];
                di += t[1];

                acc.add(i, j, dr, di);
            }
        }
    }

    // ---- BNNM ----------------------------------------------------------------

    /**
     * 8-bit quantised GEMM for neural networks: C = A * transpose(B), with per
     * matrix zero-point offsets and a fixed-point output rescale.
     *
     * The accumulation is exact in int; only the final rescale rounds. AOSP
     * describes the format as 1.10.21 with a shift down at the end and the
     * result gated to 0..255, which is what the shift and clamp below do.
     */
    static void bnnm(Allocation A, int aOffset, Allocation B, int bOffset, Allocation C,
                     int cOffset, int cMult) {
        final int m = rows(A);
        final int n = rows(B);
        final int k = cols(A);
        final ByteBuffer ab = IntrinsicSupport.bufferOf(A);
        final ByteBuffer bb = IntrinsicSupport.bufferOf(B);
        final ByteBuffer cb = IntrinsicSupport.bufferOf(C);
        final int aBase = A.baseOffset();
        final int bBase = B.baseOffset();
        final int cBase = C.baseOffset();
        final int aStride = A.rowStride();
        final int bStride = B.rowStride();
        final int cStride = C.rowStride();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                int acc = 0;
                for (int p = 0; p < k; p++) {
                    final int av = (ab.get(aBase + i * aStride + p) & 0xff) - aOffset;
                    final int bv = (bb.get(bBase + j * bStride + p) & 0xff) - bOffset;
                    acc += av * bv;
                }
                long scaled = ((long) (acc + cOffset)) * cMult;
                // Drop the 21 fractional bits, rounding to nearest.
                long out = (scaled + (1L << 20)) >> 21;
                if (out < 0) {
                    out = 0;
                } else if (out > 255) {
                    out = 255;
                }
                cb.put(cBase + i * cStride + j, (byte) out);
            }
        }
    }
}
