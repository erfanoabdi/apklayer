// android.transition.Scene -- a captured view-hierarchy state that a Transition
// animates to/from. Built from a layout resource or an existing view tree; entered
// via TransitionManager.go(scene). Was absent; referenced by TransitionManager's
// signatures. Mirrors frameworks/base/core/java/android/transition/Scene.java.
//
// AOSP's getSceneForLayout inflates a layout into the scene root on enter. Our
// resource/inflate path differs, so the resource-id ctor stores the layout id and
// inflates lazily on enter() when given an inflate hook; the common
// programmatic-scene path (Scene(root) + enterAction) is fully supported.
package android.transition;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public final class Scene {

    private final Context mContext;
    private final ViewGroup mSceneRoot;
    private ViewGroup mLayout;     // inflated content, when built from a layout
    private int mLayoutId = -1;
    private View mEnterView;       // explicit content view, when set
    private Runnable mEnterAction;
    private Runnable mExitAction;

    public Scene(ViewGroup sceneRoot) {
        mContext = null;
        mSceneRoot = sceneRoot;
    }

    public Scene(ViewGroup sceneRoot, View layout) {
        mContext = null;
        mSceneRoot = sceneRoot;
        mEnterView = layout;
    }

    /**
     * AOSP's original (now @Deprecated) ViewGroup-typed overload, kept because
     * code compiled against an older SDK emits a call with the
     * (ViewGroup,ViewGroup) descriptor, which does NOT resolve against the
     * (ViewGroup,View) form above -- the parameter types are part of the JVM
     * descriptor. apidiff cannot see this one: it tolerates a name+arity match.
     */
    @Deprecated
    public Scene(ViewGroup sceneRoot, ViewGroup layout) {
        mContext = null;
        mSceneRoot = sceneRoot;
        mEnterView = layout;
    }

    private Scene(ViewGroup sceneRoot, int layoutId, Context context) {
        mContext = context;
        mSceneRoot = sceneRoot;
        mLayoutId = layoutId;
    }

    public static Scene getSceneForLayout(ViewGroup sceneRoot, int layoutId, Context context) {
        return new Scene(sceneRoot, layoutId, context);
    }

    public ViewGroup getSceneRoot() { return mSceneRoot; }

    /**
     * AOSP Scene.java:204-221. The current scene is recorded on the scene ROOT
     * so a later scene change can exit the previous one; AOSP stores it as an
     * internal keyed view tag (com.android.internal.R.id.current_scene) via
     * View.setTagInternal.
     *
     * We cannot use View.setTag(int, Object) for that: our View faithfully
     * reproduces AOSP's rule that a key whose package byte is < 2 (a FRAMEWORK
     * id, which R.id.current_scene is) throws IllegalArgumentException, and
     * setTagInternal -- the private bypass AOSP uses here -- is not reachable
     * from this package. So the association lives in an identity side table
     * keyed weakly by the root, which answers getCurrentScene identically.
     *
     * ⚠ The value (a Scene) strongly references its key (its scene root), so a
     * root that has ever hosted a scene is retained for the life of the
     * process -- the classic WeakHashMap self-reference. That is bounded by the
     * number of distinct scene roots an app uses, and it is the price of not
     * being able to hang the reference off the View itself; a weak VALUE would
     * be worse, because getCurrentScene would then start returning null where a
     * device returns the scene.
     */
    private static final java.util.WeakHashMap<ViewGroup, Scene> sCurrentScenes =
            new java.util.WeakHashMap<ViewGroup, Scene>();

    static void setCurrentScene(ViewGroup sceneRoot, Scene scene) {
        if (sceneRoot == null) return;
        synchronized (sCurrentScenes) {
            if (scene == null) sCurrentScenes.remove(sceneRoot);
            else sCurrentScenes.put(sceneRoot, scene);
        }
    }

    public static Scene getCurrentScene(ViewGroup sceneRoot) {
        if (sceneRoot == null) return null;
        synchronized (sCurrentScenes) {
            return sCurrentScenes.get(sceneRoot);
        }
    }

    public void setEnterAction(Runnable action) { mEnterAction = action; }
    public void setExitAction(Runnable action) { mExitAction = action; }

    public void enter() {
        // Install this scene's content into the root, then run the enter action.
        if (mLayoutId >= 0 && mContext != null) {
            mSceneRoot.removeAllViews();
            mLayout = mSceneRoot;
            LayoutInflater.from(mContext).inflate(mLayoutId, mSceneRoot);
        } else if (mEnterView != null) {
            mSceneRoot.removeAllViews();
            mSceneRoot.addView(mEnterView);
        }
        if (mEnterAction != null) {
            try { mEnterAction.run(); } catch (Throwable ignored) {}
        }
        // AOSP Scene.java:193 -- record this as the scene root's current scene so
        // a later scene change (and exit(), below) can see it.
        setCurrentScene(mSceneRoot, this);
    }

    public void exit() {
        if (mExitAction != null) {
            try { mExitAction.run(); } catch (Throwable ignored) {}
        }
    }
}
