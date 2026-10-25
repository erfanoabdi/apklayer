// android.opengl.Visibility -- CPU-side visibility helpers for triangle meshes:
// frustum-test an indexed mesh, cull bounding spheres against an MVP frustum,
// and compute a bounding sphere (API 1).
//
// AOSP declares all three `native`; the bodies are ported here line for line:
//   * frameworks/base/core/jni/android/opengl/util.cpp (android14-release):
//     util_visibilityTest / visibilityTest, util_frustumCullSpheres /
//     computeFrustum / sphereHitsFrustum, util_computeBoundingSphere, and the
//     FloatArrayHelper/IntArrayHelper argument checks with their exact
//     IllegalArgumentException messages ("array == null", "offset < 0",
//     "length - offset < n", ...).
//   * frameworks/base/core/jni/android/opengl/poly_clip.cpp + poly.h:
//     poly_clip_to_frustum / poly_clip_to_halfspace (POLY_NMAX = 10).
// Pure Java for the same reason as ETC1: it is arithmetic with no platform
// dependency, so there is nothing for macOS to route to. The descriptors are
// the SDK's, so app call sites link unchanged.
//
// Two honest limits of a port, both about inputs a device mishandles too:
//  * where the C code reads past an array it merely bounds-checked by OFFSET
//    (positions has minSize 0; `maxIndex * 3 > positionsLength` lets the last
//    vertex run 1..3 floats off the end), C reads whatever memory follows and
//    Java throws ArrayIndexOutOfBoundsException. No well-formed call reaches it.
//  * the device's clang may fuse a*b+c into one fmadd; Java rounds each float
//    op. Results can differ in the last ulp, which can only flip a verdict for
//    geometry lying exactly on a clip plane.
// Census + method: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.opengl;

/**
 * A collection of utility methods for computing the visibility of triangle
 * meshes.
 */
public class Visibility {

    public Visibility() {
    }

    // poly.h
    private static final int POLY_NMAX = 10;
    private static final int POLY_CLIP_OUT = 0;
    private static final int POLY_CLIP_PARTIAL = 1;
    private static final int POLY_CLIP_IN = 2;

    /**
     * Test whether a given triangle mesh is visible on the screen. The mesh
     * is specified as an indexed triangle list.
     *
     * @return 2 if all of the mesh is visible, 1 if some part of the mesh is
     *         visible, 0 if no part is visible (and -1 when an index points past
     *         the positions, as util.cpp returns).
     * @throws IllegalArgumentException if ws is null, wsOffset < 0,
     *         positions is null, positionsOffset < 0, indices is null,
     *         indicesOffset < 0, indicesOffset > indices.length - indexCount
     */
    public static int visibilityTest(float[] ws, int wsOffset,
            float[] positions, int positionsOffset, char[] indices,
            int indicesOffset, int indexCount) {
        checkArray(ws, ws == null ? 0 : ws.length, wsOffset, 16);
        int positionsLength = checkArray(positions, positions == null ? 0 : positions.length,
                positionsOffset, 0);
        int indicesLength = checkArray(indices, indices == null ? 0 : indices.length,
                indicesOffset, 0);
        if (indicesLength < indexCount) {
            throw new IllegalArgumentException("length < offset + indexCount");
        }
        return visibilityTestImpl(ws, wsOffset, positions, positionsOffset, positionsLength,
                indices, indicesOffset, indexCount);
    }

    /**
     * Given an OpenGL ES ModelView-Projection matrix (which implicitly
     * describes a frustum) and a list of spheres, determine which spheres
     * intersect the frustum.
     *
     * @return the number of spheres that intersected the frustum. Can be
     *         larger than resultsCapacity, in which case only the first
     *         resultsCapacity results are written into the results array.
     */
    public static int frustumCullSpheres(float[] mvp, int mvpOffset,
            float[] spheres, int spheresOffset, int spheresCount,
            int[] results, int resultsOffset, int resultsCapacity) {
        checkArray(mvp, mvp == null ? 0 : mvp.length, mvpOffset, 16);
        checkArray(spheres, spheres == null ? 0 : spheres.length, spheresOffset,
                spheresCount * 4);
        checkArray(results, results == null ? 0 : results.length, resultsOffset,
                resultsCapacity);

        float[] frustum = new float[6 * 4];
        computeFrustum(mvp, mvpOffset, frustum);

        int sphere = spheresOffset;
        int outputCount = 0;
        int out = resultsOffset;
        for (int i = 0; i < spheresCount; i++, sphere += 4) {
            if (sphereHitsFrustum(frustum, spheres, sphere)) {
                if (outputCount < resultsCapacity) {
                    results[out++] = i;
                }
                outputCount++;
            }
        }
        return outputCount;
    }

