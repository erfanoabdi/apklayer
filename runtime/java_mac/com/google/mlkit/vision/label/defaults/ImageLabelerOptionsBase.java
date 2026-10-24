package com.google.mlkit.vision.label.defaults;

// com.google.mlkit.vision.label.defaults.ImageLabelerOptionsBase -- common
// supertype of the default and custom labeler options. ImageLabeling.getClient
// accepts this type. Mirrors the published abstract base; the only member apps
// read is getConfidenceThreshold. Ref: developers.google.com/android/reference
public abstract class ImageLabelerOptionsBase {
    public abstract float getConfidenceThreshold();
}
