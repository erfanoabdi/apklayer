// android.view.animation.BaseInterpolator -- the abstract base AOSP's concrete
// interpolators extend. It carries the inflated "change interval" bookkeeping used
// by AnimationUtils when loading an interpolator from XML. Was absent. Our
// concrete interpolators implement the Interpolator interface directly (also
// valid), but apps occasionally reference BaseInterpolator in a signature or do an
// instanceof check, so the type must exist on the bootclasspath. Mirrors
// frameworks/base/core/java/android/view/animation/BaseInterpolator.java.
package android.view.animation;

public abstract class BaseInterpolator implements Interpolator {
    private int mChangingConfiguration;

    int getChangingConfiguration() { return mChangingConfiguration; }
    void setChangingConfiguration(int changingConfiguration) {
        mChangingConfiguration = changingConfiguration;
    }
}
