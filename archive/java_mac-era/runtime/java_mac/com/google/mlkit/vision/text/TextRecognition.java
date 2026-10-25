package com.google.mlkit.vision.text;

// com.google.mlkit.vision.text.TextRecognition -- the entry point factory apps
// call to obtain a TextRecognizer. Both overloads return our Default recognizer
// whose process() fails cleanly (no model backend). Mirrors the published static
// factory. Ref: developers.google.com/android/reference/.../text/TextRecognition
public final class TextRecognition {
    private TextRecognition() {}

    public static TextRecognizer getClient() {
        return new TextRecognizer.Default();
    }

    public static TextRecognizer getClient(TextRecognizerOptionsInterface options) {
        return new TextRecognizer.Default();
    }
}
