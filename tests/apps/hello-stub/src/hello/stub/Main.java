package hello.stub;

import android.app.Activity;
import android.os.Bundle;

/** Calls into Victim: with Victim stubbed (a failed conversion) the call throws VerifyError naming it, the rest of the app runs. */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            System.out.println("[stub] call returned " + Victim.hello());
        } catch (VerifyError e) {
            System.out.println("[stub] VerifyError " + e.getMessage());
        } catch (Throwable t) {
            System.out.println("[stub] FAIL " + t);
        }
        // in-memory DEX (as AGDK's Swappy loads its helper): converted in the app JVM, which then runs unverified, so it goes through the
        // verifier first (ShadowHostBaseDexClassLoader); its own Victim, with the boot loader as parent, not the APK's copy
        try (java.util.zip.ZipFile apk = new java.util.zip.ZipFile(getApplicationInfo().sourceDir)) {
            byte[] dex = apk.getInputStream(apk.getEntry("classes.dex")).readAllBytes();
            ClassLoader memory = new dalvik.system.InMemoryDexClassLoader(java.nio.ByteBuffer.wrap(dex), null);
            Class<?> victim = memory.loadClass("hello.stub.Victim");
            java.lang.reflect.Method hello = victim.getDeclaredMethod("hello");
            hello.setAccessible(true);
            System.out.println("[stub] in-memory returned " + hello.invoke(null) + (victim.getClassLoader() == memory ? "" : " (not the in-memory loader's)"));
        } catch (Throwable t) {
            System.out.println("[stub] in-memory FAIL " + t);
        }
        finish();
    }
}
