package apklayer.host;

import android.os.IInterface;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The app's half of the input method binding (InputMethodManager.startInputInner): when a text field takes focus, its InputConnection wrapped in the
 * framework's RemoteInputConnectionImpl (what the IME's commitText and friends reach, run on the main looper) and its EditorInfo go to the hub
 * (HubInputMethods), which starts input on the user's Android IME; when no field has focus, the hub unbinds it.
 */
final class HostInputMethodClient {
    private HostInputMethodClient() {}

    private static View served;

    /** The focused text field, or null (AppHost's loop asks every turn: only a change reaches the hub). */
    static void follow(View editor) {
        if (editor == served || !HubClient.connected()) return;
        served = editor;
        if (editor == null) { HubClient.current().finishInput(); return; }
        EditorInfo info = new EditorInfo();
        info.packageName = editor.getContext().getPackageName();
        info.fieldId = editor.getId();
        InputConnection connection = editor.onCreateInputConnection(info);
        if (connection == null) { HubClient.current().finishInput(); return; }
        try {
            Object remote = ReflectionHelpers.callConstructor(Class.forName("android.view.inputmethod.RemoteInputConnectionImpl"),
                    ClassParameter.from(Looper.class, Looper.getMainLooper()), ClassParameter.from(InputConnection.class, connection),
                    ClassParameter.from(InputMethodManager.class, editor.getContext().getSystemService(InputMethodManager.class)), ClassParameter.from(View.class, editor));
            HubClient.current().startInput(((IInterface) remote).asBinder(), info);
        } catch (ClassNotFoundException | RuntimeException failure) { System.out.println("[apklayer] cannot start input: " + failure); }
    }
}
