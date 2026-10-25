package android.opengl;
import java.io.Writer;
import javax.microedition.khronos.opengles.GL;
/** Wraps a GL pipe with error-checking / logging for debugging. Mac.apk routes
 *  GL through ANGLE, whose own validation covers this, so wrap() returns the
 *  pipe unchanged. API 4.
 *  Source: frameworks/base/opengl/java/android/opengl/GLDebugHelper.java */
// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class GLDebugHelper {
    public static final int CONFIG_CHECK_GL_ERROR = 1;
    public static final int CONFIG_CHECK_THREAD = 2;
    public static final int CONFIG_LOG_ARGUMENT_NAMES = 4;
    public static final int ERROR_WRONG_THREAD = 0x7000;
    /** AOSP GLDebugHelper declares NO constructor, so the synthesised no-arg
     *  one is PUBLIC and part of the API surface. */
    public GLDebugHelper() { }
    public static GL wrap(GL gl, int configFlags, Writer log) { return gl; }
}
