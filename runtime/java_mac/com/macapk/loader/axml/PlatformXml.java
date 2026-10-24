// com.macapk.loader.axml.PlatformXml -- open a FRAMEWORK XML file (a 0x01......
// id: android.R.layout.simple_list_item_1, com.android.internal.R.layout.
// preference_material, ...) as an XmlResourceParser, the way AOSP's
// Resources.getLayout does on a device.
//
// AOSP's path (frameworks/base, aosp main): Resources.getLayout(id) ->
// loadXmlResourceParser(id, "layout") -> ResourcesImpl.loadXmlResourceParser:
//     impl.getValue(id, value, true);                 // resolve refs to the file
//     if (value.type == TypedValue.TYPE_STRING)
//         return loadXmlResourceParser(value.string.toString(), id, value.assetCookie, type);
// which finds (or opens and caches) the XmlBlock for that path and returns
// block.newParser(id). Here: ResourceIndex.macapkFwValueOf(id) is the resolved
// value (reference chain chased, configuration-selected entry), its string is the
// path inside framework-res.apk, ResourceIndex.openRaw(id) serves that entry's
// bytes, and the parsed AxmlBlock is cached by path for the life of the process
// -- framework-res is immutable, and a configuration change that selects a
// different variant (layout-watch, layout-land) selects a different PATH, so it
// can never be served a stale block.
//
// Why this exists: the platform layouts used to be unreadable. LayoutInflater
// handed their compiled bytes to a text XML parser ("Content is not allowed in
// prolog") and returned an empty FrameLayout, so android.R.layout.
// simple_spinner_item / simple_list_item_1 / select_dialog_* rows were blank
// (bobball's spinners: 1,662 stack traces in one run), and Preference, TabHost
// and ArrayAdapter each grew a hand-built stand-in. See
// docs/platform-layouts-axml_2026-09-26.md.
package com.macapk.loader.axml;

import android.content.res.XmlResourceParser;
import com.macapk.loader.BinaryXmlParser;
import com.macapk.loader.NativeLog;
import com.macapk.loader.ResourceIndex;

import java.util.concurrent.ConcurrentHashMap;

public final class PlatformXml {
    private static final String TAG = "Mac.apk.AXML";
    private PlatformXml() {}

    /** In-apk path -> parsed block. Never evicted: the set is bounded by the
     *  framework files a process actually opens (a few dozen), and each block
     *  is a few KB of int arrays. */
    private static final ConcurrentHashMap<String, AxmlBlock> BLOCKS = new ConcurrentHashMap<>();
    /** Paths that are NOT compiled XML (or failed to parse), so a caller that
     *  keeps asking pays the zip read once. */
    private static final java.util.Set<String> MISSES = ConcurrentHashMap.newKeySet();

    /** A fresh parser over framework XML file `id`, or null when `id` is not a
     *  framework id, has no file, or the file is not compiled XML -- the caller
     *  then keeps its previous path. Never throws. */
    public static XmlResourceParser open(int id) {
        return open(id, false);
    }

    /** open(id), but only when the file `id` resolves to is a LAYOUT
     *  (res/layout/ or a res/layout-<qualifiers>/ variant); null otherwise.
     *  Resources.getXml uses it: AOSP answers getXml and getLayout from the
     *  same XmlBlock, but our getXml consumers of the other framework XML kinds
     *  (res/xml preference screens, res/color selectors, res/menu, drawables)
     *  were verified on the DOM parser and stay there. */
    public static XmlResourceParser openLayout(int id) {
        return open(id, true);
    }

    private static XmlResourceParser open(int id, boolean layoutOnly) {
        if ((id >>> 24) != 0x01) return null;
        try {
            Object o = android.content.res.Resources.macapkGetResourceIndex();
            if (!(o instanceof ResourceIndex ri)) return null;
            String path = pathOf(ri, id);
            if (path == null || MISSES.contains(path)) return null;
            if (layoutOnly && !path.startsWith("res/layout/") && !path.startsWith("res/layout-")) return null;
            AxmlBlock b = BLOCKS.get(path);
            if (b == null) {
                byte[] bytes;
                try (java.io.InputStream in = ri.openRaw(id)) {
                    if (in == null) { MISSES.add(path); return null; }
                    bytes = in.readAllBytes();
                }
                if (!AxmlBlock.isCompiled(bytes)) { MISSES.add(path); return null; }
                try {
                    b = AxmlBlock.parse(bytes);
                } catch (IllegalArgumentException bad) {
                    MISSES.add(path);
                    NativeLog.w(TAG, "framework xml " + path + " (0x" + Integer.toHexString(id)
                        + ") is not well-formed compiled XML: " + bad.getMessage());
                    return null;
                }
                AxmlBlock prev = BLOCKS.putIfAbsent(path, b);
                if (prev != null) b = prev;
            }
            return new AxmlParser(b, id, namesOf(ri));
        } catch (Throwable t) {
            NativeLog.w(TAG, "framework xml 0x" + Integer.toHexString(id) + " unavailable: " + t);
            return null;
        }
    }

    /** The in-apk path of framework file `id` ("res/layout/simple_list_item_1.xml"),
     *  or null when the resolved value is not a file. */
    public static String pathOf(ResourceIndex ri, int id) {
        Object[] fv = ri.macapkFwValueOf(id);
        if (fv == null || fv.length < 3 || !(fv[0] instanceof Integer t)
                || t != 0x03 /* TYPE_STRING */ || !(fv[2] instanceof CharSequence cs)) return null;
        String path = cs.toString();
        return (path.startsWith("res/") && path.endsWith(".xml")) ? path : null;
    }

    // ---- names -----------------------------------------------------------
    //
    // The spelling of a typed value must be the one ResourceIndex already uses
    // for every APP layout (BinaryXmlParser.parse(bytes, macapkNames())), or the
    // two kinds of layout would read differently to the same consumers. So this
    // takes THAT Names object rather than building a second one from the same
    // tables: ResourceIndex.macapkNames(), which is private, reached by
    // reflection (both classes live in the unnamed module of one jar, so
    // setAccessible is permitted). A public accessor is preferred when one
    // exists (see the platlayouts patch in docs/platform-layouts-axml_2026-09-26.md).
    // Without it the raw spelling is used, which is what the pre-change
    // framework getXml path produced.
    /** {ResourceIndex owner, Names} published as ONE reference, so a reader
     *  can never pair one index's names with another index. */
    private static volatile Object[] namesCache;

    static BinaryXmlParser.Names namesOf(ResourceIndex ri) {
        Object[] c = namesCache;
        if (c != null && c[0] == ri) return (BinaryXmlParser.Names) c[1];
        BinaryXmlParser.Names n = null;
        try {
            java.lang.reflect.Method m;
            try {
                m = ResourceIndex.class.getMethod("macapkNames");
            } catch (NoSuchMethodException notPublic) {
                m = ResourceIndex.class.getDeclaredMethod("macapkNames");
                m.setAccessible(true);
            }
            Object v = m.invoke(ri);
            if (v instanceof BinaryXmlParser.Names bn) n = bn;
        } catch (Throwable t) {
            NativeLog.w(TAG, "ResourceIndex names unavailable (" + t
                + "); framework XML values keep their raw spelling");
        }
        namesCache = new Object[] { ri, n };
        return n;
    }
}
