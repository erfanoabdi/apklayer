// android.text.Html -- HTML <-> styled-text conversion.
//
// Previously a completely empty class (v1.0.92 auto-generated stub), so
// every `Html.fromHtml(...)` call threw NoSuchMethodError -- a silent
// killer for the very common pattern of putting a formatted string
// (app descriptions, changelogs, rich labels) into a TextView via
// `textView.setText(Html.fromHtml(s))`. Apps either crashed on a
// background thread or showed nothing.
//
// AOSP backs fromHtml with a full TagSoup HTML parser that builds a
// Spanned with style spans (bold/italic/links/colors/etc.). We provide
// a pragmatic implementation: decode HTML entities, turn block-level
// tags into line breaks (and <li> into bullets), strip the remaining
// markup, and return the readable text as a Spanned (SpannableString).
// That recovers the 90% case -- legible, tag-free text -- without the
// span-styling machinery. Callers that only read getText()/toString()
// (the common case) get exactly what they expect; callers that walk
// spans simply find none (same as plain text).
package android.text;

public class Html {
    // AOSP fromHtml flag constants (API 24+). We accept them for source
    // compatibility; the legacy/compact distinction only affects how
    // consecutive block elements collapse, which our normalizer handles
    // uniformly.
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH         = 0x00000001;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_HEADING           = 0x00000002;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM         = 0x00000004;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST              = 0x00000008;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_DIV               = 0x00000010;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE        = 0x00000020;
    public static final int FROM_HTML_OPTION_USE_CSS_COLORS                  = 0x00000100;
    public static final int FROM_HTML_MODE_COMPACT =
        FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
            | FROM_HTML_SEPARATOR_LINE_BREAK_HEADING
            | FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM
            | FROM_HTML_SEPARATOR_LINE_BREAK_LIST
            | FROM_HTML_SEPARATOR_LINE_BREAK_DIV
            | FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE;
    public static final int FROM_HTML_MODE_LEGACY = 0x00000000;

    public static final int TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0x00000000;
    public static final int TO_HTML_PARAGRAPH_LINES_INDIVIDUAL  = 0x00000001;

    private Html() {}

    /** Supplies Drawables for <img> tags. We don't render inline images,
     *  so a passed getter is simply never called. */
    public interface ImageGetter {
        android.graphics.drawable.Drawable getDrawable(String source);
    }

    /** Handles unknown/custom tags. We don't dispatch to it (we strip
     *  unknown tags), but the type must exist so callers compile. */
    public interface TagHandler {
        void handleTag(boolean opening, String tag, Editable output, org.xml.sax.XMLReader xmlReader);
    }

