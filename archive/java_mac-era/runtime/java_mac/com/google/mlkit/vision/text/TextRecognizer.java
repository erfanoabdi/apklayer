package com.google.mlkit.vision.text;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import java.io.Closeable;

// com.google.mlkit.vision.text.TextRecognizer -- the OCR detector handle.
// On-device OCR needs a bundled/downloaded model that Mac.apk does not ship, so
// process() returns a Task that fails with SERVICE_MISSING (honest "the ML Kit
// model backend is not available on this device"). The app's failure listener
// fires and it degrades, exactly as it would when a model download is blocked.
// Implements Closeable to mirror the real interface (apps call close() in
// onDestroy). Ref: developers.google.com/android/reference/.../text/TextRecognizer
public interface TextRecognizer extends Closeable {

    Task<Text> process(InputImage image);

    int getDetectorType();

    @Override void close();

    // Internal default impl returned by TextRecognition.getClient. Kept as a
    // nested class so callers only ever see the published TextRecognizer type.
    final class Default implements TextRecognizer {
        @Override public Task<Text> process(InputImage image) {
            return Tasks.forException(new ApiException(
                    new Status(CommonStatusCodes.SERVICE_MISSING,
                            "ML Kit text model unavailable")));
        }
        @Override public int getDetectorType() { return 0; }
        @Override public void close() {}
    }
}
