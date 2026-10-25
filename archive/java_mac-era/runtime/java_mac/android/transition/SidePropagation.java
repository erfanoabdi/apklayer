// android.transition.SidePropagation -- start-delay propagation based on a
// view's distance to a chosen SIDE of the scene (and, orthogonally, to the
// epicenter). This is the propagation Slide uses by default on a device. Was
// absent entirely.
// Mirrors frameworks/base/core/java/android/transition/SidePropagation.java
// (android16-release).
package android.transition;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class SidePropagation extends VisibilityPropagation {

    private float mPropagationSpeed = 3.0f;
    private int mSide = Gravity.BOTTOM;

    /**
     * The side the propagation measures from. One of Gravity.LEFT / TOP / RIGHT
     * / BOTTOM / START / END; the default is Gravity.BOTTOM.
     */
    public void setSide(int side) {
        mSide = side;
    }

    /**
     * Speed of propagation relative to the transition's duration. Zero is
     * illegal; a negative value inverts the propagation.
     */
    public void setPropagationSpeed(float propagationSpeed) {
        if (propagationSpeed == 0) {
            throw new IllegalArgumentException("propagationSpeed may not be 0");
        }
        mPropagationSpeed = propagationSpeed;
    }

    @Override
    public long getStartDelay(ViewGroup sceneRoot, Transition transition,
            TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null && endValues == null) {
            return 0;
        }
        int directionMultiplier = 1;
        Rect epicenter = transition.getEpicenter();
        TransitionValues positionValues;
        if (endValues == null || getViewVisibility(startValues) == View.VISIBLE) {
            positionValues = startValues;
            directionMultiplier = -1;
        } else {
            positionValues = endValues;
        }

        int viewCenterX = getViewX(positionValues);
        int viewCenterY = getViewY(positionValues);

        int[] loc = new int[2];
        sceneRoot.getLocationOnScreen(loc);
        int left = loc[0] + Math.round(sceneRoot.getTranslationX());
        int top = loc[1] + Math.round(sceneRoot.getTranslationY());
        int right = left + sceneRoot.getWidth();
        int bottom = top + sceneRoot.getHeight();

        int epicenterX;
        int epicenterY;
        if (epicenter != null) {
            epicenterX = epicenter.centerX();
            epicenterY = epicenter.centerY();
        } else {
            epicenterX = (left + right) / 2;
            epicenterY = (top + bottom) / 2;
        }

        float distance = distance(sceneRoot, viewCenterX, viewCenterY, epicenterX, epicenterY,
                left, top, right, bottom);
        float maxDistance = getMaxDistance(sceneRoot);
        float distanceFraction = distance / maxDistance;

        long duration = transition.getDuration();
        if (duration < 0) {
            duration = 300;
        }

        return Math.round(duration * directionMultiplier / mPropagationSpeed * distanceFraction);
    }

    private int distance(View sceneRoot, int viewX, int viewY, int epicenterX, int epicenterY,
            int left, int top, int right, int bottom) {
        final int side;
        if (mSide == Gravity.START) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            side = isRtl ? Gravity.RIGHT : Gravity.LEFT;
        } else if (mSide == Gravity.END) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            side = isRtl ? Gravity.LEFT : Gravity.RIGHT;
        } else {
            side = mSide;
        }
        int distance = 0;
        switch (side) {
            case Gravity.LEFT:
                distance = right - viewX + Math.abs(epicenterY - viewY);
                break;
            case Gravity.TOP:
                distance = bottom - viewY + Math.abs(epicenterX - viewX);
                break;
            case Gravity.RIGHT:
                distance = viewX - left + Math.abs(epicenterY - viewY);
                break;
            case Gravity.BOTTOM:
                distance = viewY - top + Math.abs(epicenterX - viewX);
                break;
            default:
                break;
        }
        return distance;
    }

    private int getMaxDistance(ViewGroup sceneRoot) {
        switch (mSide) {
            case Gravity.LEFT:
            case Gravity.RIGHT:
            case Gravity.START:
            case Gravity.END:
                return sceneRoot.getWidth();
            default:
                return sceneRoot.getHeight();
        }
    }
}
