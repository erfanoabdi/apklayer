package hello.system;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

/**
 * This app's input method (bound to another app's field by tests/vm/ime-bind.py), enabled by the user (`apklayer ime enable hello.system`, tests/vm/system-apps.sh): installed, enabled, and the default
 * as the only one. ENABLED_INPUT_METHODS itself is not readable for this targetSdk (34): the enabled list comes from InputMethodManager, as on a device.
 */
final class ImeCheck implements Check {
    @Override public String run(Context context) throws Exception {
        String id = new ComponentName(context, Ime.class).flattenToShortString();
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        String chosen = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        String seen = "installed " + imm.getInputMethodList().stream().map(InputMethodInfo::getId).toList() + ", enabled "
                + imm.getEnabledInputMethodList().stream().map(InputMethodInfo::getId).toList() + ", default " + chosen + " (location_mode " + Settings.Secure.getString(context.getContentResolver(), "location_mode") + ")";
        Check.require(imm.getInputMethodList().stream().map(InputMethodInfo::getId).anyMatch(id::equals), id + " not installed: " + seen);
        Check.require(imm.getEnabledInputMethodList().stream().map(InputMethodInfo::getId).anyMatch(id::equals), id + " not enabled: " + seen);
        Check.require(id.equals(chosen), seen);
        return seen;
    }
}
