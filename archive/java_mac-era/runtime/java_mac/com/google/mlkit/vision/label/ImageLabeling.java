package com.google.mlkit.vision.label;

import com.google.mlkit.vision.label.defaults.ImageLabelerOptionsBase;

// com.google.mlkit.vision.label.ImageLabeling -- entry-point factory for an
// ImageLabeler. Both overloads return our Default labeler whose process() fails
// cleanly. Mirrors the published static factory.
// Ref: developers.google.com/android/reference/.../label/ImageLabeling
public final class ImageLabeling {
    private ImageLabeling() {}

    public static ImageLabeler getClient() {
        return new ImageLabeler.Default();
    }

    public static ImageLabeler getClient(ImageLabelerOptionsBase options) {
        return new ImageLabeler.Default();
    }
}
