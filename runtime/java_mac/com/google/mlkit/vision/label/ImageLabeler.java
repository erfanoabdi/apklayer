package com.google.mlkit.vision.label;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import java.io.Closeable;
import java.util.List;

// com.google.mlkit.vision.label.ImageLabeler -- the labeler handle. No on-device
// labeling model is wired, so process() returns a Task that fails with
// SERVICE_MISSING; the app degrades on its failure listener. Closeable mirrors
// the real interface. Ref: developers.google.com/android/reference/.../label/ImageLabeler
public interface ImageLabeler extends Closeable {

    Task<List<ImageLabel>> process(InputImage image);

    @Override void close();

    final class Default implements ImageLabeler {
        @Override public Task<List<ImageLabel>> process(InputImage image) {
            return Tasks.forException(new ApiException(
                    new Status(CommonStatusCodes.SERVICE_MISSING,
                            "ML Kit labeling model unavailable")));
        }
        @Override public void close() {}
    }
}
