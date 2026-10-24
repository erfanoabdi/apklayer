package com.google.mlkit.vision.face;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import java.io.Closeable;
import java.util.List;

// com.google.mlkit.vision.face.FaceDetector -- the detector handle. No on-device
// face model is wired, so process() returns a Task that fails with
// SERVICE_MISSING; the app degrades on its failure listener. Closeable mirrors
// the real interface. Ref: developers.google.com/android/reference/.../face/FaceDetector
public interface FaceDetector extends Closeable {

    Task<List<Face>> process(InputImage image);

    @Override void close();

    final class Default implements FaceDetector {
        @Override public Task<List<Face>> process(InputImage image) {
            return Tasks.forException(new ApiException(
                    new Status(CommonStatusCodes.SERVICE_MISSING,
                            "ML Kit face model unavailable")));
        }
        @Override public void close() {}
    }
}
