package apklayer.host;

import android.media.MediaRecorder;
import org.robolectric.annotation.Implements;

/** The original MediaRecorder over the real recorder service (StagefrightRecorder, MPEG4Writer, the tree's encoders), not Robolectric's fake that writes nothing. */
@Implements(value = MediaRecorder.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeMediaRecorder {
}
