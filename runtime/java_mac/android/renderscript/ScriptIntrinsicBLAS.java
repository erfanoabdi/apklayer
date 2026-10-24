package android.renderscript;

/**
 * BLAS: the standard dense linear-algebra kernels over {@link Allocation}
 * storage. Levels 2 and 3 plus BNNM, in single, double, complex and double
 * complex.
 *
 * This is a WORKING implementation, not a set of signatures. Every routine
 * computes its documented result through {@link BlasReference}, and every
 * argument check below is the platform's, reproduced so that a caller who gets
 * a dimension wrong sees the same RSRuntimeException with the same message
 * rather than a wrong answer.
 *
 * CONVENTIONS THAT DIFFER FROM THE NETLIB DOCUMENTATION each method links to:
 *
 *  - Matrices are ROW MAJOR 2D Allocations: dimY is the row count, dimX the
 *    column count. The Fortran reference is column major, so an index taken
 *    from netlib must be transposed before use.
 *  - Vector increments must be POSITIVE. Fortran allows a negative increment to
 *    walk a vector backwards; RenderScript rejects it.
 *  - Banded and packed matrices use RenderScript's own row-based layouts, given
 *    as a conversion loop in the comment on each method that takes one. They
 *    are NOT the LAPACK column layouts.
 *
 * A NOTE ON WHERE THE MATH RUNS. macOS ships a BLAS in Accelerate.framework,
 * and the project's decision ladder says to prefer a host-native facility.
 * Reaching it needs a JNI binding outside this package, so for now the kernels
 * are a reference implementation in Java (see BlasReference), computed in
 * double and narrowed on store. An F32 routine is therefore slightly MORE
 * accurate than a device's, never less. Re-pointing at Accelerate later would
 * not change any signature or contract here.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicBLAS.java
 */
public final class ScriptIntrinsicBLAS extends ScriptIntrinsic {

    private ScriptIntrinsicBLAS(RenderScript rs) {
        super(rs);
    }

    public static ScriptIntrinsicBLAS create(RenderScript rs) {
        return new ScriptIntrinsicBLAS(rs);
    }

    /** op(A) = A. */
    public static final int NO_TRANSPOSE = 111;
    /** op(A) = A**T. */
    public static final int TRANSPOSE = 112;
    /** op(A) = A**H, the conjugate transpose. */
    public static final int CONJ_TRANSPOSE = 113;

    /** Reference the upper triangle. */
    public static final int UPPER = 121;
    /** Reference the lower triangle. */
    public static final int LOWER = 122;

    /** The diagonal is stored and used. */
    public static final int NON_UNIT = 131;
    /** The diagonal is assumed to be all ones and is not referenced. */
    public static final int UNIT = 132;

    /** The special matrix multiplies from the left. */
    public static final int LEFT = 141;
    /** The special matrix multiplies from the right. */
    public static final int RIGHT = 142;

    // ---- argument validation, mirrored from AOSP ----------------------------

    static void validateSide(int side) {
        if (side != LEFT && side != RIGHT) {
            throw new RSRuntimeException("Invalid side passed to BLAS");
        }
    }

    static void validateTranspose(int trans) {
        if (trans != NO_TRANSPOSE && trans != TRANSPOSE && trans != CONJ_TRANSPOSE) {
            throw new RSRuntimeException("Invalid transpose passed to BLAS");
        }
    }

    /** The Hermitian routines accept only NO_TRANSPOSE and CONJ_TRANSPOSE. */
    static void validateConjTranspose(int trans) {
        if (trans != NO_TRANSPOSE && trans != CONJ_TRANSPOSE) {
            throw new RSRuntimeException("Invalid transpose passed to BLAS");
        }
    }

    static void validateDiag(int diag) {
        if (diag != NON_UNIT && diag != UNIT) {
            throw new RSRuntimeException("Invalid diag passed to BLAS");
        }
    }

    static void validateUplo(int uplo) {
        if (uplo != UPPER && uplo != LOWER) {
            throw new RSRuntimeException("Invalid uplo passed to BLAS");
        }
    }

    private static int dimY(Allocation a) {
        return Math.max(1, a.getType().getY());
    }

    static void validateGEMV(Element e, int transA, Allocation a, Allocation x, int incX,
                             Allocation y, int incY) {
        validateTranspose(transA);
        int m = dimY(a);
        int n = a.getType().getX();
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        int expectedXDim;
        int expectedYDim;
        if (transA == NO_TRANSPOSE) {
            expectedXDim = 1 + (n - 1) * incX;
            expectedYDim = 1 + (m - 1) * incY;
        } else {
            expectedXDim = 1 + (m - 1) * incX;
            expectedYDim = 1 + (n - 1) * incY;
        }
        if (x.getType().getX() != expectedXDim || y.getType().getX() != expectedYDim) {
            throw new RSRuntimeException("Incorrect vector dimensions for GEMV");
        }
    }

    static void validateTRMV(Element e, int uplo, int transA, int diag, Allocation a,
                             Allocation x, int incX) {
        validateTranspose(transA);
        validateUplo(uplo);
        validateDiag(diag);
        int n = dimY(a);
        if (a.getType().getX() != n) {
            throw new RSRuntimeException("A must be a square matrix for TRMV");
        }
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (incX <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for TRMV");
        }
    }

    /** @return N, derived from the packed length N*(N+1)/2. */
    static int validateTPMV(Element e, int uplo, int transA, int diag, Allocation ap,
                            Allocation x, int incX) {
        validateTranspose(transA);
        validateUplo(uplo);
        validateDiag(diag);
        if (!ap.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (ap.getType().getY() > 1) {
            throw new RSRuntimeException("Ap must have a Y dimension of 0 or 1");
        }
        int n = (int) Math.sqrt((double) ap.getType().getX() * 2);
        if (ap.getType().getX() != ((n * (n + 1)) / 2)) {
            throw new RSRuntimeException("Invalid dimension for Ap");
        }
        if (incX <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for TPMV");
        }
        return n;
    }

    static int validateSYMV(Element e, int uplo, Allocation a, Allocation x, Allocation y,
                            int incX, int incY) {
        validateUplo(uplo);
        int n = dimY(a);
        if (a.getType().getX() != n) {
            throw new RSRuntimeException("A must be a square matrix for SYMV");
        }
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for SYMV");
        }
        if (y.getType().getX() != 1 + (n - 1) * incY) {
            throw new RSRuntimeException("Incorrect vector dimensions for SYMV");
        }
        return n;
    }

