package android.app.slice;
/** The Slice system service. Mac.apk pins/binds no slices, so it is inert.
 *
 * AOSP's SliceManager (core/java/android/app/slice/SliceManager.java,
 * android16-release) is a CONCRETE class -- apps can be handed a live instance
 * via Context.getSystemService(SliceManager.class) and cast/compare it as a
 * SliceManager without our shape being wrong. We had marked it abstract with
 * no bodies, which is fine for our own SystemServiceRegistry wiring (nothing
 * ever calls `new SliceManager()`) but is still the WRONG shape per
 * framework_lint's abstract/final check. Fixed to concrete with honest no-op
 * bodies: no permission state to grant/revoke/query when no slice is ever
 * pinned or bound. */
public class SliceManager {
    public void grantSlicePermission(String toPackage, android.net.Uri uri) {
    }
    public void revokeSlicePermission(String toPackage, android.net.Uri uri) {
    }
    public java.util.List<android.net.Uri> getPinnedSlices() {
        return java.util.Collections.emptyList();
    }

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/slice/SliceManager.java
    // (android16-release). Every method below reaches a SliceProvider through
    // the ContentResolver. Mac.apk installs exactly one app and no slice host,
    // and nothing in the runtime ever pins or binds a slice -- so each answer
    // is AOSP's own "no provider answered" result, which is what a caller
    // already branches on.
    // ================================================================

    /** AOSP CATEGORY_SLICE / SLICE_METADATA_KEY: the intent category and the
     *  <meta-data> key an activity uses to advertise its slice. */
    public static final String CATEGORY_SLICE = "android.app.slice.category.SLICE";
    public static final String SLICE_METADATA_KEY = "android.metadata.SLICE_URI";

    /** AOSP pinSlice(Uri, Set<SliceSpec>): "Ensures that slice updates are
     *  pushed to the app" -- the caller must be a slice HOST (it needs
     *  BIND_SLICE), which nothing here is. Nothing is pinned, which is why
     *  getPinnedSlices() above stays empty and getPinnedSpecs() below answers
     *  for an unpinned uri. AOSP requires a non-null uri and spec set. */
    public void pinSlice(android.net.Uri uri, java.util.Set<SliceSpec> specs) {
        if (uri == null) throw new NullPointerException("uri cannot be null");
        if (specs == null) throw new NullPointerException("specs cannot be null");
    }

    /** AOSP unpinSlice(Uri): the inverse; nothing was pinned. */
    public void unpinSlice(android.net.Uri uri) {
        if (uri == null) throw new NullPointerException("uri cannot be null");
    }

    /** AOSP getPinnedSpecs(Uri): "@return the specs of the pinned slice", which
     *  is empty for a uri nobody pinned. Never null. */
    public java.util.Set<SliceSpec> getPinnedSpecs(android.net.Uri uri) {
        return new java.util.HashSet<SliceSpec>();
    }

    /** AOSP bindSlice(Uri, Set<SliceSpec>): "@return The Slice provided by the
     *  app or null if none is given" -- null is AOSP's answer whenever the uri
     *  resolves to no SliceProvider, which is every uri here. */
    public Slice bindSlice(android.net.Uri uri, java.util.Set<SliceSpec> supportedSpecs) {
        if (uri == null) throw new NullPointerException("uri cannot be null");
        return null;
    }

    /** AOSP bindSlice(Intent, Set<SliceSpec>): the intent-resolving form; AOSP
     *  maps the intent to a uri first (mapIntentToUri below) and returns null
     *  when nothing resolves. AOSP requires the intent to carry a component,
     *  package or data uri and throws IllegalArgumentException otherwise --
     *  that check is the observable half, so keep it. */
    public Slice bindSlice(android.content.Intent intent,
            java.util.Set<SliceSpec> supportedSpecs) {
        if (intent == null) throw new NullPointerException("intent cannot be null");
        if (intent.getComponent() == null && intent.getPackage() == null
                && intent.getData() == null) {
            throw new IllegalArgumentException(
                    "Slice intent must be explicit " + intent);
        }
        return null;
    }

    /** AOSP mapIntentToUri(Intent): "@return the uri of the slice associated
     *  with the intent, or null" -- null when nothing advertises a slice for
     *  it. Same argument check as bindSlice(Intent, ...). */
    public android.net.Uri mapIntentToUri(android.content.Intent intent) {
        if (intent == null) throw new NullPointerException("intent cannot be null");
        if (intent.getComponent() == null && intent.getPackage() == null
                && intent.getData() == null) {
            throw new IllegalArgumentException(
                    "Slice intent must be explicit " + intent);
        }
        return null;
    }

    /** AOSP getSliceDescendants(Uri): "@return All slices within the space" --
     *  an empty collection, never null, when no provider answers. AOSP marks it
     *  @WorkerThread because it is a synchronous provider call; ours returns
     *  immediately, which is safe on any thread. */
    public java.util.Collection<android.net.Uri> getSliceDescendants(android.net.Uri uri) {
        return new java.util.ArrayList<android.net.Uri>();
    }

    /** AOSP checkSlicePermission(Uri, int pid, int uid): "@return
     *  PackageManager.PERMISSION_GRANTED if the pid/uid may access the uri."
     *  Mac.apk runs one app in one process: the only pid/uid that can ask is
     *  the app's own, and an app always has access to its own slice -- which is
     *  the same verdict a device gives for a self-owned uri. */
    public int checkSlicePermission(android.net.Uri uri, int pid, int uid) {
        return android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
