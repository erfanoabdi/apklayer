// android.transition.VisibilityPropagation -- the base for the two propagations
// that stagger a Visibility transition's per-view start delays by where the view
// SITS on screen (CircularPropagation for Explode, SidePropagation for Slide).
// Was absent entirely, so an app calling
// explode.setPropagation(new CircularPropagation()) hit NoClassDefFoundError.
// Mirrors frameworks/base/core/java/android/transition/VisibilityPropagation.java
// (android16-release) -- it captures the view's visibility and its CENTRE in
// screen coordinates, and exposes them to subclasses.
package android.transition;

import android.view.View;

public abstract class VisibilityPropagation extends TransitionPropagation {

    private static final String PROPNAME_VISIBILITY = "android:visibilityPropagation:visibility";

    // int[2]: index 0 is x, index 1 is y, both in screen coordinates.
    private static final String PROPNAME_VIEW_CENTER = "android:visibilityPropagation:center";

    private static final String[] VISIBILITY_PROPAGATION_VALUES = {
            PROPNAME_VISIBILITY,
            PROPNAME_VIEW_CENTER,
    };

    @Override
    public void captureValues(TransitionValues values) {
        View view = values.view;
        if (view == null) return;
        // Prefer the visibility a Visibility transition already captured -- AOSP
        // reads Visibility.PROPNAME_VISIBILITY here for exactly that reason, so
        // the propagation sees the SCENE's visibility, not the live one.
        Integer visibility = (Integer) values.values.get(Visibility.PROPNAME_VISIBILITY);
        if (visibility == null) {
            visibility = Integer.valueOf(view.getVisibility());
        }
        values.values.put(PROPNAME_VISIBILITY, visibility);
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        loc[0] += Math.round(view.getTranslationX());
        loc[0] += view.getWidth() / 2;
        loc[1] += Math.round(view.getTranslationY());
        loc[1] += view.getHeight() / 2;
        values.values.put(PROPNAME_VIEW_CENTER, loc);
    }

    @Override
    public String[] getPropagationProperties() {
        return VISIBILITY_PROPAGATION_VALUES;
    }

    /** The view's visibility at the moment the values were captured. */
    public int getViewVisibility(TransitionValues values) {
        if (values == null) {
            return View.GONE;
        }
        Integer visibility = (Integer) values.values.get(PROPNAME_VISIBILITY);
        if (visibility == null) {
            return View.GONE;
        }
        return visibility.intValue();
    }

    /** The view's centre x, in screen coordinates, when the values were captured. */
    public int getViewX(TransitionValues values) {
        return getViewCoordinate(values, 0);
    }

    /** The view's centre y, in screen coordinates, when the values were captured. */
    public int getViewY(TransitionValues values) {
        return getViewCoordinate(values, 1);
    }

    private static int getViewCoordinate(TransitionValues values, int coordinateIndex) {
        if (values == null) {
            return -1;
        }
        int[] coordinates = (int[]) values.values.get(PROPNAME_VIEW_CENTER);
        if (coordinates == null) {
            return -1;
        }
        return coordinates[coordinateIndex];
    }
}
