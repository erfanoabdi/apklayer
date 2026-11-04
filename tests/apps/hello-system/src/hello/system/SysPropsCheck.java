package hello.system;

import android.content.Context;
import java.lang.reflect.Method;

/** android.os.SystemProperties as apps read it by reflection (device info apps, Treble checkers): the instance's property store, every key the natives see. */
final class SysPropsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
        Method set = Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class);
        String treble = (String) get.invoke(null, "ro.treble.enabled"), egl = (String) get.invoke(null, "ro.hardware.egl"), zygote = (String) get.invoke(null, "ro.zygote");
        Check.require("true".equals(treble) && !egl.isEmpty() && !zygote.isEmpty(), "ro.treble.enabled '" + treble + "' ro.hardware.egl '" + egl + "' ro.zygote '" + zygote + "'");
        set.invoke(null, "debug.hello.sysprop", "written");  // a writable key goes to the store and reads back
        Check.require("written".equals(get.invoke(null, "debug.hello.sysprop")), "debug.hello.sysprop did not read back");
        return "ro.treble.enabled=" + treble + " ro.hardware.egl=" + egl + " ro.zygote=" + zygote + ", debug key written and read back";
    }
}