    static int validateSPMV(Element e, int uplo, Allocation ap, Allocation x, int incX,
                            Allocation y, int incY) {
        validateUplo(uplo);
        if (!ap.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (ap.getType().getY() > 1) {
            throw new RSRuntimeException("Ap must have a Y dimension of 0 or 1");
        }
        int n = (int) Math.sqrt((double) ap.getType().getX() * 2);
        if (ap.getType().getX() != ((n * (n + 1)) / 2)) {
            throw new RSRuntimeException("Invalid dimension for Ap");
        }
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for SPMV");
        }
        if (y.getType().getX() != 1 + (n - 1) * incY) {
            throw new RSRuntimeException("Incorrect vector dimensions for SPMV");
        }
        return n;
    }

    static void validateGER(Element e, Allocation x, int incX, Allocation y, int incY,
                            Allocation a) {
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        int m = dimY(a);
        int n = a.getType().getX();
        if (n < 1 || m < 1) {
            throw new RSRuntimeException("M and N must be 1 or greater for GER");
        }
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (m - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for GER");
        }
        if (y.getType().getX() != 1 + (n - 1) * incY) {
            throw new RSRuntimeException("Incorrect vector dimensions for GER");
        }
    }

    static int validateSYR(Element e, int uplo, Allocation x, int incX, Allocation a) {
        validateUplo(uplo);
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        int n = a.getType().getX();
        if (x.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (n != dimY(a)) {
            throw new RSRuntimeException("A must be a symmetric matrix");
        }
        if (incX <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for SYR");
        }
        return n;
    }

    static int validateSPR(Element e, int uplo, Allocation x, int incX, Allocation ap) {
        validateUplo(uplo);
        if (!ap.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (ap.getType().getY() > 1) {
            throw new RSRuntimeException("Ap must have a Y dimension of 0 or 1");
        }
        int n = (int) Math.sqrt((double) ap.getType().getX() * 2);
        if (ap.getType().getX() != ((n * (n + 1)) / 2)) {
            throw new RSRuntimeException("Invalid dimension for Ap");
        }
        if (incX <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for SPR");
        }
        return n;
    }

    static int validateSYR2(Element e, int uplo, Allocation x, int incX, Allocation y,
                            int incY, Allocation a) {
        validateUplo(uplo);
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        int n = a.getType().getX();
        if (n != dimY(a)) {
            throw new RSRuntimeException("A must be a symmetric matrix");
        }
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX
                || y.getType().getX() != 1 + (n - 1) * incY) {
            throw new RSRuntimeException("Incorrect vector dimensions for SYR");
        }
        return n;
    }

    static int validateSPR2(Element e, int uplo, Allocation x, int incX, Allocation y,
                            int incY, Allocation ap) {
        validateUplo(uplo);
        if (!ap.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        if (ap.getType().getY() > 1) {
            throw new RSRuntimeException("Ap must have a Y dimension of 0 or 1");
        }
        int n = (int) Math.sqrt((double) ap.getType().getX() * 2);
        if (ap.getType().getX() != ((n * (n + 1)) / 2)) {
            throw new RSRuntimeException("Invalid dimension for Ap");
        }
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (n - 1) * incX
                || y.getType().getX() != 1 + (n - 1) * incY) {
            throw new RSRuntimeException("Incorrect vector dimensions for SPR2");
        }
        return n;
    }

    /**
     * The shared Level 3 shape check. A null A or B means the routine takes
     * fewer matrices (SYRK passes B as null), and Side == RIGHT deliberately
     * reads A's dimensions off B and vice versa, which is how AOSP expresses
     * "the special matrix is on the right".
     */
    static void validateL3(Element e, int transA, int transB, int side, Allocation a,
                           Allocation b, Allocation c) {
        int aM = -1;
        int aN = -1;
        int bM = -1;
        int bN = -1;
        int cM;
        int cN;
        if ((a != null && !a.getType().getElement().isCompatible(e))
                || (b != null && !b.getType().getElement().isCompatible(e))
                || (c != null && !c.getType().getElement().isCompatible(e))) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (c == null) {
            throw new RSRuntimeException("Allocation C cannot be null");
        }
        cM = dimY(c);
        cN = c.getType().getX();

        if (side == RIGHT) {
            if ((a == null && b != null) || (a != null && b == null)) {
                throw new RSRuntimeException("Provided Matrix A without Matrix B, or vice versa");
            }
            if (b != null) {
                bM = dimY(a);
                bN = a.getType().getX();
            }
            if (a != null) {
                aM = dimY(b);
                aN = b.getType().getX();
            }
        } else {
            if (a != null) {
                if (transA == TRANSPOSE || transA == CONJ_TRANSPOSE) {
                    aN = dimY(a);
                    aM = a.getType().getX();
                } else {
                    aM = dimY(a);
                    aN = a.getType().getX();
                }
            }
            if (b != null) {
                if (transB == TRANSPOSE || transB == CONJ_TRANSPOSE) {
                    bN = dimY(b);
                    bM = b.getType().getX();
                } else {
                    bM = dimY(b);
                    bN = b.getType().getX();
                }
            }
        }
        if (a != null && b != null) {
            if (aN != bM || aM != cM || bN != cN) {
                throw new RSRuntimeException("Called BLAS with invalid dimensions");
            }
        } else if (a != null) {
            if (cM != cN) {
                throw new RSRuntimeException("Matrix C is not symmetric");
            }
            if (aM != cM) {
                throw new RSRuntimeException("Called BLAS with invalid dimensions");
            }
        }
    }

    static void validateSYR2K(Element e, int trans, Allocation a, Allocation b, Allocation c) {
        validateTranspose(trans);
        if (!a.getType().getElement().isCompatible(e)
                || !b.getType().getElement().isCompatible(e)
                || !c.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        int cdim = (trans == TRANSPOSE) ? a.getType().getX() : dimY(a);
        if (c.getType().getX() != cdim || dimY(c) != cdim) {
            throw new RSRuntimeException("Invalid symmetric matrix in SYR2K");
        }
        if (a.getType().getX() != b.getType().getX() || dimY(a) != dimY(b)) {
            throw new RSRuntimeException("Invalid A and B in SYR2K");
        }
    }

    static void validateTRMM(Element e, int side, int transA, Allocation a, Allocation b) {
        validateSide(side);
        validateTranspose(transA);
        if (!a.getType().getElement().isCompatible(e)
                || !b.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        int aM = dimY(a);
        int aN = a.getType().getX();
        if (aM != aN) {
            throw new RSRuntimeException("Called TRMM with a non-symmetric matrix A");
        }
        int bM = dimY(b);
        int bN = b.getType().getX();
        if (side == LEFT) {
            if (aN != bM) {
                throw new RSRuntimeException("Called TRMM with invalid matrices");
            }
        } else {
            if (bN != aM) {
                throw new RSRuntimeException("Called TRMM with invalid matrices");
            }
        }
    }

    static void validateTRSM(Element e, int side, int transA, Allocation a, Allocation b) {
        validateSide(side);
        validateTranspose(transA);
        if (!a.getType().getElement().isCompatible(e)
                || !b.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        int adim = a.getType().getX();
        if (adim != dimY(a)) {
            throw new RSRuntimeException("Called TRSM with a non-symmetric matrix A");
        }
        int bM = dimY(b);
        int bN = b.getType().getX();
        if (side == LEFT) {
            if (adim != bM) {
                throw new RSRuntimeException("Called TRSM with invalid matrix dimensions");
            }
        } else {
            if (adim != bN) {
                throw new RSRuntimeException("Called TRSM with invalid matrix dimensions");
            }
        }
    }

    static void validateHEMM(Element e, int side, Allocation a, Allocation b, Allocation c) {
        validateSide(side);
        if (!a.getType().getElement().isCompatible(e)
                || !b.getType().getElement().isCompatible(e)
                || !c.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        int adim = a.getType().getX();
        if (adim != dimY(a)) {
            throw new RSRuntimeException("Called HEMM with non-square A");
        }
        if ((side == LEFT && adim != dimY(b)) || (side == RIGHT && adim != b.getType().getX())) {
            throw new RSRuntimeException("Called HEMM with invalid B");
        }
        if (b.getType().getX() != c.getType().getX() || dimY(b) != dimY(c)) {
            throw new RSRuntimeException("Called HEMM with mismatched B and C");
        }
    }

    static void validateHERK(Element e, int trans, Allocation a, Allocation c) {
        if (!a.getType().getElement().isCompatible(e)
                || !c.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        validateConjTranspose(trans);
        int cdim = c.getType().getX();
        if (cdim != dimY(c)) {
            throw new RSRuntimeException("Called HERK with non-square C");
        }
        if (trans == NO_TRANSPOSE) {
            if (cdim != dimY(a)) {
                throw new RSRuntimeException("Called HERK with invalid A");
            }
        } else {
            if (cdim != a.getType().getX()) {
                throw new RSRuntimeException("Called HERK with invalid A");
            }
        }
    }

    static void validateHER2K(Element e, int trans, Allocation a, Allocation b, Allocation c) {
        if (!a.getType().getElement().isCompatible(e)
                || !b.getType().getElement().isCompatible(e)
                || !c.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        validateConjTranspose(trans);
        int cdim = c.getType().getX();
        if (cdim != dimY(c)) {
            throw new RSRuntimeException("Called HER2K with non-square C");
        }
        if (trans == NO_TRANSPOSE) {
            if (dimY(a) != cdim) {
                throw new RSRuntimeException("Called HER2K with invalid matrices");
            }
        } else {
            if (a.getType().getX() != cdim) {
                throw new RSRuntimeException("Called HER2K with invalid matrices");
            }
        }
        if (a.getType().getX() != b.getType().getX() || dimY(a) != dimY(b)) {
            throw new RSRuntimeException("Called HER2K with invalid A and B matrices");
        }
    }

    /** y := alpha*op(A)*x + beta*y. */
    public void SGEMV(int TransA, float alpha, Allocation A, Allocation X, int incX, float beta,
            Allocation Y, int incY) {
        validateGEMV(Element.F32(mRS), TransA, A, X, incX, Y, incY);
        BlasReference.gemv(TransA, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY);
    }

    /** Banded y := alpha*op(A)*x + beta*y. Band entry (i, j) lives at A(i, j - i + KL). */
    public void SGBMV(int TransA, int KL, int KU, float alpha, Allocation A, Allocation X, int incX,
            float beta, Allocation Y, int incY) {
        validateGEMV(Element.F32(mRS), TransA, A, X, incX, Y, incY);
        if (KL < 0 || KU < 0) {
            throw new RSRuntimeException("KL and KU must be greater than or equal to 0");
        }
        BlasReference.gbmv(TransA, KL, KU, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY);
    }

    /** x := op(A)*x for a triangular A. */
    public void STRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F32(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, false);
    }

    /** Triangular banded x := op(A)*x. UPPER stores (i, j) at A(i, j - i); LOWER at A(i, j - i + K). */
    public void STBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F32(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, false);
    }

    /** Triangular packed x := op(A)*x. Ap is 1D of length N*(N+1)/2. */
    public void STPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F32(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, false);
    }

    /** Solves op(A)*x = b in place, by substitution. */
    public void STRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F32(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, true);
    }

    /** Solves a triangular BANDED op(A)*x = b in place. */
    public void STBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F32(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, true);
    }

    /** Solves a triangular PACKED op(A)*x = b in place. */
    public void STPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F32(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, true);
    }

    /** y := alpha*op(A)*x + beta*y. */
    public void DGEMV(int TransA, double alpha, Allocation A, Allocation X, int incX, double beta,
            Allocation Y, int incY) {
        validateGEMV(Element.F64(mRS), TransA, A, X, incX, Y, incY);
        BlasReference.gemv(TransA, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY);
    }

    /** Banded y := alpha*op(A)*x + beta*y. Band entry (i, j) lives at A(i, j - i + KL). */
    public void DGBMV(int TransA, int KL, int KU, double alpha, Allocation A, Allocation X, int incX,
            double beta, Allocation Y, int incY) {
        validateGEMV(Element.F64(mRS), TransA, A, X, incX, Y, incY);
        if (KL < 0 || KU < 0) {
            throw new RSRuntimeException("KL and KU must be greater than or equal to 0");
        }
        BlasReference.gbmv(TransA, KL, KU, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY);
    }

    /** x := op(A)*x for a triangular A. */
    public void DTRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F64(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, false);
    }

