package hello.system;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** A desktop has no vibrator: the framework's own answer, and vibrate() is a quiet no-op (games and keyboards call it unconditionally). */
final class VibratorCheck implements Check {
    @Override public String run(Context context) throws Exception {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        VibratorManager manager = context.getSystemService(VibratorManager.class);
        Check.require(!vibrator.hasVibrator() && manager.getVibratorIds().length == 0, "hasVibrator " + vibrator.hasVibrator() + ", vibrators " + manager.getVibratorIds().length);
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        vibrator.cancel();
        return "no vibrator (" + vibrator.getClass().getSimpleName() + "), vibrate is a no-op";
    }
}
