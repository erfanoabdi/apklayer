package apklayer.host;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One line per process for the first call that lands on a fake (a null/deep proxy, a missing service, a stub): "[fake] <service> <what>". Never throws. */
final class FakeLog {
    private static final Set<String> seen = ConcurrentHashMap.newKeySet();

    private FakeLog() {}

    static void hit(String service, String what) {
        String line = "[fake] " + service + " " + what;
        if (seen.add(line)) System.out.println(line);
    }
}
