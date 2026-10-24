// android.transition.AutoTransition -- the default transition used by
// TransitionManager.beginDelayedTransition() when no explicit transition is
// given. It is a TransitionSet that fades disappearing views out, moves/resizes
// persisting views (ChangeBounds), then fades appearing views in. Was absent.
// Mirrors frameworks/base/core/java/android/transition/AutoTransition.java.
package android.transition;

import android.content.Context;
import android.util.AttributeSet;

public class AutoTransition extends TransitionSet {

    public AutoTransition() {
        init();
    }

    /**
     * AOSP AutoTransition(Context, AttributeSet) -- the XML-inflation
     * constructor. Note the ordering: the superclass reads
     * android:transitionOrdering off the attrs first, then init() forces
     * ORDERING_SEQUENTIAL, because an AutoTransition's three phases are
     * sequential by definition. AOSP does exactly this.
     */
    public AutoTransition(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrdering(ORDERING_SEQUENTIAL);
        Fade fadeOut = new Fade(Fade.OUT);
        ChangeBounds changeBounds = new ChangeBounds();
        Fade fadeIn = new Fade(Fade.IN);
        addTransition(fadeOut);
        addTransition(changeBounds);
        addTransition(fadeIn);
    }
}
