package android.icu.text;
/** ICU script/character transliterator. Mac.apk ships no transliteration tables,
 *  so instances pass text through unchanged; the API resolves. */
public class Transliterator {
    private final String id;
    protected Transliterator(String id) { this.id = id; }
    public static Transliterator getInstance(String id) { return new Transliterator(id); }
    public final String getID() { return id; }
    public String transliterate(String text) { return text; }
}
