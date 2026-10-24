package android.text;
// android.text.NoCopySpan: a marker interface for spans that should not be
// copied into a new Spannable. CandyCrush references it at class-init.
public interface NoCopySpan {
    class Concrete implements NoCopySpan {}
}
