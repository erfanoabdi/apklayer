package hello.cdsloader;

/** The nest Main.Own defines a copy of (a runtime package of its own: public). */
public class Nest {
    public static class Inner { }
    public static class Sibling { public Sibling() {} @Override public String toString() { return "sibling"; } }
}