    /** Triangular banded x := op(A)*x. UPPER stores (i, j) at A(i, j - i); LOWER at A(i, j - i + K). */
    public void DTBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F64(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, false);
    }

    /** Triangular packed x := op(A)*x. Ap is 1D of length N*(N+1)/2. */
    public void DTPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F64(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, false);
    }

    /** Solves op(A)*x = b in place, by substitution. */
    public void DTRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F64(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, true);
    }

    /** Solves a triangular BANDED op(A)*x = b in place. */
    public void DTBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F64(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, true);
    }

    /** Solves a triangular PACKED op(A)*x = b in place. */
    public void DTPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F64(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, true);
    }

    /** y := alpha*op(A)*x + beta*y. */
    public void CGEMV(int TransA, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta,
            Allocation Y, int incY) {
        validateGEMV(Element.F32_2(mRS), TransA, A, X, incX, Y, incY);
        BlasReference.gemv(TransA, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY);
    }

    /** Banded y := alpha*op(A)*x + beta*y. Band entry (i, j) lives at A(i, j - i + KL). */
    public void CGBMV(int TransA, int KL, int KU, Float2 alpha, Allocation A, Allocation X, int incX,
            Float2 beta, Allocation Y, int incY) {
        validateGEMV(Element.F32_2(mRS), TransA, A, X, incX, Y, incY);
        if (KL < 0 || KU < 0) {
            throw new RSRuntimeException("KL and KU must be greater than or equal to 0");
        }
        BlasReference.gbmv(TransA, KL, KU, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY);
    }

    /** x := op(A)*x for a triangular A. */
    public void CTRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F32_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, false);
    }

    /** Triangular banded x := op(A)*x. UPPER stores (i, j) at A(i, j - i); LOWER at A(i, j - i + K). */
    public void CTBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F32_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, false);
    }

    /** Triangular packed x := op(A)*x. Ap is 1D of length N*(N+1)/2. */
    public void CTPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F32_2(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, false);
    }

    /** Solves op(A)*x = b in place, by substitution. */
    public void CTRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F32_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, true);
    }

    /** Solves a triangular BANDED op(A)*x = b in place. */
    public void CTBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F32_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, true);
    }

    /** Solves a triangular PACKED op(A)*x = b in place. */
    public void CTPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F32_2(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, true);
    }

    /** y := alpha*op(A)*x + beta*y. */
    public void ZGEMV(int TransA, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta,
            Allocation Y, int incY) {
        validateGEMV(Element.F64_2(mRS), TransA, A, X, incX, Y, incY);
        BlasReference.gemv(TransA, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY);
    }

    /** Banded y := alpha*op(A)*x + beta*y. Band entry (i, j) lives at A(i, j - i + KL). */
    public void ZGBMV(int TransA, int KL, int KU, Double2 alpha, Allocation A, Allocation X, int incX,
            Double2 beta, Allocation Y, int incY) {
        validateGEMV(Element.F64_2(mRS), TransA, A, X, incX, Y, incY);
        if (KL < 0 || KU < 0) {
            throw new RSRuntimeException("KL and KU must be greater than or equal to 0");
        }
        BlasReference.gbmv(TransA, KL, KU, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY);
    }

    /** x := op(A)*x for a triangular A. */
    public void ZTRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F64_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, false);
    }

    /** Triangular banded x := op(A)*x. UPPER stores (i, j) at A(i, j - i); LOWER at A(i, j - i + K). */
    public void ZTBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F64_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, false);
    }

    /** Triangular packed x := op(A)*x. Ap is 1D of length N*(N+1)/2. */
    public void ZTPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F64_2(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, false);
    }

    /** Solves op(A)*x = b in place, by substitution. */
    public void ZTRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        validateTRMV(Element.F64_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.trmv(Uplo, TransA, Diag, A, X, incX, true);
    }

    /** Solves a triangular BANDED op(A)*x = b in place. */
    public void ZTBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateTRMV(Element.F64_2(mRS), Uplo, TransA, Diag, A, X, incX);
        BlasReference.tbmv(Uplo, TransA, Diag, K, A, X, incX, true);
    }

    /** Solves a triangular PACKED op(A)*x = b in place. */
    public void ZTPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        int n = validateTPMV(Element.F64_2(mRS), Uplo, TransA, Diag, Ap, X, incX);
        BlasReference.tpmv(Uplo, TransA, Diag, n, Ap, X, incX, true);
    }

    /** y := alpha*A*x + beta*y for a symmetric A held in one triangle. */
    public void SSYMV(int Uplo, float alpha, Allocation A, Allocation X, int incX, float beta,
            Allocation Y, int incY) {
        validateSYMV(Element.F32(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.symv(Uplo, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY, false);
    }

    /** Symmetric banded y := alpha*A*x + beta*y. */
    public void SSBMV(int Uplo, int K, float alpha, Allocation A, Allocation X, int incX, float beta,
            Allocation Y, int incY) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateSYMV(Element.F32(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.sbmv(Uplo, K, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY, false);
    }

    /** Symmetric packed y := alpha*A*x + beta*y. */
    public void SSPMV(int Uplo, float alpha, Allocation Ap, Allocation X, int incX, float beta,
            Allocation Y, int incY) {
        int n = validateSPMV(Element.F32(mRS), Uplo, Ap, X, incX, Y, incY);
        BlasReference.spmv(Uplo, n, new double[] {alpha, 0}, Ap, X, incX, new double[] {beta, 0}, Y, incY, false);
    }

    /** Rank 1 update A := alpha*x*y**T + A. */
    public void SGER(float alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateGER(Element.F32(mRS), X, incX, Y, incY, A);
        BlasReference.ger(new double[] {alpha, 0}, X, incX, Y, incY, A, false);
    }

    /** Symmetric rank 1 update A := alpha*x*x**T + A, Uplo triangle only. */
    public void SSYR(int Uplo, float alpha, Allocation X, int incX, Allocation A) {
        validateSYR(Element.F32(mRS), Uplo, X, incX, A);
        BlasReference.syr(Uplo, new double[] {alpha, 0}, X, incX, A, false);
    }

    /** Packed symmetric rank 1 update. */
    public void SSPR(int Uplo, float alpha, Allocation X, int incX, Allocation Ap) {
        int n = validateSPR(Element.F32(mRS), Uplo, X, incX, Ap);
        BlasReference.spr(Uplo, n, new double[] {alpha, 0}, X, incX, Ap, false);
    }

    /** Symmetric rank 2 update A := alpha*x*y**T + alpha*y*x**T + A. */
    public void SSYR2(int Uplo, float alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation A) {
        validateSYR2(Element.F32(mRS), Uplo, X, incX, Y, incY, A);
        BlasReference.syr2(Uplo, new double[] {alpha, 0}, X, incX, Y, incY, A, false);
    }

    /** Packed symmetric rank 2 update. */
    public void SSPR2(int Uplo, float alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation Ap) {
        int n = validateSPR2(Element.F32(mRS), Uplo, X, incX, Y, incY, Ap);
        BlasReference.spr2(Uplo, n, new double[] {alpha, 0}, X, incX, Y, incY, Ap, false);
    }

    /** y := alpha*A*x + beta*y for a symmetric A held in one triangle. */
    public void DSYMV(int Uplo, double alpha, Allocation A, Allocation X, int incX, double beta,
            Allocation Y, int incY) {
        validateSYMV(Element.F64(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.symv(Uplo, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY, false);
    }

    /** Symmetric banded y := alpha*A*x + beta*y. */
    public void DSBMV(int Uplo, int K, double alpha, Allocation A, Allocation X, int incX, double beta,
            Allocation Y, int incY) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateSYMV(Element.F64(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.sbmv(Uplo, K, new double[] {alpha, 0}, A, X, incX, new double[] {beta, 0}, Y, incY, false);
    }

    /** Symmetric packed y := alpha*A*x + beta*y. */
    public void DSPMV(int Uplo, double alpha, Allocation Ap, Allocation X, int incX, double beta,
            Allocation Y, int incY) {
        int n = validateSPMV(Element.F64(mRS), Uplo, Ap, X, incX, Y, incY);
        BlasReference.spmv(Uplo, n, new double[] {alpha, 0}, Ap, X, incX, new double[] {beta, 0}, Y, incY, false);
    }

    /** Rank 1 update A := alpha*x*y**T + A. */
    public void DGER(double alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateGER(Element.F64(mRS), X, incX, Y, incY, A);
        BlasReference.ger(new double[] {alpha, 0}, X, incX, Y, incY, A, false);
    }

    /** Symmetric rank 1 update A := alpha*x*x**T + A, Uplo triangle only. */
    public void DSYR(int Uplo, double alpha, Allocation X, int incX, Allocation A) {
        validateSYR(Element.F64(mRS), Uplo, X, incX, A);
        BlasReference.syr(Uplo, new double[] {alpha, 0}, X, incX, A, false);
    }

    /** Packed symmetric rank 1 update. */
    public void DSPR(int Uplo, double alpha, Allocation X, int incX, Allocation Ap) {
        int n = validateSPR(Element.F64(mRS), Uplo, X, incX, Ap);
        BlasReference.spr(Uplo, n, new double[] {alpha, 0}, X, incX, Ap, false);
    }

    /** Symmetric rank 2 update A := alpha*x*y**T + alpha*y*x**T + A. */
    public void DSYR2(int Uplo, double alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation A) {
        validateSYR2(Element.F64(mRS), Uplo, X, incX, Y, incY, A);
        BlasReference.syr2(Uplo, new double[] {alpha, 0}, X, incX, Y, incY, A, false);
    }

    /** Packed symmetric rank 2 update. */
    public void DSPR2(int Uplo, double alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation Ap) {
        int n = validateSPR2(Element.F64(mRS), Uplo, X, incX, Y, incY, Ap);
        BlasReference.spr2(Uplo, n, new double[] {alpha, 0}, X, incX, Y, incY, Ap, false);
    }

    /** y := alpha*A*x + beta*y for a Hermitian A. The stored diagonal's imaginary part is ignored. */
    public void CHEMV(int Uplo, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta,
            Allocation Y, int incY) {
        validateSYMV(Element.F32_2(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.symv(Uplo, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY, true);
    }

    /** Hermitian banded y := alpha*A*x + beta*y. */
    public void CHBMV(int Uplo, int K, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta,
            Allocation Y, int incY) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateSYMV(Element.F32_2(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.sbmv(Uplo, K, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY, true);
    }

    /** Hermitian packed y := alpha*A*x + beta*y. */
    public void CHPMV(int Uplo, Float2 alpha, Allocation Ap, Allocation X, int incX, Float2 beta,
            Allocation Y, int incY) {
        int n = validateSPMV(Element.F32_2(mRS), Uplo, Ap, X, incX, Y, incY);
        BlasReference.spmv(Uplo, n, new double[] {alpha.x, alpha.y}, Ap, X, incX, new double[] {beta.x, beta.y}, Y, incY, true);
    }

    /** Rank 1 update A := alpha*x*y**T + A, WITHOUT conjugating y. */
    public void CGERU(Float2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateGERU(Element.F32_2(mRS), X, incX, Y, incY, A);
        BlasReference.ger(new double[] {alpha.x, alpha.y}, X, incX, Y, incY, A, false);
    }

    /** Rank 1 update A := alpha*x*y**H + A, conjugating y. */
    public void CGERC(Float2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateGERU(Element.F32_2(mRS), X, incX, Y, incY, A);
        BlasReference.ger(new double[] {alpha.x, alpha.y}, X, incX, Y, incY, A, true);
    }

    /** Hermitian rank 1 update A := alpha*x*x**H + A. alpha is REAL. */
    public void CHER(int Uplo, float alpha, Allocation X, int incX, Allocation A) {
        validateSYR(Element.F32_2(mRS), Uplo, X, incX, A);
        BlasReference.syr(Uplo, new double[] {alpha, 0}, X, incX, A, true);
    }

    /** Packed Hermitian rank 1 update. alpha is REAL. */
    public void CHPR(int Uplo, float alpha, Allocation X, int incX, Allocation Ap) {
        int n = validateSPR(Element.F32_2(mRS), Uplo, X, incX, Ap);
        BlasReference.spr(Uplo, n, new double[] {alpha, 0}, X, incX, Ap, true);
    }

    /** Hermitian rank 2 update A := alpha*x*y**H + conj(alpha)*y*x**H + A. */
    public void CHER2(int Uplo, Float2 alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation A) {
        validateSYR2(Element.F32_2(mRS), Uplo, X, incX, Y, incY, A);
        BlasReference.syr2(Uplo, new double[] {alpha.x, alpha.y}, X, incX, Y, incY, A, true);
    }

    /** Packed Hermitian rank 2 update. */
    public void CHPR2(int Uplo, Float2 alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation Ap) {
        int n = validateSPR2(Element.F32_2(mRS), Uplo, X, incX, Y, incY, Ap);
        BlasReference.spr2(Uplo, n, new double[] {alpha.x, alpha.y}, X, incX, Y, incY, Ap, true);
    }

    /** y := alpha*A*x + beta*y for a Hermitian A. The stored diagonal's imaginary part is ignored. */
    public void ZHEMV(int Uplo, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta,
            Allocation Y, int incY) {
        validateSYMV(Element.F64_2(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.symv(Uplo, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY, true);
    }

    /** Hermitian banded y := alpha*A*x + beta*y. */
    public void ZHBMV(int Uplo, int K, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta,
            Allocation Y, int incY) {
        if (K < 0) {
            throw new RSRuntimeException("K must be greater than or equal to 0");
        }
        validateSYMV(Element.F64_2(mRS), Uplo, A, X, Y, incX, incY);
        BlasReference.sbmv(Uplo, K, new double[] {alpha.x, alpha.y}, A, X, incX, new double[] {beta.x, beta.y}, Y, incY, true);
    }

    /** Hermitian packed y := alpha*A*x + beta*y. */
    public void ZHPMV(int Uplo, Double2 alpha, Allocation Ap, Allocation X, int incX, Double2 beta,
            Allocation Y, int incY) {
        int n = validateSPMV(Element.F64_2(mRS), Uplo, Ap, X, incX, Y, incY);
        BlasReference.spmv(Uplo, n, new double[] {alpha.x, alpha.y}, Ap, X, incX, new double[] {beta.x, beta.y}, Y, incY, true);
    }

    /** Rank 1 update A := alpha*x*y**T + A, WITHOUT conjugating y. */
    public void ZGERU(Double2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateGERU(Element.F64_2(mRS), X, incX, Y, incY, A);
        BlasReference.ger(new double[] {alpha.x, alpha.y}, X, incX, Y, incY, A, false);
    }

    /** Rank 1 update A := alpha*x*y**H + A, conjugating y. */
    public void ZGERC(Double2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateGERU(Element.F64_2(mRS), X, incX, Y, incY, A);
        BlasReference.ger(new double[] {alpha.x, alpha.y}, X, incX, Y, incY, A, true);
    }

    /** Hermitian rank 1 update A := alpha*x*x**H + A. alpha is REAL. */
    public void ZHER(int Uplo, double alpha, Allocation X, int incX, Allocation A) {
        validateSYR(Element.F64_2(mRS), Uplo, X, incX, A);
        BlasReference.syr(Uplo, new double[] {alpha, 0}, X, incX, A, true);
    }

    /** Packed Hermitian rank 1 update. alpha is REAL. */
    public void ZHPR(int Uplo, double alpha, Allocation X, int incX, Allocation Ap) {
        int n = validateSPR(Element.F64_2(mRS), Uplo, X, incX, Ap);
        BlasReference.spr(Uplo, n, new double[] {alpha, 0}, X, incX, Ap, true);
    }

    /** Hermitian rank 2 update A := alpha*x*y**H + conj(alpha)*y*x**H + A. */
    public void ZHER2(int Uplo, Double2 alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation A) {
        validateSYR2(Element.F64_2(mRS), Uplo, X, incX, Y, incY, A);
        BlasReference.syr2(Uplo, new double[] {alpha.x, alpha.y}, X, incX, Y, incY, A, true);
    }

    /** Packed Hermitian rank 2 update. */
    public void ZHPR2(int Uplo, Double2 alpha, Allocation X, int incX, Allocation Y, int incY,
            Allocation Ap) {
        int n = validateSPR2(Element.F64_2(mRS), Uplo, X, incX, Y, incY, Ap);
        BlasReference.spr2(Uplo, n, new double[] {alpha.x, alpha.y}, X, incX, Y, incY, Ap, true);
    }

    /** C := alpha*op(A)*op(B) + beta*C. */
    public void SGEMM(int TransA, int TransB, float alpha, Allocation A, Allocation B, float beta,
            Allocation C) {
        validateTranspose(TransA);
        validateTranspose(TransB);
        validateL3(Element.F32(mRS), TransA, TransB, 0, A, B, C);
        BlasReference.gemm(TransA, TransB, new double[] {alpha, 0}, A, B, new double[] {beta, 0}, C);
    }

    /** C := alpha*A*B + beta*C or alpha*B*A + beta*C, A symmetric. */
    public void SSYMM(int Side, int Uplo, float alpha, Allocation A, Allocation B, float beta,
            Allocation C) {
        validateSide(Side);
        validateUplo(Uplo);
        if (A.getType().getX() != Math.max(1, A.getType().getY())) {
            throw new RSRuntimeException("Matrix A is not symmetric");
        }
        validateL3(Element.F32(mRS), 0, 0, Side, A, B, C);
        BlasReference.symm(Side, Uplo, new double[] {alpha, 0}, A, B, new double[] {beta, 0}, C, false);
    }

    /** C := alpha*A*A**T + beta*C or alpha*A**T*A + beta*C, Uplo triangle only. */
    public void SSYRK(int Uplo, int Trans, float alpha, Allocation A, float beta, Allocation C) {
        validateTranspose(Trans);
        validateUplo(Uplo);
        validateL3(Element.F32(mRS), Trans, 0, 0, A, null, C);
        BlasReference.syrk(Uplo, Trans, new double[] {alpha, 0}, A, new double[] {beta, 0}, C, false);
    }

    /** Symmetric rank 2k update, Uplo triangle only. */
    public void SSYR2K(int Uplo, int Trans, float alpha, Allocation A, Allocation B, float beta,
            Allocation C) {
        validateUplo(Uplo);
        validateSYR2K(Element.F32(mRS), Trans, A, B, C);
        BlasReference.syr2k(Uplo, Trans, new double[] {alpha, 0}, A, B, new double[] {beta, 0}, C, false);
    }

    /** B := alpha*op(A)*B or alpha*B*op(A), A triangular. */
    public void STRMM(int Side, int Uplo, int TransA, int Diag, float alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRMM(Element.F32(mRS), Side, TransA, A, B);
        BlasReference.trmm(Side, Uplo, TransA, Diag, new double[] {alpha, 0}, A, B);
    }

    /** Solves op(A)*X = alpha*B or X*op(A) = alpha*B, leaving X in B. */
    public void STRSM(int Side, int Uplo, int TransA, int Diag, float alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRSM(Element.F32(mRS), Side, TransA, A, B);
        BlasReference.trsm(Side, Uplo, TransA, Diag, new double[] {alpha, 0}, A, B);
    }

    /** C := alpha*op(A)*op(B) + beta*C. */
    public void DGEMM(int TransA, int TransB, double alpha, Allocation A, Allocation B, double beta,
            Allocation C) {
        validateTranspose(TransA);
        validateTranspose(TransB);
        validateL3(Element.F64(mRS), TransA, TransB, 0, A, B, C);
        BlasReference.gemm(TransA, TransB, new double[] {alpha, 0}, A, B, new double[] {beta, 0}, C);
    }

    /** C := alpha*A*B + beta*C or alpha*B*A + beta*C, A symmetric. */
    public void DSYMM(int Side, int Uplo, double alpha, Allocation A, Allocation B, double beta,
            Allocation C) {
        validateSide(Side);
        validateUplo(Uplo);
        if (A.getType().getX() != Math.max(1, A.getType().getY())) {
            throw new RSRuntimeException("Matrix A is not symmetric");
        }
        validateL3(Element.F64(mRS), 0, 0, Side, A, B, C);
        BlasReference.symm(Side, Uplo, new double[] {alpha, 0}, A, B, new double[] {beta, 0}, C, false);
    }

    /** C := alpha*A*A**T + beta*C or alpha*A**T*A + beta*C, Uplo triangle only. */
    public void DSYRK(int Uplo, int Trans, double alpha, Allocation A, double beta, Allocation C) {
        validateTranspose(Trans);
        validateUplo(Uplo);
        validateL3(Element.F64(mRS), Trans, 0, 0, A, null, C);
        BlasReference.syrk(Uplo, Trans, new double[] {alpha, 0}, A, new double[] {beta, 0}, C, false);
    }

    /** Symmetric rank 2k update, Uplo triangle only. */
    public void DSYR2K(int Uplo, int Trans, double alpha, Allocation A, Allocation B, double beta,
            Allocation C) {
        validateUplo(Uplo);
        validateSYR2K(Element.F64(mRS), Trans, A, B, C);
        BlasReference.syr2k(Uplo, Trans, new double[] {alpha, 0}, A, B, new double[] {beta, 0}, C, false);
    }

    /** B := alpha*op(A)*B or alpha*B*op(A), A triangular. */
    public void DTRMM(int Side, int Uplo, int TransA, int Diag, double alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRMM(Element.F64(mRS), Side, TransA, A, B);
        BlasReference.trmm(Side, Uplo, TransA, Diag, new double[] {alpha, 0}, A, B);
    }

    /** Solves op(A)*X = alpha*B or X*op(A) = alpha*B, leaving X in B. */
    public void DTRSM(int Side, int Uplo, int TransA, int Diag, double alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRSM(Element.F64(mRS), Side, TransA, A, B);
        BlasReference.trsm(Side, Uplo, TransA, Diag, new double[] {alpha, 0}, A, B);
    }

    /** C := alpha*op(A)*op(B) + beta*C. */
    public void CGEMM(int TransA, int TransB, Float2 alpha, Allocation A, Allocation B, Float2 beta,
            Allocation C) {
        validateTranspose(TransA);
        validateTranspose(TransB);
        validateL3(Element.F32_2(mRS), TransA, TransB, 0, A, B, C);
        BlasReference.gemm(TransA, TransB, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C);
    }

    /** C := alpha*A*B + beta*C or alpha*B*A + beta*C, A symmetric. */
    public void CSYMM(int Side, int Uplo, Float2 alpha, Allocation A, Allocation B, Float2 beta,
            Allocation C) {
        validateSide(Side);
        validateUplo(Uplo);
        if (A.getType().getX() != Math.max(1, A.getType().getY())) {
            throw new RSRuntimeException("Matrix A is not symmetric");
        }
        validateL3(Element.F32_2(mRS), 0, 0, Side, A, B, C);
        BlasReference.symm(Side, Uplo, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C, false);
    }

    /** C := alpha*A*A**T + beta*C or alpha*A**T*A + beta*C, Uplo triangle only. */
    public void CSYRK(int Uplo, int Trans, Float2 alpha, Allocation A, Float2 beta, Allocation C) {
        validateTranspose(Trans);
        validateUplo(Uplo);
        validateL3(Element.F32_2(mRS), Trans, 0, 0, A, null, C);
        BlasReference.syrk(Uplo, Trans, new double[] {alpha.x, alpha.y}, A, new double[] {beta.x, beta.y}, C, false);
    }

    /** Symmetric rank 2k update, Uplo triangle only. */
    public void CSYR2K(int Uplo, int Trans, Float2 alpha, Allocation A, Allocation B, Float2 beta,
            Allocation C) {
        validateUplo(Uplo);
        validateSYR2K(Element.F32_2(mRS), Trans, A, B, C);
        BlasReference.syr2k(Uplo, Trans, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C, false);
    }

    /** B := alpha*op(A)*B or alpha*B*op(A), A triangular. */
    public void CTRMM(int Side, int Uplo, int TransA, int Diag, Float2 alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRMM(Element.F32_2(mRS), Side, TransA, A, B);
        BlasReference.trmm(Side, Uplo, TransA, Diag, new double[] {alpha.x, alpha.y}, A, B);
    }

    /** Solves op(A)*X = alpha*B or X*op(A) = alpha*B, leaving X in B. */
    public void CTRSM(int Side, int Uplo, int TransA, int Diag, Float2 alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRSM(Element.F32_2(mRS), Side, TransA, A, B);
        BlasReference.trsm(Side, Uplo, TransA, Diag, new double[] {alpha.x, alpha.y}, A, B);
    }

    /** C := alpha*op(A)*op(B) + beta*C. */
    public void ZGEMM(int TransA, int TransB, Double2 alpha, Allocation A, Allocation B, Double2 beta,
            Allocation C) {
        validateTranspose(TransA);
        validateTranspose(TransB);
        validateL3(Element.F64_2(mRS), TransA, TransB, 0, A, B, C);
        BlasReference.gemm(TransA, TransB, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C);
    }

    /** C := alpha*A*B + beta*C or alpha*B*A + beta*C, A symmetric. */
    public void ZSYMM(int Side, int Uplo, Double2 alpha, Allocation A, Allocation B, Double2 beta,
            Allocation C) {
        validateSide(Side);
        validateUplo(Uplo);
        if (A.getType().getX() != Math.max(1, A.getType().getY())) {
            throw new RSRuntimeException("Matrix A is not symmetric");
        }
        validateL3(Element.F64_2(mRS), 0, 0, Side, A, B, C);
        BlasReference.symm(Side, Uplo, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C, false);
    }

    /** C := alpha*A*A**T + beta*C or alpha*A**T*A + beta*C, Uplo triangle only. */
    public void ZSYRK(int Uplo, int Trans, Double2 alpha, Allocation A, Double2 beta, Allocation C) {
        validateTranspose(Trans);
        validateUplo(Uplo);
        validateL3(Element.F64_2(mRS), Trans, 0, 0, A, null, C);
        BlasReference.syrk(Uplo, Trans, new double[] {alpha.x, alpha.y}, A, new double[] {beta.x, beta.y}, C, false);
    }

    /** Symmetric rank 2k update, Uplo triangle only. */
    public void ZSYR2K(int Uplo, int Trans, Double2 alpha, Allocation A, Allocation B, Double2 beta,
            Allocation C) {
        validateUplo(Uplo);
        validateSYR2K(Element.F64_2(mRS), Trans, A, B, C);
        BlasReference.syr2k(Uplo, Trans, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C, false);
    }

    /** B := alpha*op(A)*B or alpha*B*op(A), A triangular. */
    public void ZTRMM(int Side, int Uplo, int TransA, int Diag, Double2 alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRMM(Element.F64_2(mRS), Side, TransA, A, B);
        BlasReference.trmm(Side, Uplo, TransA, Diag, new double[] {alpha.x, alpha.y}, A, B);
    }

    /** Solves op(A)*X = alpha*B or X*op(A) = alpha*B, leaving X in B. */
    public void ZTRSM(int Side, int Uplo, int TransA, int Diag, Double2 alpha, Allocation A,
            Allocation B) {
        validateUplo(Uplo);
        validateDiag(Diag);
        validateTRSM(Element.F64_2(mRS), Side, TransA, A, B);
        BlasReference.trsm(Side, Uplo, TransA, Diag, new double[] {alpha.x, alpha.y}, A, B);
    }

    /** C := alpha*A*B + beta*C or alpha*B*A + beta*C, A Hermitian. */
    public void CHEMM(int Side, int Uplo, Float2 alpha, Allocation A, Allocation B, Float2 beta,
            Allocation C) {
        validateUplo(Uplo);
        validateHEMM(Element.F32_2(mRS), Side, A, B, C);
        BlasReference.symm(Side, Uplo, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C, true);
    }

    /** C := alpha*A*A**H + beta*C, Uplo triangle only. alpha and beta are REAL. */
    public void CHERK(int Uplo, int Trans, float alpha, Allocation A, float beta, Allocation C) {
        validateUplo(Uplo);
        validateHERK(Element.F32_2(mRS), Trans, A, C);
        BlasReference.syrk(Uplo, Trans, new double[] {alpha, 0}, A,
                new double[] {beta, 0}, C, true);
    }

    /** Hermitian rank 2k update, Uplo triangle only. beta is REAL. */
    public void CHER2K(int Uplo, int Trans, Float2 alpha, Allocation A, Allocation B, float beta,
            Allocation C) {
        validateUplo(Uplo);
        validateHER2K(Element.F32_2(mRS), Trans, A, B, C);
        BlasReference.syr2k(Uplo, Trans, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta, 0}, C, true);
    }

    /** C := alpha*A*B + beta*C or alpha*B*A + beta*C, A Hermitian. */
    public void ZHEMM(int Side, int Uplo, Double2 alpha, Allocation A, Allocation B, Double2 beta,
            Allocation C) {
        validateUplo(Uplo);
        validateHEMM(Element.F64_2(mRS), Side, A, B, C);
        BlasReference.symm(Side, Uplo, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta.x, beta.y}, C, true);
    }

    /** C := alpha*A*A**H + beta*C, Uplo triangle only. alpha and beta are REAL. */
    public void ZHERK(int Uplo, int Trans, double alpha, Allocation A, double beta, Allocation C) {
        validateUplo(Uplo);
        validateHERK(Element.F64_2(mRS), Trans, A, C);
        BlasReference.syrk(Uplo, Trans, new double[] {alpha, 0}, A,
                new double[] {beta, 0}, C, true);
    }

    /** Hermitian rank 2k update, Uplo triangle only. beta is REAL. */
    public void ZHER2K(int Uplo, int Trans, Double2 alpha, Allocation A, Allocation B, double beta,
            Allocation C) {
        validateUplo(Uplo);
        validateHER2K(Element.F64_2(mRS), Trans, A, B, C);
        BlasReference.syr2k(Uplo, Trans, new double[] {alpha.x, alpha.y}, A, B, new double[] {beta, 0}, C, true);
    }

    static void validateGERU(Element e, Allocation x, int incX, Allocation y, int incY,
                             Allocation a) {
        if (!a.getType().getElement().isCompatible(e)
                || !x.getType().getElement().isCompatible(e)
                || !y.getType().getElement().isCompatible(e)) {
            throw new RSRuntimeException("Called BLAS with wrong Element type");
        }
        if (x.getType().getY() > 1 || y.getType().getY() > 1) {
            throw new RSRuntimeException("BLAS vectors must have Y dimension of 0 or 1");
        }
        int m = dimY(a);
        int n = a.getType().getX();
        if (incX <= 0 || incY <= 0) {
            throw new RSRuntimeException("Vector increments must be greater than 0");
        }
        if (x.getType().getX() != 1 + (m - 1) * incX) {
            throw new RSRuntimeException("Incorrect vector dimensions for GERU");
        }
        if (y.getType().getX() != 1 + (n - 1) * incY) {
            throw new RSRuntimeException("Incorrect vector dimensions for GERU");
        }
    }

    /**
     * 8-bit quantised GEMM for neural networks: C = A * transpose(B).
     *
     * Each matrix carries a zero-point offset that is subtracted before the
     * multiply, and the accumulator is rescaled through c_offset and c_mult in
     * 1.10.21 fixed point before being gated to 0..255. The 10 integer bits of
     * headroom are deliberate: a small overflow saturates instead of wrapping.
     *
     * @param a_offset 0..255, subtracted from every A entry
     * @param b_offset 0..255, subtracted from every B entry
     * @param c_offset added to the accumulator before the multiply
     * @param c_mult   the fixed-point output scale
     */
    public void BNNM(Allocation A, int a_offset, Allocation B, int b_offset, Allocation C,
                     int c_offset, int c_mult) {
        validateL3(Element.U8(mRS), NO_TRANSPOSE, TRANSPOSE, 0, A, B, C);
        if (a_offset < 0 || a_offset > 255) {
            throw new RSRuntimeException("Invalid a_offset passed to BNNM");
        }
        if (b_offset < 0 || b_offset > 255) {
            throw new RSRuntimeException("Invalid b_offset passed to BNNM");
        }
        BlasReference.bnnm(A, a_offset, B, b_offset, C, c_offset, c_mult);
    }
}
