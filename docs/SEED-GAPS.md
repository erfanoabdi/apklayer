# Seed gaps — LoadProbe on converted uibench.apk (2026-10-25)

`java -cp out apklayer.probe.LoadProbe /tmp/uibench-classes.jar` on the VM:
**classes=800 linked=567 failed=233** (link = resolution of the whole supertype graph).

Top missing roots (count = app classes blocked by them):

| blocked | class |
|---|---|
| 35 | android.app.Activity |
| 20 | android.view.ViewGroup |
| 17 | android.widget.FrameLayout |
| 16 | android.content.ComponentCallbacks |
| 13 | android.app.Fragment |
| 10 | android.widget.LinearLayout |
| 9  | android.graphics.drawable.Drawable |
| 9  | android.os.Parcelable |
| 5  | android.view.Menu / MenuItem, animation.Interpolator |
| 5  | android.widget.TextView |
| 4  | android.view.View$OnClickListener |
| 3  | android.view.View, widget.EditText/ImageView, os.IInterface |

Growth order (leaves first, then the graph pulls in upward):
1. leaves: Parcelable, ComponentCallbacks(2), IInterface, Interpolator/BaseInterpolator,
   Menu/MenuItem/SubMenu (interfaces first), ParcelableSpan… 
2. content: Context (huge, thin-patch the statics), ContextWrapper, ComponentName…
3. view: View/ViewGroup (the big ones — RenderNode/hwui gates), then widget/* layouts.
4. app: Activity/Application/ContextImpl/ActivityThread driver (Boot-style).

Each step: copy from los24 -> javac on VM -> thin-patch marked "apklayer patch" -> commit.

## Update (M2c): gaps CLOSED by framework-base switch
Adopted Mac.apk runtime/java (3049 files, compiles clean, javac 21, ARM64 VM).
LoadProbe: classes=800 linked=800 failed=0. Hand-grown seed superseded (kept for history).