    /**
     * Compute a bounding sphere for a set of points. It is approximately the
     * minimal bounding sphere of an axis-aligned box that bounds the points.
     *
     * @param sphere receives x, y, z, radius at sphereOffset.
     */
    public static void computeBoundingSphere(float[] positions, int positionsOffset,
            int positionsCount, float[] sphere, int sphereOffset) {
        checkArray(positions, positions == null ? 0 : positions.length, positionsOffset, 0);
        checkArray(sphere, sphere == null ? 0 : sphere.length, sphereOffset, 4);

        if (positionsCount < 1) {
            throw new IllegalArgumentException("positionsCount < 1");
        }

        int src = positionsOffset;

        // find bounding box
        float x0 = positions[src++];
        float x1 = x0;
        float y0 = positions[src++];
        float y1 = y0;
        float z0 = positions[src++];
        float z1 = z0;

        for (int i = 1; i < positionsCount; i++) {
            float x = positions[src++];
            if (x < x0) {
                x0 = x;
            } else if (x > x1) {
                x1 = x;
            }
            float y = positions[src++];
            if (y < y0) {
                y0 = y;
            } else if (y > y1) {
                y1 = y;
            }
            float z = positions[src++];
            if (z < z0) {
                z0 = z;
            } else if (z > z1) {
                z1 = z;
            }
        }

        // Because we know our input meshes fit pretty well into bounding boxes,
        // just take the diagonal of the box as defining our sphere. (AOSP)
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        sphere[sphereOffset] = x0 + dx * 0.5f;
        sphere[sphereOffset + 1] = y0 + dy * 0.5f;
        sphere[sphereOffset + 2] = z0 + dz * 0.5f;
        sphere[sphereOffset + 3] = distance(dx, dy, dz) * 0.5f;
    }

    // ------------------------------------------------------------------
    // util.cpp ArrayHelper::check(): null, negative offset, then length -
    // offset against the minimum. Returns mLength (= length - offset).
    // ------------------------------------------------------------------
    private static int checkArray(Object ref, int arrayLength, int offset, int minSize) {
        if (ref == null) {
            throw new IllegalArgumentException("array == null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset < 0");
        }
        int length = arrayLength - offset;
        if (length < minSize) {
            throw new IllegalArgumentException("length - offset < n");
        }
        return length;
    }

    private static float distance2(float x, float y, float z) {
        return x * x + y * y + z * z;
    }

    private static float distance(float x, float y, float z) {
        return (float) Math.sqrt(distance2(x, y, z));
    }

    private static void normalizePlane(float[] p, int off) {
        float rdist = 1.0f / distance(p[off], p[off + 1], p[off + 2]);
        for (int i = 0; i < 4; i++) {
            p[off + i] *= rdist;
        }
    }

    private static float signedDistance(float[] plane, int off, float x, float y, float z) {
        return plane[off] * x + plane[off + 1] * y + plane[off + 2] * z + plane[off + 3];
    }

    /** Return true if the sphere intersects or is inside the frustum. */
    private static boolean sphereHitsFrustum(float[] frustum, float[] s, int so) {
        float x = s[so];
        float y = s[so + 1];
        float z = s[so + 2];
        float negRadius = -s[so + 3];
        for (int i = 0; i < 6; i++) {
            if (signedDistance(frustum, i * 4, x, y, z) <= negRadius) {
                return false;
            }
        }
        return true;
    }

