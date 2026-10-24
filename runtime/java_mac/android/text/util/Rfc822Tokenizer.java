package android.text.util;

import android.widget.MultiAutoCompleteTextView;

import java.util.ArrayList;
import java.util.Collection;

/**
 * AOSP-faithful android.text.util.Rfc822Tokenizer. Works as a Tokenizer for
 * MultiAutoCompleteTextView address-list fields and converts a string of
 * addresses into Rfc822Tokens. The parsing logic is a verbatim port of
 * frameworks/base/core/java/android/text/util/Rfc822Tokenizer.java so the
 * tolerant broken-syntax behavior matches Android exactly.
 */
public class Rfc822Tokenizer implements MultiAutoCompleteTextView.Tokenizer {

    public static void tokenize(CharSequence text, Collection<Rfc822Token> out) {
        StringBuilder name = new StringBuilder();
        StringBuilder address = new StringBuilder();
        StringBuilder comment = new StringBuilder();

        int i = 0;
        int cursor = text.length();

        while (i < cursor) {
            char c = text.charAt(i);

            if (c == ',' || c == ';') {
                i++;

                while (i < cursor && text.charAt(i) == ' ') {
                    i++;
                }

                crunch(name);

                if (address.length() > 0) {
                    out.add(new Rfc822Token(name.toString(),
                                            address.toString(),
                                            comment.toString()));
                } else if (name.length() > 0) {
                    out.add(new Rfc822Token(null,
                                            name.toString(),
                                            comment.toString()));
                }

                name.setLength(0);
                address.setLength(0);
                comment.setLength(0);
            } else if (c == '"') {
                i++;

                while (i < cursor) {
                    c = text.charAt(i);

                    if (c == '"') {
                        i++;
                        break;
                    } else if (c == '\\') {
                        if (i + 1 < cursor) {
                            name.append(text.charAt(i + 1));
                        }
                        i += 2;
                    } else {
                        name.append(c);
                        i++;
                    }
                }
            } else if (c == '(') {
                int level = 1;
                i++;

                while (i < cursor && level > 0) {
                    c = text.charAt(i);

                    if (c == ')') {
                        if (level > 1) {
                            comment.append(c);
                        }

                        level--;
                        i++;
                    } else if (c == '(') {
                        comment.append(c);
                        level++;
                        i++;
                    } else if (c == '\\') {
                        if (i + 1 < cursor) {
                            comment.append(text.charAt(i + 1));
                        }
                        i += 2;
                    } else {
                        comment.append(c);
                        i++;
                    }
                }
            } else if (c == '<') {
                i++;

                while (i < cursor) {
                    c = text.charAt(i);

                    if (c == '>') {
                        i++;
                        break;
                    } else {
                        address.append(c);
                        i++;
                    }
                }
            } else if (c == ' ') {
                name.append('\0');
                i++;
            } else {
                name.append(c);
                i++;
            }
        }

        crunch(name);

        if (address.length() > 0) {
            out.add(new Rfc822Token(name.toString(),
                                    address.toString(),
                                    comment.toString()));
        } else if (name.length() > 0) {
            out.add(new Rfc822Token(null,
                                    name.toString(),
                                    comment.toString()));
        }
    }

    public static Rfc822Token[] tokenize(CharSequence text) {
        ArrayList<Rfc822Token> out = new ArrayList<Rfc822Token>();
        tokenize(text, out);
        return out.toArray(new Rfc822Token[out.size()]);
    }

    private static void crunch(StringBuilder sb) {
        int i = 0;
        int len = sb.length();

        while (i < len) {
            char c = sb.charAt(i);

            if (c == '\0') {
                if (i == 0 || i == len - 1 ||
                    sb.charAt(i - 1) == ' ' ||
                    sb.charAt(i - 1) == '\0' ||
                    sb.charAt(i + 1) == ' ' ||
                    sb.charAt(i + 1) == '\0') {
                    sb.deleteCharAt(i);
                    len--;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }

        for (i = 0; i < len; i++) {
            if (sb.charAt(i) == '\0') {
                sb.setCharAt(i, ' ');
            }
        }
    }

    public int findTokenStart(CharSequence text, int cursor) {
        // It's hard to search backward, so search forward until we reach
        // the cursor.
        int best = 0;
        int i = 0;

        while (i < cursor) {
            i = findTokenEnd(text, i);

            if (i < cursor) {
                i++; // Skip terminating punctuation

                while (i < cursor && text.charAt(i) == ' ') {
                    i++;
                }

                if (i < cursor) {
                    best = i;
                }
            }
        }

        return best;
    }

    public int findTokenEnd(CharSequence text, int cursor) {
        int len = text.length();
        int i = cursor;

        while (i < len) {
            char c = text.charAt(i);

            if (c == ',' || c == ';') {
                return i;
            } else if (c == '"') {
                i++;

                while (i < len) {
                    c = text.charAt(i);

                    if (c == '"') {
                        i++;
                        break;
                    } else if (c == '\\' && i + 1 < len) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
            } else if (c == '(') {
                int level = 1;
                i++;

                while (i < len && level > 0) {
                    c = text.charAt(i);

                    if (c == ')') {
                        level--;
                        i++;
                    } else if (c == '(') {
                        level++;
                        i++;
                    } else if (c == '\\' && i + 1 < len) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
            } else if (c == '<') {
                i++;

                while (i < len) {
                    c = text.charAt(i);

                    if (c == '>') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
            } else {
                i++;
            }
        }

        return i;
    }

    public CharSequence terminateToken(CharSequence text) {
        return text + ", ";
    }
}
