package com.google.mlkit.common.model;

// com.google.mlkit.common.model.LocalModel -- describes a model bundled in the
// app's assets or stored at an absolute path, used by custom-model detectors
// (custom image labeling, object detection). Pure config; Mac.apk runs no
// inference so the descriptor is never loaded, but the type and its Builder must
// link for apps that construct one. Mirrors the published Builder
// (setAssetFilePath / setAbsoluteFilePath).
// Ref: developers.google.com/android/reference/.../common/model/LocalModel
public class LocalModel {

    private final String assetFilePath;
    private final String absoluteFilePath;

    private LocalModel(String assetFilePath, String absoluteFilePath) {
        this.assetFilePath = assetFilePath;
        this.absoluteFilePath = absoluteFilePath;
    }

    public String getAssetFilePath() { return assetFilePath; }
    public String getAbsoluteFilePath() { return absoluteFilePath; }

    public static class Builder {
        private String assetFilePath;
        private String absoluteFilePath;
        public Builder setAssetFilePath(String assetFilePath) { this.assetFilePath = assetFilePath; return this; }
        public Builder setAbsoluteFilePath(String absoluteFilePath) { this.absoluteFilePath = absoluteFilePath; return this; }
        public LocalModel build() { return new LocalModel(assetFilePath, absoluteFilePath); }
    }
}
