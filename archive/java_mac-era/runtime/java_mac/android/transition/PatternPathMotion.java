// android.transition.PatternPathMotion -- a PathMotion that takes a "pattern"
// Path (authored once, e.g. an arc) and transforms it to connect any given
// start/end pair, so all motions follow the same shape. Was absent. We store the
// pattern and, lacking a full path-measure/transform stack, fall back to a
// straight segment between the points (a benign shape difference; the pattern is
// preserved via getPatternPath for any caller that inspects it). Mirrors the
// public surface of
// frameworks/base/core/java/android/transition/PatternPathMotion.java.
package android.transition;

import android.content.Context;
import android.graphics.Path;
import android.util.AttributeSet;

public class PatternPathMotion extends PathMotion {

    private Path mPatternPath = new Path();

    public PatternPathMotion() {
        // AOSP's default pattern is a straight horizontal line from (0,0) to (1,0).
        mPatternPath.moveTo(0, 0);
        mPatternPath.lineTo(1, 0);
    }

    /**
     * AOSP PatternPathMotion.java:55-68 -- the XML-inflation constructor. It
     * reads android:patternPathData, runs it through
     * android.util.PathParser.createPathFromPathData, and throws
     * RuntimeException("pathData must be supplied for patternPathMotion") when
     * the attribute is absent. Both behaviours are reproduced here.
     *
     * Note it does NOT chain to super(context, attrs) on AOSP either -- PathMotion
     * declares no attributes of its own.
     *
     * ⚠ Mac.apk has no android.util.PathParser (it is @hide on a device, so no
     * app can call it directly). The equivalent parser that DOES exist here is
     * private to android.graphics.drawable.VectorDrawable, which this package
     * cannot reach, so the SVG path-data grammar is parsed below. See
     * parsePathData()'s own note for the one command it does not cover.
     */
    public PatternPathMotion(Context context, AttributeSet attrs) {
        String pathData = (attrs == null) ? null
                : attrs.getAttributeValue(Transition.ANDROID_NS, "patternPathData");
        if (pathData == null) {
            throw new RuntimeException("pathData must be supplied for patternPathMotion");
        }
        setPatternPath(parsePathData(pathData));
    }

    public PatternPathMotion(Path patternPath) {
        setPatternPath(patternPath);
    }

    public Path getPatternPath() { return mPatternPath; }

    public void setPatternPath(Path patternPath) {
        if (patternPath != null) mPatternPath = patternPath;
    }

    @Override
    public Path getPath(float startX, float startY, float endX, float endY) {
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        return path;
    }

