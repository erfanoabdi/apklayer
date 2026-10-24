// android.hardware.camera2.params.ExtensionSessionConfiguration (API 31; the
// postview output is API 34 and the colour-space members API 34).
//
// The arguments CameraDevice.createExtensionSession needs, aggregated into one
// object: which extension, the outputs, the executor and the state callback.
//
// This is a REAL implementation. AOSP's version is a plain mutable holder whose
// methods only read and write its own fields (setColorSpace additionally
// forwards to each OutputConfiguration, which is reproduced), so an app that
// builds one and reads it back sees identical behaviour here. Nothing consumes
// it today because CameraExtensionCharacteristics reports no extensions, but
// that decision lives there, not here.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/params/ExtensionSessionConfiguration.java
// (aosp-mirror/platform_frameworks_base, main): fields L40-45, constructor
// L56-63, getExtension L70, setPostviewOutputConfiguration L81,
// getPostviewOutputConfiguration L92, getOutputConfigurations L103,
// getStateCallback L114, getExecutor L124, setColorSpace L139-147,
// clearColorSpace L152-160, getColorSpace L169-175.
package android.hardware.camera2.params;

import android.graphics.ColorSpace;
import android.hardware.camera2.CameraExtensionSession;

import java.util.List;
import java.util.concurrent.Executor;

public final class ExtensionSessionConfiguration {

    /** ColorSpaceProfiles.UNSPECIFIED; see the same constant in
     *  OutputConfiguration for why it is inlined rather than pulled in. */
    private static final int COLOR_SPACE_UNSPECIFIED = -1;

    private int mExtensionType;
    private List<OutputConfiguration> mOutputs;
    private OutputConfiguration mPostviewOutput;
    private Executor mExecutor;
    private CameraExtensionSession.StateCallback mCallback;
    private int mColorSpace = COLOR_SPACE_UNSPECIFIED;

    public ExtensionSessionConfiguration(int extension, List<OutputConfiguration> outputs,
            Executor executor, CameraExtensionSession.StateCallback listener) {
        mExtensionType = extension;
        mOutputs = outputs;
        mExecutor = executor;
        mCallback = listener;
    }

    public int getExtension() {
        return mExtensionType;
    }

    /** API 34. Optional -- null means the app does not want a postview. */
    public void setPostviewOutputConfiguration(OutputConfiguration postviewOutput) {
        mPostviewOutput = postviewOutput;
    }

    /** API 34. */
    public OutputConfiguration getPostviewOutputConfiguration() {
        return mPostviewOutput;
    }

    public List<OutputConfiguration> getOutputConfigurations() {
        return mOutputs;
    }

    public CameraExtensionSession.StateCallback getStateCallback() {
        return mCallback;
    }

    public Executor getExecutor() {
        return mExecutor;
    }

    /** API 34. AOSP pushes the colour space down into every output and the
     *  postview as well, so a later read of an OutputConfiguration reflects it;
     *  that propagation is behaviour an app can observe, not an internal
     *  detail, so it is reproduced. */
    public void setColorSpace(ColorSpace.Named colorSpace) {
        if (colorSpace == null) {
            throw new NullPointerException("colorSpace must not be null");
        }
        mColorSpace = colorSpace.ordinal();
        for (OutputConfiguration outputConfiguration : mOutputs) {
            outputConfiguration.setColorSpace(colorSpace);
        }
        if (mPostviewOutput != null) {
            mPostviewOutput.setColorSpace(colorSpace);
        }
    }

    /** API 34. */
    public void clearColorSpace() {
        mColorSpace = COLOR_SPACE_UNSPECIFIED;
        for (OutputConfiguration outputConfiguration : mOutputs) {
            outputConfiguration.clearColorSpace();
        }
        if (mPostviewOutput != null) {
            mPostviewOutput.clearColorSpace();
        }
    }

    /** API 34. Null when never set, matching AOSP. */
    public ColorSpace getColorSpace() {
        if (mColorSpace != COLOR_SPACE_UNSPECIFIED) {
            return ColorSpace.get(ColorSpace.Named.values()[mColorSpace]);
        }
        return null;
    }
}
