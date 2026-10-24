package android.app;
import android.content.Context;
import android.view.Display;
/** A Dialog shown on a secondary Display. Mac.apk has one display, so it behaves
 *  as a normal Dialog bound to the default display. */
public class Presentation extends Dialog {
    private final Display mDisplay;
    public Presentation(Context outerContext, Display display) { super(outerContext); mDisplay = display; }
    public Presentation(Context outerContext, Display display, int theme) { super(outerContext); mDisplay = display; }
    public Display getDisplay() { return mDisplay; }
    public Context getPresentationContext() { return getContext(); }
    public void onDisplayChanged() {}
    public void onDisplayRemoved() {}

    /** AOSP Presentation.getResources(): "Gets the Resources object that should
     *  be used to inflate layouts for this presentation" -- AOSP returns the
     *  resources of the presentation's OWN display-adjusted context, not the
     *  outer activity's, because a secondary display can have a different
     *  density and size. Mac.apk has exactly one display (getDisplay() above
     *  always hands back the default one), so the presentation context's
     *  resources ARE the outer context's and there is no adjusted
     *  configuration to build. */
    public android.content.res.Resources getResources() {
        Context c = getPresentationContext();
        return c != null ? c.getResources() : null;
    }
}
