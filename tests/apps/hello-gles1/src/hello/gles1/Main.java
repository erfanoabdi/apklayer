package hello.gles1;

import android.app.Activity;
import android.opengl.GLES10;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL ES 1.x (GLSurfaceView's default context): a red clear and a green fixed-function triangle from a client vertex array under
 *  glOrthof, read back with glReadPixels; the log line names GL_VERSION and GL_RENDERER (the host driver's ES 1, or the translator's). */
public class Main extends Activity implements GLSurfaceView.Renderer {
    private int frame;
    private int width, height;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        GLSurfaceView view = new GLSurfaceView(this);
        view.setRenderer(this);
        setContentView(view);
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {}

    @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
        width = w;
        height = h;
        gl.glViewport(0, 0, w, h);
    }

    private static int pixel(int x, int y) {
        ByteBuffer b = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        GLES10.glReadPixels(x, y, 1, 1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, b);
        return (b.get(0) & 255) << 24 | (b.get(1) & 255) << 16 | (b.get(2) & 255) << 8 | (b.get(3) & 255);
    }

    @Override public void onDrawFrame(GL10 gl) {
        gl.glClearColor(1, 0, 0, 1);
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
        FloatBuffer tri = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
        tri.put(new float[] {-1, -1, 1, -1, 0, 1}).position(0);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrthof(-1, 1, -1, 1, -1, 1);
        gl.glColor4f(0, 1, 0, 1);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, tri);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 3);
        if (++frame == 3) {  // the triangle's centre is green, the top-left corner (outside it) red
            int[] size = new int[2];
            GLES10.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, size, 0);
            System.out.println("gles1: " + gl.glGetString(GL10.GL_VERSION) + " | " + gl.glGetString(GL10.GL_RENDERER) + " | maxtex " + size[0]
                + " | center " + Integer.toHexString(pixel(width / 2, height / 4)) + " corner " + Integer.toHexString(pixel(2, height - 3))
                + " err " + gl.glGetError());
        }
    }
}
