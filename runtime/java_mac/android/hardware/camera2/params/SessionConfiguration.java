// android.hardware.camera2.params.SessionConfiguration (API 28; the
// output-only constructor and SESSION_SHARED are API 35, the colour-space
// members API 34).
//
// Everything needed to create -- or merely to DESCRIBE -- a capture session:
// session type, output configurations, the reprocessing input configuration,
// session-wide parameters, the state callback and its executor.
//
// This is a REAL implementation. AOSP's SessionConfiguration is a mutable
// value holder that the framework reads when the session is finally created;
// none of its own methods touch the camera, so the setters, getters and
// setInputConfiguration's high-speed rejection are reproduced exactly. An app
// that builds a SessionConfiguration and reads it back behaves identically here
// and on a real device -- what never happens is the session actually opening,
// and that is decided in CameraManager/CameraBackend, not here.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/params/SessionConfiguration.java
// (aosp-mirror/platform_frameworks_base, main): session-type constants L63-92,
// the 4-arg constructor L130, the outputs-only constructor L162-166,
// getSessionType L288, getOutputConfigurations L297, getStateCallback L306,
// getExecutor L315, setInputConfiguration L327-334, getInputConfiguration L341,
// setSessionParameters L363, getSessionParameters L373, setColorSpace L387,
// clearColorSpace L397, getColorSpace L410, setStateCallback L430.
package android.hardware.camera2.params;

import android.graphics.ColorSpace;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class SessionConfiguration implements android.os.Parcelable {

    /** AOSP aliases these to CameraDevice.SESSION_OPERATION_MODE_*; the
     *  literal values are 0/1/2 and are what an app compiles against. */
    public static final int SESSION_REGULAR = 0;
    public static final int SESSION_HIGH_SPEED = 1;
    /** API 35. */
    public static final int SESSION_SHARED = 2;

    /** ColorSpaceProfiles.UNSPECIFIED; see the same constant in
     *  OutputConfiguration for why it is inlined. */
    private static final int COLOR_SPACE_UNSPECIFIED = -1;

    private final int mSessionType;
    private final List<OutputConfiguration> mOutputConfigurations;
    private CameraCaptureSession.StateCallback mStateCallback;
    private Executor mExecutor;
    private InputConfiguration mInputConfig;
    private CaptureRequest mSessionParameters;
    private int mColorSpace = COLOR_SPACE_UNSPECIFIED;

    public SessionConfiguration(int sessionType, List<OutputConfiguration> outputs,
            Executor executor, CameraCaptureSession.StateCallback cb) {
        mSessionType = sessionType;
        mOutputConfigurations = Collections.unmodifiableList(new ArrayList<>(outputs));
        mStateCallback = cb;
        mExecutor = executor;
    }

    /** API 35 -- describes a session without committing to run it, so
     *  CameraDeviceSetup can be asked whether the combination is supported.
     *  setStateCallback must be called before it can create a real session. */
    public SessionConfiguration(int sessionType, List<OutputConfiguration> outputs) {
        mSessionType = sessionType;
        mOutputConfigurations = Collections.unmodifiableList(new ArrayList<>(outputs));
    }

    public int getSessionType() {
        return mSessionType;
    }

    public List<OutputConfiguration> getOutputConfigurations() {
        return mOutputConfigurations;
    }

    public CameraCaptureSession.StateCallback getStateCallback() {
        return mStateCallback;
    }

    public Executor getExecutor() {
        return mExecutor;
    }

    /** API 35. */
    public void setStateCallback(Executor executor, CameraCaptureSession.StateCallback cb) {
        mExecutor = executor;
        mStateCallback = cb;
    }

    /** AOSP rejects an input configuration on a high-speed session with
     *  UnsupportedOperationException rather than silently ignoring it. */
    public void setInputConfiguration(InputConfiguration input) {
        if (mSessionType != SESSION_HIGH_SPEED) {
            mInputConfig = input;
        } else {
            throw new UnsupportedOperationException("Method not supported for high speed session"
                    + " types");
        }
    }

    public InputConfiguration getInputConfiguration() {
        return mInputConfig;
    }

    public void setSessionParameters(CaptureRequest params) {
        mSessionParameters = params;
    }

    public CaptureRequest getSessionParameters() {
        return mSessionParameters;
    }

    /** API 34. */
    public void setColorSpace(ColorSpace.Named colorSpace) {
        if (colorSpace == null) {
            throw new NullPointerException("colorSpace must not be null");
        }
        mColorSpace = colorSpace.ordinal();
        for (OutputConfiguration outputConfiguration : mOutputConfigurations) {
            outputConfiguration.setColorSpace(colorSpace);
        }
    }

    /** API 34. */
    public void clearColorSpace() {
        mColorSpace = COLOR_SPACE_UNSPECIFIED;
        for (OutputConfiguration outputConfiguration : mOutputConfigurations) {
            outputConfiguration.clearColorSpace();
        }
    }

    /** API 34. Null when never set, matching AOSP. */
    public ColorSpace getColorSpace() {
        if (mColorSpace != COLOR_SPACE_UNSPECIFIED) {
            return ColorSpace.get(ColorSpace.Named.values()[mColorSpace]);
        }
        return null;
    }

    // ---- Parcelable -------------------------------------------------------
    //
    // Zero-byte writeToParcel by design, exactly as on OutputConfiguration and
    // CaptureRequest: android.os.Parcel's documented empty-writer fallback
    // (Parcel.writeParcelableBody) keeps the LIVE SessionConfiguration and warns
    // once on stderr.
    //
    // The case is even clearer here than for OutputConfiguration. A
    // SessionConfiguration carries a CameraCaptureSession.StateCallback and a
    // java.util.concurrent.Executor -- live objects with no byte form in any
    // implementation, AOSP's included (AOSP's own writeToParcel drops both, and
    // its javadoc says the parcelled copy is meant for the camera service, not
    // for the app to read back). Marshalling the rest and silently losing the
    // callback would hand back a configuration that can never report
    // onConfigured, which is the failure mode this project treats as worse than
    // an honest gap.
    //
    // REMOVAL CONDITION is automatic: the fallback is driven by a measurement
    // (did the body write anything), so a real marshaller here disables it with
    // no edit to Parcel.

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    public static final android.os.Parcelable.Creator<SessionConfiguration> CREATOR =
            new android.os.Parcelable.Creator<SessionConfiguration>() {
        /** Reads nothing, matching writeToParcel; Parcel then substitutes the
         *  live configuration it kept. */
        @Override public SessionConfiguration createFromParcel(android.os.Parcel in) {
            return new SessionConfiguration(SESSION_REGULAR,
                    new java.util.ArrayList<OutputConfiguration>());
        }
        @Override public SessionConfiguration[] newArray(int size) {
            return new SessionConfiguration[size];
        }
    };
}
