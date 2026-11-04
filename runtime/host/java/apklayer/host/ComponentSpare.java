package apklayer.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A spare component host (`apklayer component-host --spare`, started ahead by the hub: HubSpare) gets its package on stdin once the hub needs a host,
 * as a zygote child learns what it becomes: one line "<package>[ <isolated service>]". Its JVM options are the ones the CLI gives a component host of
 * that package (`apklayer component-host --args`), so the process ends up as the one a cold start makes; the name and uid follow from them.
 */
final class ComponentSpare {
    private ComponentSpare() {}

    /** Blocks until the hub names the package (exits when the hub is gone first); returns the APK and the classes jar. */
    static String[] awaitTarget() {
        StartTrace.mark("spare prepared");
        String line;
        try { line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine(); }
        catch (IOException failure) { line = null; }
        if (line == null || line.isBlank()) { System.out.println("[apklayer] spare component host: the hub is gone"); System.exit(0); }
        String[] target = line.trim().split(" ", 2);
        StartTrace.mark("spare target " + line.trim());
        ProcessBuilder args = new ProcessBuilder(System.getProperty("apklayer.cli"), "component-host", "--args", target[0]).redirectError(ProcessBuilder.Redirect.INHERIT);
        if (target.length > 1) args.environment().put("APKLAYER_ISOLATED", target[1]);
        List<String> out;
        try {
            Process cli = args.start();
            out = new String(cli.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            if (cli.waitFor() != 0 || out.size() < 2) throw new IOException("apklayer component-host --args " + target[0] + " failed");
        } catch (IOException | InterruptedException failure) {
            System.out.println("[apklayer] spare component host: " + failure);
            System.exit(1);
            return null;
        }
        for (String option : out.subList(0, out.size() - 2)) {
            int eq = option.indexOf('=');
            if (!option.startsWith("-D") || eq < 0) throw new IllegalStateException("unexpected JVM option " + option);
            System.setProperty(option.substring(2, eq), option.substring(eq + 1));
        }
        return new String[] {out.get(out.size() - 2), out.get(out.size() - 1)};
    }
}
