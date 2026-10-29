package hello.system;

import android.content.Context;

/** One scenario of the system-service check: uses a service or provider the way an app does and returns what it saw, or throws. */
interface Check {
    String run(Context context) throws Exception;

    static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
