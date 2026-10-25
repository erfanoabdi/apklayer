// org.xmlpull.v1.XmlPullParserException -- exception thrown by
// XmlPullParser methods. Apps catch this type explicitly (see
// PreferenceInflater's try/catch around inflate()), so the class
// must be a real Throwable with matching constructors.
//
// Mirrors xpp3 1.1.4c's public surface: row/column accessors,
// detail-cause chaining, and the four standard constructors. The
// `protected` access on row/column matches xpp3 (subclasses set
// them via the public setters).
package org.xmlpull.v1;

public class XmlPullParserException extends Exception {
    protected Throwable detail;
    protected int       row = -1;
    protected int       column = -1;

    public XmlPullParserException(String s) { super(s); }

    public XmlPullParserException(String msg, XmlPullParser parser, Throwable chain) {
        super((msg == null ? "" : msg + " ")
            + (parser == null ? "" : "(position:" + parser.getPositionDescription() + ") ")
            + (chain == null ? "" : "caused by: " + chain));
        if (parser != null) {
            this.row    = parser.getLineNumber();
            this.column = parser.getColumnNumber();
        }
        this.detail = chain;
    }

    public Throwable getDetail() { return detail; }
    public int       getLineNumber()   { return row; }
    public int       getColumnNumber() { return column; }
}
