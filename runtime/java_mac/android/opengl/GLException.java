// android.opengl.GLException -- the unchecked exception GLSurfaceView and the
// GLES helper classes throw when a GL call reports an error.
//
// A REAL implementation: it is a plain RuntimeException carrying a GL error
// code, with no device dependency at all. Apps catch it by type, so the type
// identity and the RuntimeException supertype are the whole contract.
//
// Mirrors frameworks/base/opengl/java/android/opengl/GLException.java
// (android11-release). Two details copied deliberately rather than
// "improved":
//   * the one-arg constructor builds its message via GLU.gluErrorString and
//     falls back to "Unknown error 0x<hex>" -- apps grep that text.
//   * getError() is PACKAGE-PRIVATE on AOSP, so it is package-private here.
//     It is absent from android.jar's stubs, which means no app can reference
//     it and widening it to public would add surface AOSP does not have.
package android.opengl;

@SuppressWarnings("serial")
public class GLException extends RuntimeException {
    public GLException(final int error) {
        super(getErrorString(error));
        mError = error;
    }

    public GLException(final int error, final String string) {
        super(string);
        mError = error;
    }

    private static String getErrorString(int error) {
        String errorString = GLU.gluErrorString(error);
        if (errorString == null) {
            errorString = "Unknown error 0x" + Integer.toHexString(error);
        }
        return errorString;
    }

    int getError() {
        return mError;
    }

    private final int mError;
}