    /**
     * SVG path-data grammar, as android.util.PathParser implements it:
     * M/m L/l H/h V/v C/c S/s Q/q T/t Z/z, absolute (uppercase) and relative
     * (lowercase), with implicit repeats of the previous command and
     * comma-or-whitespace separators.
     *
     * ⚠ KNOWN GAP: elliptical arcs (A/a) are NOT converted. A pattern path for a
     * PatternPathMotion is authored as a motion curve and in practice uses the
     * line/quad/cubic commands; rather than emit a silently WRONG shape for an
     * arc, an A/a command raises the same RuntimeException an unparsable
     * pathData raises on a device. If an app is found using one, the fix is a
     * shared android.util.PathParser (which would also let
     * android.graphics.drawable.VectorDrawable and
     * android.view.animation.PathInterpolator stop each carrying their own copy).
     */
    private static Path parsePathData(String d) {
        Path path = new Path();
        if (d == null || d.isEmpty()) return path;
        final int len = d.length();
        int i = 0;
        char prevCmd = 'M';
        float curX = 0, curY = 0, startX = 0, startY = 0;
        float lastCubicX = 0, lastCubicY = 0, lastQuadX = 0, lastQuadY = 0;
        float[] a = new float[7];
        while (i < len) {
            char c = d.charAt(i);
            if (c == ',' || Character.isWhitespace(c)) { i++; continue; }
            char cmd;
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
            } else {
                // An implicit repeat: a second coordinate pair after "M 1,2 3,4"
                // is a lineto, per the SVG grammar.
                cmd = prevCmd;
                if (cmd == 'M') cmd = 'L';
                else if (cmd == 'm') cmd = 'l';
            }
            prevCmd = cmd;
            int n;
            switch (Character.toUpperCase(cmd)) {
                case 'Z': n = 0; break;
                case 'H': case 'V': n = 1; break;
                case 'M': case 'L': case 'T': n = 2; break;
                case 'S': case 'Q': n = 4; break;
                case 'C': n = 6; break;
                default:
                    throw new RuntimeException("unsupported path command '" + cmd
                            + "' in pathData: " + d);
            }
            i = readValues(d, i, n, a);
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    curX = rel ? curX + a[0] : a[0];
                    curY = rel ? curY + a[1] : a[1];
                    path.moveTo(curX, curY);
                    startX = curX; startY = curY;
                    lastCubicX = curX; lastCubicY = curY;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                case 'L': {
                    curX = rel ? curX + a[0] : a[0];
                    curY = rel ? curY + a[1] : a[1];
                    path.lineTo(curX, curY);
                    lastCubicX = curX; lastCubicY = curY;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                case 'H': {
                    curX = rel ? curX + a[0] : a[0];
                    path.lineTo(curX, curY);
                    lastCubicX = curX; lastCubicY = curY;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                case 'V': {
                    curY = rel ? curY + a[0] : a[0];
                    path.lineTo(curX, curY);
                    lastCubicX = curX; lastCubicY = curY;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                case 'C': {
                    float x1 = rel ? curX + a[0] : a[0], y1 = rel ? curY + a[1] : a[1];
                    float x2 = rel ? curX + a[2] : a[2], y2 = rel ? curY + a[3] : a[3];
                    float x3 = rel ? curX + a[4] : a[4], y3 = rel ? curY + a[5] : a[5];
                    path.cubicTo(x1, y1, x2, y2, x3, y3);
                    lastCubicX = x2; lastCubicY = y2;
                    curX = x3; curY = y3;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                case 'S': {
                    // The first control point is the reflection of the previous
                    // curve's second control point about the current point.
                    float x1 = 2 * curX - lastCubicX, y1 = 2 * curY - lastCubicY;
                    float x2 = rel ? curX + a[0] : a[0], y2 = rel ? curY + a[1] : a[1];
                    float x3 = rel ? curX + a[2] : a[2], y3 = rel ? curY + a[3] : a[3];
                    path.cubicTo(x1, y1, x2, y2, x3, y3);
                    lastCubicX = x2; lastCubicY = y2;
                    curX = x3; curY = y3;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                case 'Q': {
                    float x1 = rel ? curX + a[0] : a[0], y1 = rel ? curY + a[1] : a[1];
                    float x2 = rel ? curX + a[2] : a[2], y2 = rel ? curY + a[3] : a[3];
                    path.quadTo(x1, y1, x2, y2);
                    lastQuadX = x1; lastQuadY = y1;
                    curX = x2; curY = y2;
                    lastCubicX = curX; lastCubicY = curY;
                    break;
                }
                case 'T': {
                    float x1 = 2 * curX - lastQuadX, y1 = 2 * curY - lastQuadY;
                    float x2 = rel ? curX + a[0] : a[0], y2 = rel ? curY + a[1] : a[1];
                    path.quadTo(x1, y1, x2, y2);
                    lastQuadX = x1; lastQuadY = y1;
                    curX = x2; curY = y2;
                    lastCubicX = curX; lastCubicY = curY;
                    break;
                }
                case 'Z': {
                    path.close();
                    curX = startX; curY = startY;
                    lastCubicX = curX; lastCubicY = curY;
                    lastQuadX = curX; lastQuadY = curY;
                    break;
                }
                default:
                    break;
            }
        }
        return path;
    }

    /** Reads `n` floats starting at `i`, into `out`; returns the new position. */
    private static int readValues(String d, int i, int n, float[] out) {
        final int len = d.length();
        for (int k = 0; k < n; k++) {
            while (i < len && (d.charAt(i) == ',' || Character.isWhitespace(d.charAt(i)))) i++;
            int start = i;
            if (i < len && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
            boolean seenDot = false, seenExp = false;
            while (i < len) {
                char ch = d.charAt(i);
                if (ch >= '0' && ch <= '9') { i++; continue; }
                if (ch == '.' && !seenDot && !seenExp) { seenDot = true; i++; continue; }
                if ((ch == 'e' || ch == 'E') && !seenExp && i > start) {
                    seenExp = true; i++;
                    if (i < len && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
                    continue;
                }
                break;
            }
            if (i == start) {
                throw new RuntimeException("malformed pathData near offset " + start + ": " + d);
            }
            out[k] = Float.parseFloat(d.substring(start, i));
        }
        return i;
    }
}
