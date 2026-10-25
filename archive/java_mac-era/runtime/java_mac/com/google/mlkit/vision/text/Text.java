package com.google.mlkit.vision.text;

import android.graphics.Rect;
import android.graphics.Point;
import java.util.Collections;
import java.util.List;

// com.google.mlkit.vision.text.Text -- the OCR result tree (Text > TextBlock >
// Line > Element > Symbol). Mac.apk runs no OCR model, so a real recognition
// Task fails and an app never receives a Text instance through the success path;
// this type exists so the success-listener generic and any code that references
// Text/TextBlock/Line compiles and links. An empty result mirrors "nothing
// recognized" honestly. Ref: developers.google.com/android/reference/.../text/Text
public class Text {

    private final String text;
    private final List<TextBlock> blocks;

    public Text() { this("", Collections.<TextBlock>emptyList()); }
    public Text(String text, List<TextBlock> blocks) {
        this.text = text == null ? "" : text;
        this.blocks = blocks == null ? Collections.<TextBlock>emptyList() : blocks;
    }

    public String getText() { return text; }
    public List<TextBlock> getTextBlocks() { return blocks; }

    // Shared base for every recognized region.
    public static class TextBase {
        private final String text;
        private final Rect boundingBox;
        private final Point[] cornerPoints;
        private final String language;
        private final float confidence;
        protected TextBase(String text, Rect boundingBox, Point[] cornerPoints,
                String language, float confidence) {
            this.text = text == null ? "" : text;
            this.boundingBox = boundingBox;
            this.cornerPoints = cornerPoints;
            this.language = language;
            this.confidence = confidence;
        }
        public String getText() { return text; }
        public Rect getBoundingBox() { return boundingBox; }
        public Point[] getCornerPoints() { return cornerPoints; }
        public String getRecognizedLanguage() { return language; }
        public float getConfidence() { return confidence; }
    }

    public static class TextBlock extends TextBase {
        private final List<Line> lines;
        public TextBlock(String text, Rect box, Point[] corners, String lang,
                float conf, List<Line> lines) {
            super(text, box, corners, lang, conf);
            this.lines = lines == null ? Collections.<Line>emptyList() : lines;
        }
        public List<Line> getLines() { return lines; }
    }

    public static class Line extends TextBase {
        private final List<Element> elements;
        public Line(String text, Rect box, Point[] corners, String lang,
                float conf, List<Element> elements) {
            super(text, box, corners, lang, conf);
            this.elements = elements == null ? Collections.<Element>emptyList() : elements;
        }
        public List<Element> getElements() { return elements; }
        public float getAngle() { return 0f; }
    }

    public static class Element extends TextBase {
        private final List<Symbol> symbols;
        public Element(String text, Rect box, Point[] corners, String lang,
                float conf, List<Symbol> symbols) {
            super(text, box, corners, lang, conf);
            this.symbols = symbols == null ? Collections.<Symbol>emptyList() : symbols;
        }
        public List<Symbol> getSymbols() { return symbols; }
        public float getAngle() { return 0f; }
    }

    public static class Symbol extends TextBase {
        public Symbol(String text, Rect box, Point[] corners, String lang, float conf) {
            super(text, box, corners, lang, conf);
        }
        public float getAngle() { return 0f; }
    }
}