    public static Spanned fromHtml(String source) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY, null, null);
    }
    public static Spanned fromHtml(String source, ImageGetter imageGetter, TagHandler tagHandler) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY, imageGetter, tagHandler);
    }
    public static Spanned fromHtml(String source, int flags) {
        return fromHtml(source, flags, null, null);
    }
    public static Spanned fromHtml(String source, int flags,
                                   ImageGetter imageGetter, TagHandler tagHandler) {
        // v1.0.46x: build a REAL Spanned with style spans (bold/italic/
        // underline/strike/color/link/sub/sup/size/img) instead of stripping to
        // plain text. AOSP uses a full TagSoup parser; we use a single-pass
        // tag-stack tokenizer that covers the common inline-styling tags apps
        // put through fromHtml (app descriptions, changelogs, rich labels). The
        // text content + entity decoding + block-break behavior matches the
        // prior implementation; spans are now attached so callers that walk
        // getSpans() (e.g. F-Droid's link binder) find them.
        return parseSpanned(source == null ? "" : source, imageGetter, tagHandler);
    }

    // A pending open tag plus the output offset where its content started.
    private static final class Open {
        final String name; final java.util.Map<String,String> attrs; final int start;
        Open(String name, java.util.Map<String,String> attrs, int start) {
            this.name = name; this.attrs = attrs; this.start = start;
        }
    }

    private static Spanned parseSpanned(String html, ImageGetter imageGetter,
                                        TagHandler tagHandler) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        java.util.ArrayDeque<Open> stack = new java.util.ArrayDeque<>();
        int n = html.length();
        int i = 0;
        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = html.indexOf('>', i + 1);
                if (end < 0) break;
                String raw = html.substring(i + 1, end).trim();
                i = end + 1;
                if (raw.startsWith("!") || raw.startsWith("?")) continue; // comment/decl
                boolean closing = raw.startsWith("/");
                boolean selfClose = raw.endsWith("/");
                if (closing) raw = raw.substring(1).trim();
                if (selfClose) raw = raw.substring(0, raw.length() - 1).trim();
                String name = tagName(raw).toLowerCase();
                java.util.Map<String,String> attrs = closing
                    ? java.util.Collections.emptyMap() : parseAttrs(raw);

                if (name.equals("br")) { out.append('\n'); continue; }
                if (name.equals("img") && !closing) {
                    appendImage(out, attrs, imageGetter);
                    continue;
                }
                if (!closing) {
                    appendOpenBlockBreak(out, name);
                    stack.push(new Open(name, attrs, out.length()));
                } else {
                    applyClose(out, stack, name, tagHandler);
                    appendCloseBlockBreak(out, name);
                }
            } else if (c == '&') {
                int end = html.indexOf(';', i + 1);
                if (end > i && end - i <= 12) {
                    out.append(decodeEntity(html.substring(i + 1, end)));
                    i = end + 1;
                } else {
                    out.append('&'); i++;
                }
            } else {
                // Collapse runs of inline whitespace to single spaces.
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    int len = out.length();
                    char prev = len > 0 ? out.charAt(len - 1) : '\n';
                    if (prev != ' ' && prev != '\n') out.append(' ');
                    i++;
                } else {
                    out.append(c); i++;
                }
            }
        }
        return out;
    }

    private static String tagName(String raw) {
        int sp = raw.length();
        for (int k = 0; k < raw.length(); k++) {
            char ch = raw.charAt(k);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '/') { sp = k; break; }
        }
        return raw.substring(0, sp);
    }

    private static java.util.Map<String,String> parseAttrs(String raw) {
        java.util.Map<String,String> m = new java.util.HashMap<>();
        int k = tagName(raw).length();
        while (k < raw.length()) {
            while (k < raw.length() && Character.isWhitespace(raw.charAt(k))) k++;
            int eq = raw.indexOf('=', k);
            if (eq < 0) break;
            String key = raw.substring(k, eq).trim().toLowerCase();
            int v = eq + 1;
            while (v < raw.length() && Character.isWhitespace(raw.charAt(v))) v++;
            String val;
            if (v < raw.length() && (raw.charAt(v) == '"' || raw.charAt(v) == '\'')) {
                char q = raw.charAt(v);
                int qe = raw.indexOf(q, v + 1);
                if (qe < 0) break;
                val = raw.substring(v + 1, qe);
                k = qe + 1;
            } else {
                int e = v;
                while (e < raw.length() && !Character.isWhitespace(raw.charAt(e))) e++;
                val = raw.substring(v, e);
                k = e;
            }
            if (!key.isEmpty()) m.put(key, val);
        }
        return m;
    }

    private static void appendImage(SpannableStringBuilder out,
                                    java.util.Map<String,String> attrs, ImageGetter g) {
        String src = attrs.get("src");
        out.append('￼'); // OBJECT REPLACEMENT CHARACTER, per AOSP
        if (g != null && src != null) {
            android.graphics.drawable.Drawable d = g.getDrawable(src);
            if (d != null) {
                if (d.getBounds().isEmpty()) {
                    int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
                    if (w > 0 && h > 0) d.setBounds(0, 0, w, h);
                }
                out.setSpan(new android.text.style.ImageSpan(d, src),
                    out.length() - 1, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void appendOpenBlockBreak(SpannableStringBuilder out, String name) {
        switch (name) {
            case "p": case "div": case "ul": case "ol": case "blockquote":
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                break;
            case "li":
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                out.append("• ");
                break;
            default: break;
        }
    }

    private static void appendCloseBlockBreak(SpannableStringBuilder out, String name) {
        switch (name) {
            case "p": case "div": case "tr": case "ul": case "ol": case "li":
            case "blockquote":
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                break;
            default: break;
        }
    }

    private static void applyClose(SpannableStringBuilder out,
                                   java.util.ArrayDeque<Open> stack, String name,
                                   TagHandler tagHandler) {
        // Pop until we find the matching open tag (tolerant of mis-nesting).
        Open match = null;
        java.util.ArrayDeque<Open> reopened = new java.util.ArrayDeque<>();
        while (!stack.isEmpty()) {
            Open o = stack.pop();
            if (o.name.equals(name)) { match = o; break; }
            reopened.push(o); // unmatched: keep for re-push (will be closed later)
        }
        while (!reopened.isEmpty()) stack.push(reopened.pop());
        if (match == null) {
            if (tagHandler != null) {
                try { tagHandler.handleTag(false, name, out, null); } catch (Throwable ignore) {}
            }
            return;
        }
        int start = match.start, endIdx = out.length();
        if (start >= endIdx) return; // empty element, no span needed
        Object span = spanFor(name, match.attrs);
        if (span != null) {
            out.setSpan(span, start, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (tagHandler != null) {
            try { tagHandler.handleTag(false, name, out, null); } catch (Throwable ignore) {}
        }
    }

    private static Object spanFor(String name, java.util.Map<String,String> attrs) {
        switch (name) {
            case "b": case "strong":
                return new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
            case "i": case "em": case "cite": case "dfn":
                return new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC);
            case "u": case "ins":
                return new android.text.style.UnderlineSpan();
            case "s": case "strike": case "del":
                return new android.text.style.StrikethroughSpan();
            case "sub":
                return new android.text.style.SubscriptSpan();
            case "sup":
                return new android.text.style.SuperscriptSpan();
            case "big":
                return new android.text.style.RelativeSizeSpan(1.25f);
            case "small":
                return new android.text.style.RelativeSizeSpan(0.8f);
            case "a": {
                String href = attrs.get("href");
                return href != null ? new android.text.style.URLSpan(href) : null;
            }
            case "font": {
                String color = attrs.get("color");
                Integer parsed = parseColor(color);
                if (parsed != null) {
                    return new android.text.style.ForegroundColorSpan(parsed);
                }
                return null;
            }
            default:
                return null;
        }
    }

    private static Integer parseColor(String c) {
        if (c == null) return null;
        c = c.trim();
        try {
            if (c.startsWith("#")) {
                String hex = c.substring(1);
                if (hex.length() == 6) return (int) (0xFF000000L | Long.parseLong(hex, 16));
                if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
                if (hex.length() == 3) {
                    int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                    int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                    int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
            switch (c.toLowerCase()) {
                case "black":  return 0xFF000000;
                case "white":  return 0xFFFFFFFF;
                case "red":    return 0xFFFF0000;
                case "green":  return 0xFF008000;
                case "blue":   return 0xFF0000FF;
                case "gray": case "grey": return 0xFF808080;
                case "yellow": return 0xFFFFFF00;
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Convert styled text back to HTML. We don't track spans, so this
     *  emits the escaped plain text wrapped in a single paragraph --
     *  enough for round-trip callers that just persist/restore a label. */
    public static String toHtml(Spanned text) {
        return toHtml(text, TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
    }
    public static String toHtml(Spanned text, int option) {
        // Line breaks are turned into <br> BEFORE the text is escaped, which is
        // the order AOSP uses too (withinDiv/withinParagraph emit the break and
        // hand only the newline-free segments to withinStyle). It has to be this
        // way round now that escapeHtml is AOSP-faithful: escapeHtml renders a
        // newline as the numeric entity &#10;, so a post-hoc replace("\n", ...)
        // would no longer find anything to replace.
        StringBuilder body = new StringBuilder();
        String s = (text == null) ? "" : text.toString();
        int start = 0;
        while (true) {
            int nl = s.indexOf('\n', start);
            int end = (nl < 0) ? s.length() : nl;
            body.append(escapeHtml(s.substring(start, end)));
            if (nl < 0) break;
            body.append("<br>\n");
            start = nl + 1;
        }
        return "<p dir=\"ltr\">" + body + "</p>\n";
    }

    /** AOSP Html.escapeHtml (frameworks/base core/java/android/text/Html.java:
     *  273-277), whose whole body is `withinStyle(out, text, 0, text.length())`
     *  (same file, lines 633-665). It is NOT the same function as
     *  TextUtils.htmlEncode, and delegating to that five-character switch was
     *  wrong in four separate ways that the answer-probe caught:
     *
     *    * QUOTES ARE NOT ESCAPED. withinStyle leaves ' and " alone; only
     *      &lt; &gt; &amp; are named entities. htmlEncode's &#39; / &quot;
     *      are its own thing, for XML attribute contexts.
     *    * NON-ASCII IS NUMERICALLY ESCAPED. Anything above 0x7E or below
     *      0x20 becomes &#dddd;, so "cafe(acute)" -> "caf&#233;" and a tab ->
     *      "&#9;". Passing them through produced output that only survives if
     *      the consumer happens to agree about the charset.
     *    * A SURROGATE PAIR IS ONE ESCAPE. An emoji becomes a single
     *      &#128512; for the whole code point, not two escapes or two raw
     *      surrogates (an unpaired surrogate is dropped entirely).
     *    * RUNS OF SPACES COLLAPSE-PROOF THEMSELVES. Every space but the last
     *      in a run becomes &nbsp;, because HTML would otherwise collapse
     *      them; "a  b" -> "a&nbsp; b".
     *
     *  AOSP has no null guard here, so escapeHtml(null) is an NPE from
     *  text.length(); toHtml above no longer relies on one. */
    public static String escapeHtml(CharSequence text) {
        StringBuilder out = new StringBuilder();
        withinStyle(out, text, 0, text.length());
        return out.toString();
    }

    /** AOSP Html.withinStyle (Html.java:633-665), verbatim in behaviour. */
    private static void withinStyle(StringBuilder out, CharSequence text,
                                    int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);

            if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                // Only a well-formed high+low pair produces output; a lone
                // surrogate is skipped rather than emitted, since it cannot be
                // represented as a character reference.
                if (c < 0xDC00 && i + 1 < end) {
                    char d = text.charAt(i + 1);
                    if (d >= 0xDC00 && d <= 0xDFFF) {
                        i++;
                        int codepoint = 0x010000 | (int) c - 0xD800 << 10 | (int) d - 0xDC00;
                        out.append("&#").append(codepoint).append(";");
                    }
                }
            } else if (c > 0x7E || c < ' ') {
                out.append("&#").append((int) c).append(";");
            } else if (c == ' ') {
                while (i + 1 < end && text.charAt(i + 1) == ' ') {
                    out.append("&nbsp;");
                    i++;
                }

                out.append(' ');
            } else {
                out.append(c);
            }
        }
    }

    // ---- pragmatic HTML -> readable text ----
    private static String htmlToText(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int n = html.length();
        int i = 0;
        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = html.indexOf('>', i + 1);
                if (end < 0) break;   // unterminated tag; drop the rest
                String tag = html.substring(i + 1, end).trim();
                appendTagBreak(out, tag);
                i = end + 1;
            } else if (c == '&') {
                int end = html.indexOf(';', i + 1);
                if (end > i && end - i <= 12) {
                    out.append(decodeEntity(html.substring(i + 1, end)));
                    i = end + 1;
                } else {
                    out.append('&');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return normalizeWhitespace(out.toString());
    }

    /** Map a tag to the whitespace it should contribute to the text run. */
    private static void appendTagBreak(StringBuilder out, String tag) {
        // Strip attributes: the tag name is up to the first space / slash.
        String name = tag;
        boolean closing = name.startsWith("/");
        if (closing) name = name.substring(1);
        int sp = name.length();
        for (int k = 0; k < name.length(); k++) {
            char ch = name.charAt(k);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '/' ) { sp = k; break; }
        }
        name = name.substring(0, sp).toLowerCase();
        switch (name) {
            case "br":
                out.append('\n');
                break;
            case "li":
                if (!closing) out.append("\n• ");   // bullet on open
                break;
            case "p":
            case "div":
            case "tr":
            case "ul":
            case "ol":
            case "blockquote":
            case "h1": case "h2": case "h3":
            case "h4": case "h5": case "h6":
                out.append('\n');                         // block boundary
                break;
            default:
                break;                                    // inline tag: drop
        }
    }

    private static String decodeEntity(String e) {
        if (e.isEmpty()) return "&;";
        if (e.charAt(0) == '#') {
            try {
                int cp;
                if (e.length() > 1 && (e.charAt(1) == 'x' || e.charAt(1) == 'X')) {
                    cp = Integer.parseInt(e.substring(2), 16);
                } else {
                    cp = Integer.parseInt(e.substring(1));
                }
                return new String(Character.toChars(cp));
            } catch (Exception ex) {
                return "&" + e + ";";
            }
        }
        switch (e) {
            case "amp":   return "&";
            case "lt":    return "<";
            case "gt":    return ">";
            case "quot":  return "\"";
            case "apos":  return "'";
            case "nbsp":  return " ";
            case "copy":  return "©";
            case "reg":   return "®";
            case "trade": return "™";
            case "mdash": return "—";
            case "ndash": return "–";
            case "hellip":return "…";
            case "bull":  return "•";
            case "deg":   return "°";
            case "euro":  return "€";
            case "pound": return "£";
            case "cent":  return "¢";
            case "laquo": return "«";
            case "raquo": return "»";
            case "ldquo": return "“";
            case "rdquo": return "”";
            case "lsquo": return "‘";
            case "rsquo": return "’";
            default:      return "&" + e + ";";   // unknown: leave literal
        }
    }

    /** Collapse runs of spaces/tabs and limit consecutive blank lines to
     *  one, matching how a browser renders HTML whitespace, then trim. */
    private static String normalizeWhitespace(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int newlineRun = 0;
        boolean lastWasSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                newlineRun++;
                lastWasSpace = false;
                if (newlineRun <= 2) sb.append('\n');
            } else if (c == ' ' || c == '\t' || c == '\r') {
                if (!lastWasSpace && newlineRun == 0) { sb.append(' '); lastWasSpace = true; }
                // spaces right after a newline are dropped (leading indent)
            } else {
                sb.append(c);
                lastWasSpace = false;
                newlineRun = 0;
            }
        }
        // Trim leading/trailing whitespace+newlines.
        int start = 0, endIdx = sb.length();
        while (start < endIdx && Character.isWhitespace(sb.charAt(start))) start++;
        while (endIdx > start && Character.isWhitespace(sb.charAt(endIdx - 1))) endIdx--;
        return sb.substring(start, endIdx);
    }
}
