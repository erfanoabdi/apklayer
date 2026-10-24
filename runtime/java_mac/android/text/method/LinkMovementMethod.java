// android.text.method.LinkMovementMethod -- AOSP movement method that
// makes ClickableSpan / URLSpan links in a TextView tappable and lets
// the text scroll. AOSP:
//   public class LinkMovementMethod extends ScrollingMovementMethod
// with a process-wide singleton via getInstance().
//
// F-Droid's AppDetailsRecyclerViewAdapter$HeaderViewHolder.bindModel
// calls `descriptionView.setMovementMethod(LinkMovementMethod.getInstance())`
// while binding the app description (which carries URLSpans from
// HtmlCompat.fromHtml + LinkifyCompat). The previous bare stub had no
// getInstance(), so onBindViewHolder threw NoSuchMethodError -- and that
// throw propagated up RecyclerView.dispatchLayoutStep2 / onLayoutChildren,
// aborting the WHOLE detail tree's layout pass (blank screen). We don't
// drive link taps on the canvas, so inheriting ScrollingMovementMethod's
// no-op behavior is the whole implementation; the singleton + return type
// (MovementMethod) match AOSP so setMovementMethod type-checks.
package android.text.method;

public class LinkMovementMethod extends ScrollingMovementMethod {
    private static LinkMovementMethod sInstance;

    public LinkMovementMethod() {}

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new LinkMovementMethod();
        return sInstance;
    }
}
