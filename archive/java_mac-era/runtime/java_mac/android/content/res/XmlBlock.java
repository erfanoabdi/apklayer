package android.content.res;

import com.macapk.loader.BinaryXmlParser;
import com.macapk.loader.DomXmlResourceParser;
import com.macapk.loader.NativeLog;

import org.w3c.dom.Document;

/**
 * AOSP's wrapper around a block of COMPILED (binary) XML.
 *
 * AOSP: frameworks/base/core/java/android/content/res/XmlBlock.java --
 * {@code public final class XmlBlock implements AutoCloseable}, marked
 * {@code @hide}. On a device it wraps a native ResXMLTree; the only observable
 * surface is the (byte[]) constructor, {@link #newParser()} and {@link #close()}.
 *
 * WHY WE NEED IT. It is hidden, so nothing calls it by name at compile time --
 * apps reach it REFLECTIVELY. Instagram 445.0.0.45.83 does exactly that during
 * AppInit:
 *
 *   Class.forName("android.content.res.XmlBlock")
 *     .getDeclaredConstructor(byte[].class)     -> setAccessible(true)
 *     .getDeclaredMethod("newParser")           -> setAccessible(true)
 *     .getDeclaredMethod("close")               -> setAccessible(true)
 *
 * and wraps the three in a helper (X.3zt) that turns a byte[] of compiled XML
 * into an XmlResourceParser. Without the class the helper's constructor throws
 * {@code RuntimeException: Unable to load XmlBlock methods by reflection}
 * (caused by ClassNotFoundException) on the AppInit1 thread, which is uncaught,
 * so the app's AOSP-style KillApplicationHandler ends the process with
 * System.exit(10). Generic: any app that parses compiled XML it holds in memory
 * (Meta's Bloks/Litho layouts are the measured case) reaches for this.
 *
 * THIS IS A REAL IMPLEMENTATION, NOT A STUB. Mac.apk already parses compiled
 * binary XML -- {@link BinaryXmlParser#parse(byte[])} produces a DOM and
 * {@link DomXmlResourceParser} presents a DOM as an
 * {@link android.content.res.XmlResourceParser} -- so newParser() hands back a
 * parser that really walks the caller's document. Returning a do-nothing parser
 * would just move the failure one layer up (see CLAUDE.md, the no-shortcuts rule).
 *
 * Deliberately mirrors AOSP's CLOSED behaviour: once {@link #close()} has run,
 * newParser() returns null rather than throwing, because that is what the real
 * class does when its native block is gone and callers are written against it.
 */
public final class XmlBlock implements AutoCloseable {

    /** The compiled-XML bytes this block owns (already sliced to offset/size). */
    private final byte[] mData;
    /** Parsed lazily and cached: a caller may take several parsers off one block. */
    private Document mDoc;
    private boolean mParsed;
    private boolean mOpen = true;

    public XmlBlock(byte[] data) {
        this(data, 0, data != null ? data.length : 0);
    }

    public XmlBlock(byte[] data, int offset, int size) {
        if (data == null || offset < 0 || size < 0 || offset + size > data.length) {
            mData = new byte[0];
            return;
        }
        if (offset == 0 && size == data.length) {
            mData = data;
        } else {
            byte[] slice = new byte[size];
            System.arraycopy(data, offset, slice, 0, size);
            mData = slice;
        }
    }

    /** AOSP: synchronized, idempotent, and never throws. */
    @Override
    public void close() {
        synchronized (this) {
            if (mOpen) {
                mOpen = false;
                mDoc = null;
            }
        }
    }

    public XmlResourceParser newParser() {
        return newParser(0);
    }

    /**
     * AOSP takes the block's resource id purely so the parser can report it from
     * getSourceResId(); the parse itself does not depend on it. We accept and
     * ignore it for signature parity.
     */
    public XmlResourceParser newParser(int resId) {
        synchronized (this) {
            // AOSP returns null once the native block is gone. Callers are
            // written against that, so a closed block must NOT throw.
            if (!mOpen) return null;
            Document doc = document();
            if (doc == null) return null;
            try {
                return new DomXmlResourceParser(doc);
            } catch (Throwable t) {
                NativeLog.w("XmlBlock", "newParser: could not present the parsed"
                        + " document as an XmlResourceParser (" + t + ")");
                return null;
            }
        }
    }

    /** Parse once, cache. Null when the bytes are not XML we can read. */
    private Document document() {
        if (mParsed) return mDoc;
        mParsed = true;
        if (mData == null || mData.length == 0) return null;
        try {
            if (BinaryXmlParser.isBinaryXml(mData)) {
                mDoc = BinaryXmlParser.parse(mData);
            } else {
                // AOSP's XmlBlock only ever holds COMPILED xml, so this branch is
                // not a device behaviour we are mirroring -- it is leniency that
                // costs nothing: a caller that hands us plain text still gets a
                // working parser instead of a null it did not expect. Logged so a
                // genuinely wrong byte[] is visible rather than silently empty.
                NativeLog.w("XmlBlock", "block is not compiled binary XML ("
                        + mData.length + " bytes); parsing it as text");
                try (java.io.InputStream in = new java.io.ByteArrayInputStream(mData)) {
                    javax.xml.parsers.DocumentBuilderFactory f =
                            javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    f.setNamespaceAware(true);
                    mDoc = f.newDocumentBuilder().parse(in);
                }
            }
        } catch (Throwable t) {
            NativeLog.w("XmlBlock", "could not parse a " + mData.length
                    + "-byte XML block: " + t);
            mDoc = null;
        }
        return mDoc;
    }
}
