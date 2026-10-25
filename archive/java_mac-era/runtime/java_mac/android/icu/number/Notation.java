package android.icu.number;
/** Base for ICU number notation styles. Factory methods return the concrete
 *  notation; Mac.apk formats via the host, so these are structural holders. */
public class Notation {
    Notation() {}
    public static CompactNotation compactShort() { return new CompactNotation(); }
    public static CompactNotation compactLong() { return new CompactNotation(); }
    // scientific()/engineering()/simple() return the TOP-LEVEL concrete types in
    // the platform (android.icu.number.{ScientificNotation,SimpleNotation}); a
    // nested type or the Notation base gives a different method descriptor.
    public static ScientificNotation scientific() { return new ScientificNotation(); }
    public static ScientificNotation engineering() { return new ScientificNotation(); }
    public static SimpleNotation simple() { return new SimpleNotation(); }
}