    private static void computeFrustum(float[] m, int mo, float[] f) {
        float m3 = m[mo + 3];
        float m7 = m[mo + 7];
        float m11 = m[mo + 11];
        float m15 = m[mo + 15];
        // right
        f[0] = m3 - m[mo];
        f[1] = m7 - m[mo + 4];
        f[2] = m11 - m[mo + 8];
        f[3] = m15 - m[mo + 12];
        normalizePlane(f, 0);
        // left
        f[4] = m3 + m[mo];
        f[5] = m7 + m[mo + 4];
        f[6] = m11 + m[mo + 8];
        f[7] = m15 + m[mo + 12];
        normalizePlane(f, 4);
        // top
        f[8] = m3 - m[mo + 1];
        f[9] = m7 - m[mo + 5];
        f[10] = m11 - m[mo + 9];
        f[11] = m15 - m[mo + 13];
        normalizePlane(f, 8);
        // bottom
        f[12] = m3 + m[mo + 1];
        f[13] = m7 + m[mo + 5];
        f[14] = m11 + m[mo + 9];
        f[15] = m15 + m[mo + 13];
        normalizePlane(f, 12);
        // far
        f[16] = m3 - m[mo + 2];
        f[17] = m7 - m[mo + 6];
        f[18] = m11 - m[mo + 10];
        f[19] = m15 - m[mo + 14];
        normalizePlane(f, 16);
        // near
        f[20] = m3 + m[mo + 2];
        f[21] = m7 + m[mo + 6];
        f[22] = m11 + m[mo + 10];
        f[23] = m15 + m[mo + 14];
        normalizePlane(f, 20);
    }

    /** mx4transform: column-major 4x4 times (x, y, z, w). */
    private static void mx4transform(float x, float y, float z, float w,
            float[] m, int mo, float[] dest, int d) {
        dest[d] = m[mo + 0] * x + m[mo + 4] * y + m[mo + 8] * z + m[mo + 12] * w;
        dest[d + 1] = m[mo + 1] * x + m[mo + 5] * y + m[mo + 9] * z + m[mo + 13] * w;
        dest[d + 2] = m[mo + 2] * x + m[mo + 6] * y + m[mo + 10] * z + m[mo + 14] * w;
        dest[d + 3] = m[mo + 3] * x + m[mo + 7] * y + m[mo + 11] * z + m[mo + 15] * w;
    }

    private static int visibilityTestImpl(float[] ws, int wo, float[] positions, int po,
            int positionsLength, char[] indices, int io, int indexCount) {
        int result = POLY_CLIP_OUT;

        if (indexCount < 3) {
            return POLY_CLIP_OUT;
        }

        // We transform every vertex between the min and max indices,
        // inclusive. (AOSP)
        int minIndex = 65536;
        int maxIndex = -1;
        for (int i = 0; i < indexCount; i++) {
            int index = indices[io + i];
            if (index < minIndex) {
                minIndex = index;
            }
            if (index > maxIndex) {
                maxIndex = index;
            }
        }

        if (maxIndex * 3 > positionsLength) {
            return -1;
        }

        int transformedIndexCount = maxIndex - minIndex + 1;
        float[] transformed = new float[transformedIndexCount * 4];

        // Transform the vertices
        int src = po + 3 * minIndex;
        int dst = 0;
        for (int i = 0; i < transformedIndexCount; i++, src += 3, dst += 4) {
            mx4transform(positions[src], positions[src + 1], positions[src + 2], 1.0f,
                    ws, wo, transformed, dst);
        }

        // Clip the triangles
        float[] poly = new float[POLY_NMAX * 4];
        for (int i = 0; i < indexCount; i += 3) {
            // AOSP reads indices[i + 1] / [i + 2] even when indexCount is not a
            // multiple of three (its "ignored" remainder is still read);
            // `i < indexCount` is the same loop bound.
            System.arraycopy(transformed, 4 * (indices[io + i] - minIndex), poly, 0, 4);
            System.arraycopy(transformed, 4 * (indices[io + i + 1] - minIndex), poly, 4, 4);
            System.arraycopy(transformed, 4 * (indices[io + i + 2] - minIndex), poly, 8, 4);
            result = polyClipToFrustum(poly, 3);
            if (result != POLY_CLIP_OUT) {
                return result;
            }
        }

        return result;
    }

