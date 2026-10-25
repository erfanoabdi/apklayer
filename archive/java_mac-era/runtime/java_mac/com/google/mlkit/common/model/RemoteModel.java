package com.google.mlkit.common.model;

// com.google.mlkit.common.model.RemoteModel -- base for models downloaded from a
// remote source (Firebase ML custom models, etc.). Abstract in the real SDK;
// concrete subclasses carry the download source. Mac.apk performs no model
// download, so RemoteModelManager.download (not shipped) would fail cleanly; this
// base exists so apps that reference RemoteModel or subclass it link. Mirrors the
// published getModelName accessor.
// Ref: developers.google.com/android/reference/.../common/model/RemoteModel
public abstract class RemoteModel {

    private final String modelName;

    protected RemoteModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() { return modelName; }
    public String getModelNameForBackend() { return modelName; }
    public String getUniqueModelNameForPersist() { return modelName; }
}
