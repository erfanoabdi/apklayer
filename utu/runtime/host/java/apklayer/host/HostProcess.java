package apklayer.host;

import java.util.ArrayList;
import java.util.List;

/** Helper processes of an app JVM end with it, also when the JVM is killed (a shutdown hook does not run then): `setpriv --pdeathsig` where the host has it. */
final class HostProcess {
    private HostProcess() {}

    private static final boolean SETPRIV = new java.io.File("/usr/bin/setpriv").canExecute();

    static List<String> dying(String... command) {
        List<String> out = new ArrayList<>();
        if (SETPRIV) { out.add("setpriv"); out.add("--pdeathsig"); out.add("TERM"); }
        out.addAll(List.of(command));
        return out;
    }
}
