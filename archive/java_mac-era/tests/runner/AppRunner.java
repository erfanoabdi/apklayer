package apklayer.runner;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * M2d runner probe: load a converted app's Activity against the JVM framework base and
 * report how far instantiation gets. First real end-to-end step; failures here are the
 * work list for the bridge (natives, Context bootstrap, resources).
 *
 * Usage: java -cp out_mac:runnerout apklayer.runner.AppRunner <converted.jar> <ActivityClass>
 */
public final class AppRunner {
    public static void main(String[] args) throws Exception {
        String jni = System.getProperty("apklayer.jni");
        if (jni != null) {
            System.load(jni);
            System.out.println("[apklayer] JNI bridge loaded: " + jni);
        }
        String jar = args[0];
        String activity = args[1];
        URLClassLoader cl = new URLClassLoader(
                new URL[]{new File(jar).toURI().toURL()}, AppRunner.class.getClassLoader());

        Class<?> act = Class.forName(activity, false, cl);
        System.out.println("[apklayer] loaded activity: " + act.getName());
        StringBuilder chain = new StringBuilder();
        for (Class<?> c = act; c != null; c = c.getSuperclass()) {
            chain.append(c.getName()).append(" <- ");
        }
        System.out.println("[apklayer] chain: " + chain);

        System.out.println("[apklayer] initializing activity class...");
        Class.forName(activity, true, cl);
        System.out.println("[apklayer] static init OK");

        System.out.println("[apklayer] instantiating (no attach yet)...");
        Object inst = act.getDeclaredConstructor().newInstance();
        System.out.println("[apklayer] INSTANTIATED: " + inst);
    }
}
