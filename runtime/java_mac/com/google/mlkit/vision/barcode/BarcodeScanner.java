package com.google.mlkit.vision.barcode;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.io.Closeable;
import java.util.List;

// com.google.mlkit.vision.barcode.BarcodeScanner -- the scanner handle. With no
// on-device barcode model wired, process() returns a Task that fails with
// SERVICE_MISSING (honest "scanner backend unavailable"); the app's failure
// listener fires and it degrades. Implements Closeable to mirror the real
// interface. Ref: developers.google.com/android/reference/.../barcode/BarcodeScanner
public interface BarcodeScanner extends Closeable {

    Task<List<Barcode>> process(InputImage image);

    @Override void close();

    final class Default implements BarcodeScanner {
        @Override public Task<List<Barcode>> process(InputImage image) {
            return Tasks.forException(new ApiException(
                    new Status(CommonStatusCodes.SERVICE_MISSING,
                            "ML Kit barcode model unavailable")));
        }
        @Override public void close() {}
    }
}
