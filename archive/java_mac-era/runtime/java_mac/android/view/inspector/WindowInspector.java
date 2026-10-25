package android.view.inspector;
import java.util.Collections;
import java.util.List;
/** Test/inspection hook listing the process's attached window root views. Mac.apk
 *  drives its own windowing, so the list is empty. */
public final class WindowInspector {
    private WindowInspector() {}
    public static List<android.view.View> getGlobalWindowViews() { return Collections.emptyList(); }

    /** AOSP WindowInspector.addGlobalWindowViewsListener(Executor,
     *  Consumer&lt;List&lt;View&gt;&gt;) (API 36): told whenever the set of
     *  attached window roots changes, and (per its javadoc) called IMMEDIATELY
     *  with the current set. Mac.apk drives its own windowing and
     *  getGlobalWindowViews() above reports an empty list, so the immediate
     *  callback carries that empty list and nothing further is ever delivered.
     *  Delivering the initial callback is the part a caller depends on to
     *  initialise itself, so it is honoured rather than skipped. */
    public static void addGlobalWindowViewsListener(java.util.concurrent.Executor executor,
            java.util.function.Consumer<List<android.view.View>> listener) {
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(listener);
        executor.execute(() -> listener.accept(getGlobalWindowViews()));
    }

    /** AOSP WindowInspector.removeGlobalWindowViewsListener(Consumer) (API 36).
     *  Nothing is retained (the set never changes), so removal is a no-op. */
    public static void removeGlobalWindowViewsListener(
            java.util.function.Consumer<List<android.view.View>> listener) {
    }
}
