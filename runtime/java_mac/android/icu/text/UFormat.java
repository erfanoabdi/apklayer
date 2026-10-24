package android.icu.text;

/**
 * ICU's common base for its formatters. In the platform, DateFormat /
 * MessageFormat / NumberFormat all extend android.icu.text.UFormat rather than
 * java.text.Format directly, so code doing `instanceof UFormat` (or casting a
 * formatter to it) needs the type to really sit in the chain -- a missing link
 * turns into a ClassCastException far from the declaration.
 *
 * UFormat adds no public members of its own beyond the constructor; its purpose
 * upstream is to carry the (hidden) locale-provenance API.
 *
 * Source: android.icu.text.UFormat (android-30 android.jar):
 *   public abstract class android.icu.text.UFormat extends java.text.Format
 */
public abstract class UFormat extends java.text.Format {
    public UFormat() {}

    /**
     * Rebuild a java.text formatter's attributed iterator with each attribute
     * key replaced by its android.icu.text counterpart (the attribute value is
     * the ICU field itself, as ICU sets it). Keys with no counterpart are
     * dropped; the text is unchanged.
     */
    static java.text.AttributedCharacterIterator translateAttributes(
            java.text.AttributedCharacterIterator it,
            java.util.Map<java.text.AttributedCharacterIterator.Attribute,
                    java.text.AttributedCharacterIterator.Attribute> map) {
        int begin = it.getBeginIndex();
        StringBuilder text = new StringBuilder(it.getEndIndex() - begin);
        for (char c = it.first(); c != java.text.CharacterIterator.DONE; c = it.next()) text.append(c);
        java.text.AttributedString out = new java.text.AttributedString(text.toString());
        if (text.length() == 0) return out.getIterator();
        for (java.text.AttributedCharacterIterator.Attribute key : it.getAllAttributeKeys()) {
            java.text.AttributedCharacterIterator.Attribute icu = map.get(key);
            if (icu == null) continue;
            it.setIndex(begin);
            while (it.getIndex() < it.getEndIndex()) {
                int start = it.getRunStart(key);
                int limit = it.getRunLimit(key);
                if (it.getAttribute(key) != null) out.addAttribute(icu, icu, start - begin, limit - begin);
                if (limit <= it.getIndex()) break;
                it.setIndex(limit);
            }
        }
        return out.getIterator();
    }
}
