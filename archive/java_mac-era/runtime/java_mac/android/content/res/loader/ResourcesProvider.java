package android.content.res.loader;
import android.os.ParcelFileDescriptor;
/** A single provider of additional resources/assets loaded at runtime (API 30).
 *  Mac.apk resolves resources from the app's own apk; a runtime-loaded table is
 *  held as an opaque provider that contributes nothing to the lookup.
 *  Source: frameworks/base/core/java/android/content/res/loader/ResourcesProvider.java */
// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class ResourcesProvider implements AutoCloseable, java.io.Closeable {
    private ResourcesProvider() { }
    public static ResourcesProvider loadFromTable(ParcelFileDescriptor fileDescriptor,
            AssetsProvider assetsProvider) { return new ResourcesProvider(); }
    public static ResourcesProvider empty(AssetsProvider assetsProvider) {
        return new ResourcesProvider();
    }

    // AOSP core/java/android/content/res/loader/ResourcesProvider.java (API 30)
    // -- the remaining factories. Every one of them builds an ApkAssets on the
    // native AssetManager2 and would contribute a real resource table to a
    // ResourcesLoader. Mac.apk resolves resources through ResourceIndex, which
    // has no runtime-loaded-table concept at all, so a provider built from any
    // of these contributes nothing to a lookup -- exactly as the pre-existing
    // loadFromTable()/empty() already do. The construction still SUCCEEDS
    // rather than throwing, because that is what an app's own try/catch and
    // its subsequent ResourcesLoader.addProvider() are written against, and a
    // provider that contributes nothing is the same end state empty() gives.
    //
    // ⚠ STATED GAP: this is the one place in this lane where the object is
    // real but inert. Wiring runtime resource loading into ResourceIndex is a
    // separate piece of work; until then an app that ships an overlay APK and
    // expects its strings to win will not see them.
    public static ResourcesProvider loadFromApk(ParcelFileDescriptor fileDescriptor)
            throws java.io.IOException {
        return loadFromApk(fileDescriptor, null);
    }

    public static ResourcesProvider loadFromApk(ParcelFileDescriptor fileDescriptor,
                                                AssetsProvider assetsProvider)
            throws java.io.IOException {
        if (fileDescriptor == null) throw new NullPointerException("fileDescriptor");
        return new ResourcesProvider();
    }

    public static ResourcesProvider loadFromDirectory(String path,
                                                      AssetsProvider assetsProvider)
            throws java.io.IOException {
        if (path == null) throw new NullPointerException("path");
        return new ResourcesProvider();
    }

    public static ResourcesProvider loadFromSplit(android.content.Context context,
                                                  String splitName)
            throws java.io.IOException {
        if (context == null) throw new NullPointerException("context");
        if (splitName == null) throw new NullPointerException("splitName");
        return new ResourcesProvider();
    }

    // NOT IMPLEMENTED: loadOverlay(android.content.om.OverlayInfo). The whole
    // android.content.om package (OverlayInfo, OverlayIdentifier,
    // OverlayManager, OverlayManagerTransaction, FabricatedOverlay) is absent
    // from this tree, so the parameter type does not exist and the method
    // cannot be declared. It belongs with that package, not here.
    @Override public void close() { }
}