    // ------------------------------------------------------------------
    // poly_clip.cpp. A Poly is float[POLY_NMAX * 4] (sx, sy, sz, sw per
    // vertex) plus its vertex count, which the C struct carries as `n`.
    // ------------------------------------------------------------------

    private static final int SX = 0;
    private static final int SY = 1;
    private static final int SZ = 2;
    private static final int SW = 3;

    /**
     * Clip convex polygon p (np vertices) against a plane, copying the portion
     * satisfying sign*s[index] < k*sw into q. Returns q's vertex count.
     */
    private static int polyClipToHalfspace(float[] p, int np, float[] q, int index,
            float sign, float k) {
        int nq = 0;

        // start with u=vert[n-1], v=vert[0]
        int u = (np - 1) * 4;
        float tu = sign * p[u + index] - p[u + SW] * k;
        for (int v = 0, i = np; i > 0; i--, u = v, v += 4) {
            // on old polygon (p), u is previous vertex, v is current vertex
            // tv is negative if vertex v is in
            float tv = sign * p[v + index] - p[v + SW] * k;
            if ((tu <= 0.0f) ^ (tv <= 0.0f)) {
                // edge crosses plane; add intersection point to q
                float t = tu / (tu - tv);
                int w = nq * 4;
                for (int c = 0; c < 4; c++) {
                    q[w + c] = p[u + c] + t * (p[v + c] - p[u + c]);
                }
                nq++;
            }
            if (tv <= 0.0f) {
                // vertex v is in, copy it to q
                System.arraycopy(p, v, q, nq * 4, 4);
                nq++;
            }
            tu = tv;
        }
        return nq;
    }

    /**
     * Clip the convex polygon p1 to the screen space frustum. Returns
     * POLY_CLIP_IN, POLY_CLIP_OUT or POLY_CLIP_PARTIAL (the clipped polygon
     * itself is not needed by the one caller, so it is not copied back).
     */
    private static int polyClipToFrustum(float[] p1, int n) {
        int x0out = 0, x1out = 0, y0out = 0, y1out = 0, z0out = 0, z1out = 0;

        // count vertices "outside" with respect to each of the six planes
        for (int v = 0, i = n; i > 0; i--, v += 4) {
            float sw = p1[v + SW];
            if (p1[v + SX] < -sw) x0out++;   // out on left
            if (p1[v + SX] > sw) x1out++;    // out on right
            if (p1[v + SY] < -sw) y0out++;   // out on top
            if (p1[v + SY] > sw) y1out++;    // out on bottom
            if (p1[v + SZ] < -sw) z0out++;   // out on near
            if (p1[v + SZ] > sw) z1out++;    // out on far
        }

        // check if all vertices inside
        if (x0out + x1out + y0out + y1out + z0out + z1out == 0) {
            return POLY_CLIP_IN;
        }

        // check if all vertices are "outside" any of the six planes
        if (x0out == n || x1out == n || y0out == n
                || y1out == n || z0out == n || z1out == n) {
            return POLY_CLIP_OUT;
        }

        // now clip against each of the planes that might cut the polygon,
        // at each step toggling between polygons p and q (CLIP_AND_SWAP passes
        // k as sign * k)
        float[] p = p1;
        float[] q = new float[POLY_NMAX * 4];
        int np = n;
        int[] outs = { x0out, x1out, y0out, y1out, z0out, z1out };
        int[] elem = { SX, SX, SY, SY, SZ, SZ };
        float[] signs = { -1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f };
        for (int plane = 0; plane < 6; plane++) {
            if (outs[plane] == 0) {
                continue;
            }
            float sign = signs[plane];
            int nq = polyClipToHalfspace(p, np, q, elem[plane], sign, sign * sign);
            if (nq == 0) {
                return POLY_CLIP_OUT;
            }
            float[] r = p;
            p = q;
            q = r;
            np = nq;
        }
        return POLY_CLIP_PARTIAL;
    }
}
