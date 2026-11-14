package hello.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** A fragment committed in onCreate (androidx's FragmentActivity does the same through its FragmentController): its view must exist at onViewCreated,
 * the activity's onStart and onResume (Droid-ify's TabsFragment.toolbar), also in the activity that recreate() makes. */
public class Main extends Activity {
    static boolean recreated;
    final StringBuilder fails = new StringBuilder();

    private void check(String what, boolean ok) { System.out.println("[fragment] " + (ok ? "ok " : "FAIL ") + what); if (!ok) fails.append(what).append("; "); }

    public static class Screen extends Fragment {
        TextView text;
        @Override public View onCreateView(LayoutInflater i, ViewGroup c, Bundle s) { text = new TextView(getActivity()); text.setText("fragment"); return text; }
        @Override public void onViewCreated(View v, Bundle s) { ((Main) getActivity()).check("onViewCreated view", getView() == v && text != null); }
        @Override public void onStart() { super.onStart(); ((Main) getActivity()).check("fragment onStart view", getView() != null); }
        @Override public void onResume() { super.onResume(); ((Main) getActivity()).check("fragment onResume view", getView() != null); }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setId(1001);
        setContentView(root);
        if (state == null) getFragmentManager().beginTransaction().add(1001, new Screen(), "s").commit();
    }
    @Override protected void onStart() {
        super.onStart();
        Fragment f = getFragmentManager().findFragmentByTag("s");
        check("onStart: fragment found with a view", f != null && f.getView() != null);
    }
    @Override protected void onPostResume() {  // the framework resumes the activity's fragments here, after onResume
        super.onPostResume();
        Fragment f = getFragmentManager().findFragmentByTag("s");
        check("onPostResume: fragment resumed", f != null && f.isResumed() && f.getView() != null);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!recreated) { recreated = true; recreate(); return; }
        new android.os.Handler().postDelayed(() -> {
            System.out.println("[fragment] " + (fails.length() == 0 ? "PASS" : "FAIL " + fails) + " (second instance)");
            System.out.println("[fragment] END");
        }, 1500);
    }
}
