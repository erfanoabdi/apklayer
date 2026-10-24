package com.google.mlkit.vision.label.defaults;

// com.google.mlkit.vision.label.defaults.ImageLabelerOptions -- options for the
// bundled default labeler. Most labeling apps pass
// ImageLabelerOptions.DEFAULT_OPTIONS. Mirrors the published Builder
// (setConfidenceThreshold). Values are inert because no labeler runs.
// Ref: developers.google.com/android/reference/.../label/defaults/ImageLabelerOptions
public class ImageLabelerOptions extends ImageLabelerOptionsBase {

    public static final ImageLabelerOptions DEFAULT_OPTIONS =
            new ImageLabelerOptions.Builder().build();

    private final float confidenceThreshold;

    private ImageLabelerOptions(float confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override public float getConfidenceThreshold() { return confidenceThreshold; }

    public static class Builder {
        private float confidenceThreshold = 0.5f;
        public Builder setConfidenceThreshold(float confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold; return this;
        }
        public ImageLabelerOptions build() { return new ImageLabelerOptions(confidenceThreshold); }
    }
}
