package android.graphics;

/**
 * AOSP-faithful android.graphics.MeshSpecification (API 34). Describes the
 * vertex attribute/varying layout and the AGSL vertex/fragment programs for a
 * custom Mesh. We have no programmable Skia pipeline on AWT, so this is a
 * faithful data holder: the layout is retained and returned but never executed.
 * Mirrors frameworks/base/graphics/java/android/graphics/MeshSpecification.java.
 */
public class MeshSpecification {
    public static final int TYPE_FLOAT  = 0;
    public static final int TYPE_FLOAT2 = 1;
    public static final int TYPE_FLOAT3 = 2;
    public static final int TYPE_FLOAT4 = 3;
    public static final int TYPE_UBYTE4 = 4;

    public static final int ALPHA_TYPE_UNKNOWN        = 0;
    public static final int ALPHA_TYPE_OPAQUE         = 1;
    public static final int ALPHA_TYPE_PREMULTIPLIED  = 2;
    public static final int ALPHA_TYPE_UNPREMULTIPLIED = 3;

    public static class Attribute {
        final int mType;
        final int mOffset;
        final String mName;
        public Attribute(int type, int offset, String name) {
            mType = type; mOffset = offset; mName = name;
        }
        /** AOSP MeshSpecification.Attribute.getType() -- one of the TYPE_* above. */
        public int getType() { return mType; }
        /** AOSP MeshSpecification.Attribute.getOffset() -- byte offset in the vertex. */
        public int getOffset() { return mOffset; }
        /** AOSP MeshSpecification.Attribute.getName() -- the AGSL attribute name. */
        public String getName() { return mName; }
    }

    public static class Varying {
        final int mType;
        final String mName;
        public Varying(int type, String name) {
            mType = type; mName = name;
        }
        /** AOSP MeshSpecification.Varying.getType(). */
        public int getType() { return mType; }
        /** AOSP MeshSpecification.Varying.getName(). */
        public String getName() { return mName; }
    }

    private final Attribute[] mAttributes;
    private final int mVertexStride;
    private final Varying[] mVaryings;

    private MeshSpecification(Attribute[] attributes, int vertexStride, Varying[] varyings) {
        mAttributes = attributes;
        mVertexStride = vertexStride;
        mVaryings = varyings;
    }

    public static MeshSpecification make(Attribute[] attributes, int vertexStride,
            Varying[] varyings, String vertexShader, String fragmentShader) {
        return new MeshSpecification(attributes, vertexStride, varyings);
    }

    public static MeshSpecification make(Attribute[] attributes, int vertexStride,
            Varying[] varyings, String vertexShader, String fragmentShader,
            ColorSpace colorSpace) {
        return new MeshSpecification(attributes, vertexStride, varyings);
    }

    public static MeshSpecification make(Attribute[] attributes, int vertexStride,
            Varying[] varyings, String vertexShader, String fragmentShader,
            ColorSpace colorSpace, int alphaType) {
        return new MeshSpecification(attributes, vertexStride, varyings);
    }
}
