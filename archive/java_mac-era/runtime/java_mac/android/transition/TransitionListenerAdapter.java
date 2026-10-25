// android.transition.TransitionListenerAdapter -- empty-default adapter so apps
// can override only the TransitionListener callbacks they care about (typically
// onTransitionEnd to clean up after a transition). Was absent; commonly used
// anonymously. Mirrors
// frameworks/base/core/java/android/transition/TransitionListenerAdapter.java.
package android.transition;

public abstract class TransitionListenerAdapter implements Transition.TransitionListener {
    @Override public void onTransitionStart(Transition transition) {}
    @Override public void onTransitionEnd(Transition transition) {}
    @Override public void onTransitionCancel(Transition transition) {}
    @Override public void onTransitionPause(Transition transition) {}
    @Override public void onTransitionResume(Transition transition) {}
}
