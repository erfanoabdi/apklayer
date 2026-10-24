package com.google.mlkit.vision.text.latin;

import com.google.mlkit.vision.text.TextRecognizerOptionsInterface;
import java.util.concurrent.Executor;

// com.google.mlkit.vision.text.latin.TextRecognizerOptions -- options for the
// Latin-script (default) recognizer. The vast majority of ML Kit OCR apps pass
// TextRecognizerOptions.DEFAULT_OPTIONS. Mirrors the published Builder shape so
// that construction links; the values are inert because no model runs.
// Ref: developers.google.com/android/reference/.../text/latin/TextRecognizerOptions
public class TextRecognizerOptions implements TextRecognizerOptionsInterface {

    public static final TextRecognizerOptions DEFAULT_OPTIONS =
            new TextRecognizerOptions.Builder().build();

    private final Executor executor;

    private TextRecognizerOptions(Executor executor) { this.executor = executor; }

    @Override public int getLoggingLanguageOption() { return 1; }
    @Override public Executor getExecutor() { return executor; }

    public static class Builder {
        private Executor executor;
        public Builder setExecutor(Executor executor) { this.executor = executor; return this; }
        public TextRecognizerOptions build() { return new TextRecognizerOptions(executor); }
    }
}
