package com.google.mlkit.vision.label;

// com.google.mlkit.vision.label.ImageLabel -- one label produced by the image
// labeler. Mac.apk runs no labeling model, so a label Task fails and apps never
// receive an ImageLabel via the success path; the type exists so success-listener
// generics and field reads link. Mirrors the published accessors (text,
// confidence, index). Ref: developers.google.com/android/reference/.../label/ImageLabel
public class ImageLabel {

    private final String text;
    private final float confidence;
    private final int index;

    public ImageLabel() { this("", 0f, -1); }
    public ImageLabel(String text, float confidence, int index) {
        this.text = text == null ? "" : text;
        this.confidence = confidence;
        this.index = index;
    }

    public String getText() { return text; }
    public float getConfidence() { return confidence; }
    public int getIndex() { return index; }
}
