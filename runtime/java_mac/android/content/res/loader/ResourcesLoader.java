package android.content.res.loader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** A set of ResourcesProviders addable to a Resources object at runtime (API 30).
 *  Mac.apk holds the providers but resolves resources from the base apk, so an
 *  added loader contributes no overrides.
 *  Source: frameworks/base/core/java/android/content/res/loader/ResourcesLoader.java */
// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class ResourcesLoader {
    private final List<ResourcesProvider> mProviders = new ArrayList<>();
    public ResourcesLoader() { }
    public List<ResourcesProvider> getProviders() {
        return Collections.unmodifiableList(mProviders);
    }
    public void addProvider(ResourcesProvider resourcesProvider) {
        if (resourcesProvider != null) mProviders.add(resourcesProvider);
    }
    public void removeProvider(ResourcesProvider resourcesProvider) {
        mProviders.remove(resourcesProvider);
    }
    public void setProviders(List<ResourcesProvider> resourcesProviders) {
        mProviders.clear();
        if (resourcesProviders != null) mProviders.addAll(resourcesProviders);
    }
    public void clearProviders() { mProviders.clear(); }
}
