// android.animation.AnimatorListenerAdapter -- the top-level adapter apps
// subclass anonymously (`new AnimatorListenerAdapter(){ onAnimationEnd... }`)
// for transition-completion callbacks (FAB show/hide, Snackbar dismiss,
// RecyclerView ItemAnimator.dispatchAnimationsFinished, BottomSheet settle).
// Was an EMPTY stub that did NOT implement Animator.AnimatorListener, so our
// Animator.addListener() never recognized these objects as listeners and
// onAnimationEnd never fired -> transition callbacks silently dropped. Now it
// reuses the correct nested Animator.AnimatorListenerAdapter (which implements
// AnimatorListener + AnimatorPauseListener with empty defaults). v1.0.422.
package android.animation;

public class AnimatorListenerAdapter extends Animator.AnimatorListenerAdapter {
    public AnimatorListenerAdapter() {}
}
