// v1.0.124: AOSP `TextDirectionHeuristic` -- interface for text-bidi
// heuristics. Used by Compose UI's paragraph chain alongside
// TextDirectionHeuristics.{LTR, RTL, FIRSTSTRONG_LTR, ...}.
//
// Per cs.android.com/.../text/TextDirectionHeuristic.java this is an
// interface with two isRtl methods. Was a stubbed CLASS in v1.0.92;
// promoted to interface here so static finals can implement it.
package android.text;

public interface TextDirectionHeuristic {
    boolean isRtl(char[] array, int start, int count);
    boolean isRtl(CharSequence cs, int start, int count);
}
