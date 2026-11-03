package apklayer.host;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.robolectric.annotation.Config;

/** APK launcher metadata, shared by install and launch. */
final class AppMetadata {
    private static ResolveInfo launcher(Application app) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
        List<ResolveInfo> found = app.getPackageManager().queryIntentActivities(query, 0);
        return found.isEmpty() ? null : found.get(0);
    }

    static Intent launcherIntent(Application app) {
        ResolveInfo resolved = launcher(app);
        if (resolved == null) throw new IllegalStateException("No launcher activity in " + app.getPackageName());
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.getPackageName(), resolved.activityInfo.name);
    }

    /** What a launcher shows: the launcher activity's label (falls back to the app's). */
    static String label(Application app) {
        ResolveInfo resolved = launcher(app);
        return String.valueOf(resolved == null ? app.getPackageManager().getApplicationLabel(app.getApplicationInfo())
                : resolved.loadLabel(app.getPackageManager()));
    }

    /**
     * Install-time metadata (PLAN D19): info.properties plus the launcher icon rendered by the
     * framework's own drawable code (adaptive icons included) as a 256 px PNG.
     */
    static void describe(Application app, Path dir) {
        try {
            PackageManager pm = app.getPackageManager();
            ResolveInfo launcher = launcher(app);
            PackageInfo pkg = pm.getPackageInfo(app.getPackageName(), 0);
            Properties info = new Properties();
            info.setProperty("package", app.getPackageName());
            info.setProperty("label", label(app));
            info.setProperty("launcher", launcher == null ? "" : launcher.activityInfo.name);
            info.setProperty("versionName", String.valueOf(pkg.versionName));
            info.setProperty("versionCode", String.valueOf(pkg.getLongVersionCode()));
            info.setProperty("targetSdk", String.valueOf(app.getApplicationInfo().targetSdkVersion));
            List<ManifestFilters.Filter> filters = ManifestFilters.read(app);
            for (int i = 0; i < filters.size(); i++) info.setProperty("filter." + i, filters.get(i).encode()); // for cross-app intents (PLAN P6)
            android.content.pm.ProviderInfo[] providers = pm.getPackageInfo(app.getPackageName(), PackageManager.GET_PROVIDERS).providers;
            info.setProperty("authorities", providers == null ? "" : String.join(";", java.util.Arrays.stream(providers).map(p -> p.authority).filter(java.util.Objects::nonNull).toList()));  // read at launch before the manifest is parsed (AppEnvironment)
            signer(info, dir, app.getPackageName());
            Files.createDirectories(dir);
            try (Writer out = Files.newBufferedWriter(dir.resolve("info.properties"))) {
                info.store(out, "apklayer install metadata");
            }

            int size = 256;
            Drawable icon = launcher == null ? app.getApplicationInfo().loadIcon(pm) : launcher.loadIcon(pm);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(bitmap));
            int[] px = new int[size * size];
            bitmap.getPixels(px, 0, size, 0, 0, size, size);
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, size, size, px, 0, size);
            ImageIO.write(img, "png", dir.resolve("icon.png").toFile());
            System.out.println("[apklayer] described " + app.getPackageName() + " (" + info.getProperty("label") + ")");
        } catch (IOException | PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The signer of the new package (its base and every split, as PackageManagerService requires) and, on an update, whether the installed one has another
     * (signerMismatch: install refuses it). shortcut: an unsigned APK (the test apps) has signer "" and updates an unsigned install; Android refuses
     * unsigned APKs outright, refuse them too if anything but the test apps ever comes unsigned.
     */
    private static void signer(Properties info, Path dir, String pkg) throws IOException {
        String signer = HostCertificate.digest(dir.resolve("base.apk").toFile());
        try (var files = Files.list(dir)) {
            for (Path split : files.filter(f -> f.getFileName().toString().matches("split_.*\\.apk")).sorted().toList()) {
                if (!HostCertificate.digest(split.toFile()).equals(signer)) throw new IOException(split.getFileName() + " is not signed like base.apk");
                info.setProperty("splits", (info.getProperty("splits", "").isEmpty() ? "" : info.getProperty("splits") + ",") + split.getFileName().toString().replaceAll("^split_|\\.apk$", ""));
            }
        }
        info.setProperty("signer", signer);
        String apps = System.getProperty("apklayer.apps");
        java.io.File installed = apps == null ? null : Path.of(apps, pkg, "base.apk").toFile();
        if (installed != null && installed.isFile() && !HostCertificate.digest(installed).equals(signer)) info.setProperty("signerMismatch", "true");
    }

    private AppMetadata() {}
}
