package android.graphics;

import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * AOSP-faithful android.graphics.Mesh (API 34). Holds a custom vertex mesh +
 * uniforms for Canvas.drawMesh. We have no programmable Skia pipeline on AWT,
 * so this is a faithful data holder: the buffers/uniforms round-trip and are
 * retained, but Canvas.drawMesh does not rasterize them. Mirrors
 * frameworks/base/graphics/java/android/graphics/Mesh.java.
 */
public class Mesh {
    public static final int TRIANGLES      = 0;
    public static final int TRIANGLE_STRIP = 1;

    private final MeshSpecification mMeshSpec;
    private final int mMode;
    private final Buffer mVertexBuffer;
    private final int mVertexCount;
    private final ShortBuffer mIndexBuffer;
    private final RectF mBounds;
    private final Map<String, Object> mUniforms = new HashMap<>();

    public Mesh(MeshSpecification meshSpec, int mode, Buffer vertexBuffer,
            int vertexCount, RectF bounds) {
        this(meshSpec, mode, vertexBuffer, vertexCount, null, bounds);
    }

    public Mesh(MeshSpecification meshSpec, int mode, Buffer vertexBuffer,
            int vertexCount, ShortBuffer indexBuffer, RectF bounds) {
        mMeshSpec = meshSpec;
        mMode = mode;
        mVertexBuffer = vertexBuffer;
        mVertexCount = vertexCount;
        mIndexBuffer = indexBuffer;
        mBounds = bounds;
    }

    public void setColorUniform(String uniformName, int color) {
        mUniforms.put(uniformName, color);
    }
    public void setColorUniform(String uniformName, long color) {
        mUniforms.put(uniformName, color);
    }
    public void setColorUniform(String uniformName, Color color) {
        mUniforms.put(uniformName, color);
    }

    public void setFloatUniform(String uniformName, float value) {
        mUniforms.put(uniformName, new float[] { value });
    }
    public void setFloatUniform(String uniformName, float v1, float v2) {
        mUniforms.put(uniformName, new float[] { v1, v2 });
    }
    public void setFloatUniform(String uniformName, float v1, float v2, float v3) {
        mUniforms.put(uniformName, new float[] { v1, v2, v3 });
    }
    public void setFloatUniform(String uniformName, float v1, float v2, float v3, float v4) {
        mUniforms.put(uniformName, new float[] { v1, v2, v3, v4 });
    }
    public void setFloatUniform(String uniformName, float[] values) {
        mUniforms.put(uniformName, values);
    }

    public void setIntUniform(String uniformName, int value) {
        mUniforms.put(uniformName, new int[] { value });
    }
    public void setIntUniform(String uniformName, int v1, int v2) {
        mUniforms.put(uniformName, new int[] { v1, v2 });
    }
    public void setIntUniform(String uniformName, int v1, int v2, int v3) {
        mUniforms.put(uniformName, new int[] { v1, v2, v3 });
    }
    public void setIntUniform(String uniformName, int v1, int v2, int v3, int v4) {
        mUniforms.put(uniformName, new int[] { v1, v2, v3, v4 });
    }
    public void setIntUniform(String uniformName, int[] values) {
        mUniforms.put(uniformName, values);
    }
}
