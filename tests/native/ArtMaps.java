import java.io.*;
import java.nio.file.*;

/** A real HotSpot process must present ART to app file APIs without losing its mappings. */
class ArtMaps {
    public static void main(String[] args) throws Exception {
        String art = "/apex/com.android.art/" + (System.getProperty("sun.arch.data.model").equals("64") ? "lib64" : "lib") + "/libopenjdkjvm.so";
        for (String path : new String[]{"/proc/self/maps", "/proc/thread-self/maps"}) {
            StringBuilder maps = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = in.readLine()) != null) maps.append(line).append('\n');
            }
            String text = maps.toString();
            if (!text.contains(art) || text.contains("libjvm.so") ||
                text.contains("libjava.so") || text.contains("/lib/modules") || text.contains(".jsa"))
                throw new AssertionError("Runtime maps identity: " + path);
            try (InputStream in = Files.newInputStream(Path.of(path))) {
                if (!new String(in.readAllBytes()).contains(art))
                    throw new AssertionError("NIO runtime identity");
            }
        }
        System.out.println("PASS real JVM: FileReader and NIO see ART without JDK mappings");
    }
}
