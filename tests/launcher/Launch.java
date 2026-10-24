package apklayer.launch;

import com.macapk.loader.BinaryXmlParser;
import com.macapk.loader.Boot;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** apklayer launcher: unpack APK -> decode manifest (pure-Java AXML) -> dex2jvm jar -> Boot.run. */
public final class Launch {
    public static void main(String[] args) throws Exception {
        String jni = System.getProperty("apklayer.jni");
        if (jni != null) System.load(jni);

        String apk = args[0];
        String pkg = new File(apk).getName().replace(".apk", "");
        File dir = new File(System.getProperty("user.home"), "apklayer-apps/" + pkg);
        dir.mkdirs();
        try (ZipFile z = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                File out = new File(dir, e.getName());
                out.getParentFile().mkdirs();
                try (InputStream in = z.getInputStream(e)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        File amf = new File(dir, "AndroidManifest.xml");
        byte[] am = Files.readAllBytes(amf.toPath());
        String text = BinaryXmlParser.isBinaryXml(am)
                ? BinaryXmlParser.toText(BinaryXmlParser.parse(am))
                : new String(am, "UTF-8");
        File mf = new File(dir, "manifest.txt");
        Files.writeString(mf.toPath(), text);

        File jar = new File(dir, pkg + "-classes.jar");
        if (!jar.exists()) {
            new ProcessBuilder("java", "-XX:UseSVE=0", "-cp",
                    System.getProperty("java.class.path"), "com.macapk.dex.Main",
                    apk, jar.getAbsolutePath()).inheritIO().start().waitFor();
        }
        System.out.println("[apklayer] unpacked=" + dir + " manifest=" + mf + " jar=" + jar);
        Boot.run(apk, jar.getAbsolutePath(), dir.getAbsolutePath(), mf.getAbsolutePath());
    }
}
