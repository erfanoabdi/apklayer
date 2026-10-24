package com.google.mlkit.vision.text;

// com.google.mlkit.vision.text.TextRecognizerOptionsInterface -- the common
// supertype of every script-specific options object (latin / chinese / devanagari
// / japanese / korean). TextRecognition.getClient accepts this type, so the
// concrete options classes implement it. Mirrors the published interface; the
// only member apps read is getExecutor (nullable, defaults to null = ML Kit's
// own executor). Ref: developers.google.com/android/reference
public interface TextRecognizerOptionsInterface {
    int getLoggingLanguageOption();
    java.util.concurrent.Executor getExecutor();
}
