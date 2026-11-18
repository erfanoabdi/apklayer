package apklayer.host;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLoadedApk;
import org.robolectric.util.ReflectionHelpers;

/** Supplies the process app loader and a separate loader for authorized foreign package contexts. */
@Implements(className = "android.app.LoadedApk", isInAndroidSdk = false)
public class ShadowHostLoadedApk extends ShadowLoadedApk {
    @Implementation @Override public ClassLoader getClassLoader() {
        if (isForeign(loaded)) return FrameworkParent.INSTANCE;
        HostProcessName.fill(loaded.getApplicationInfo());  // before the Application is made (makeApplication asks for the loader first): attachBaseContext sees it
        ClassLoader sandbox = super.getClassLoader();  // also creates the manifest's AppComponentFactory
        return AppClassLoader.app != null ? AppClassLoader.app : sandbox;
    }

    @Implementation @Override public ClassLoader getSplitClassLoader(String splitName) throws android.content.pm.PackageManager.NameNotFoundException {
        // ContextImpl selects foreign code with the context flags after the framework constructs the split context.
        return getClassLoader();
    }

    @org.robolectric.annotation.RealObject private android.app.LoadedApk loaded;

    private ClassLoader foreignLoader;
    private CodeSource foreignSource;

    // Main publishes the launch's index before entering AppHost or ComponentHost, including after AppSpare handoff.
    private static Path processInstall() {
        if (!(AppClassLoader.index instanceof URLClassLoader index)) return null;
        URL[] launchPaths = index.getURLs();
        if (launchPaths.length != 2 || !"file".equals(launchPaths[1].getProtocol())) return null;
        try {
            Path base = Path.of(launchPaths[1].toURI()).toAbsolutePath().normalize();
            return base.endsWith("base.apk") ? base.getParent() : null;
        } catch (URISyntaxException | IllegalArgumentException invalidPath) {
            return null;
        }
    }

    static boolean isForeign(android.app.LoadedApk apk) {
        Path process = processInstall();
        return apk != null && process != null && !process.getFileName().toString().equals(apk.getPackageName());
    }

    /** Only a framework-created ContextImpl supplies the flags for this lookup. */
    synchronized ClassLoader classLoaderForContext(int flags) {
        if ((flags & Context.CONTEXT_INCLUDE_CODE) == 0
                || !ReflectionHelpers.<Boolean>getField(loaded, "mIncludeCode")
                || (loaded.isSecurityViolation() && (flags & Context.CONTEXT_IGNORE_SECURITY) == 0)) {
            return FrameworkParent.INSTANCE;
        }
        ApplicationInfo info = loaded.getApplicationInfo();
        if (info == null || (info.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) return FrameworkParent.INSTANCE;

        try {
            Path process = processInstall();
            if (process == null) return FrameworkParent.INSTANCE;
            CodeSource source = codeSource(process.getParent(), loaded.getPackageName(), loaded.getAppDir());
            if (source == null) return FrameworkParent.INSTANCE;
            // Wait for the framework metadata refresh if the installer replaced either code file.
            if (foreignLoader != null) return source.sameCode(foreignSource) ? foreignLoader : FrameworkParent.INSTANCE;

            // Pin the opened archive and check the publication again before exposing its classes.
            JarFile jar = new JarFile(source.jar().toFile());
            boolean accepted = false;
            try {
                if (!source.equals(codeSource(process.getParent(), loaded.getPackageName(), loaded.getAppDir()))) {
                    return FrameworkParent.INSTANCE;
                }
                foreignLoader = new InstalledCodeLoader(loaded.getPackageName(), source.jar().toUri().toURL(), jar);
                foreignSource = source;
                accepted = true;
                return foreignLoader;
            } finally {
                if (!accepted) jar.close();
            }
        } catch (IOException | InvalidPathException unavailable) {
            return FrameworkParent.INSTANCE;
        }
    }

    private static CodeSource codeSource(Path appsDir, String packageName, String authorizedBase) throws IOException {
        if (appsDir == null || authorizedBase == null || packageName == null
                || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) return null;
        Path expected = appsDir.resolve(packageName);
        Path base = Path.of(authorizedBase).toAbsolutePath().normalize();
        if (!expected.resolve("base.apk").equals(base) || !Files.isDirectory(expected, LinkOption.NOFOLLOW_LINKS)) return null;
        Path jar = base.resolveSibling("classes.jar");
        // CLI publishes info.properties last, after replacing the APKs and converted jar.
        FileStamp commit = FileStamp.read(base.resolveSibling("info.properties"));
        FileStamp baseStamp = FileStamp.read(base);
        FileStamp jarStamp = FileStamp.read(jar);
        if (commit == null || baseStamp == null || jarStamp == null
                || commit.modified().compareTo(baseStamp.modified()) < 0
                || commit.modified().compareTo(jarStamp.modified()) < 0) return null;
        return new CodeSource(jar, commit, baseStamp, jarStamp);
    }

    private record CodeSource(Path jar, FileStamp commit, FileStamp baseStamp, FileStamp jarStamp) {
        boolean sameCode(CodeSource other) {
            return jar.equals(other.jar) && baseStamp.equals(other.baseStamp) && jarStamp.equals(other.jarStamp);
        }
    }

    private record FileStamp(Object key, long size, FileTime modified) {
        static FileStamp read(Path path) throws IOException {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isRegularFile() ? new FileStamp(attrs.fileKey(), attrs.size(), attrs.lastModifiedTime()) : null;
        }
    }

    /** Read every class and resource from the same open archive across installer renames. */
    private static final class InstalledCodeLoader extends SecureClassLoader {
        static { registerAsParallelCapable(); }

        private final URL location;
        private final JarFile jar;

        InstalledCodeLoader(String name, URL location, JarFile jar) {
            super(name, FrameworkParent.INSTANCE);
            this.location = location;
            this.jar = jar;
        }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            var entry = jar.getJarEntry(name.replace('.', '/') + ".class");
            if (entry == null) throw new ClassNotFoundException(name);
            try (InputStream input = jar.getInputStream(entry)) {
                byte[] bytes = input.readAllBytes();
                var source = new java.security.CodeSource(location, entry.getCodeSigners());
                return defineClass(name, bytes, 0, bytes.length, source);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override protected URL findResource(String name) {
            var entry = jar.getJarEntry(name);
            if (entry == null) return null;
            try {
                return URL.of(URI.create("jar:" + location.toExternalForm() + "!/"
                        + URLEncoder.encode(name, StandardCharsets.UTF_8)), new URLStreamHandler() {
                    @Override protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override public void connect() { connected = true; }
                            @Override public InputStream getInputStream() throws IOException {
                                connect();
                                return jar.getInputStream(entry);
                            }
                        };
                    }
                });
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid installed resource URL", e);
            }
        }

        @Override protected Enumeration<URL> findResources(String name) {
            URL resource = findResource(name);
            return Collections.enumeration(resource == null ? List.of() : List.of(resource));
        }
    }

    /** Invoke the overlay's framework lookup directly, before its caller-app fallback.
     *  Resource lookup follows the platform parent and never enters the sandbox. */
    private static final class FrameworkParent extends ClassLoader {
        static final FrameworkParent INSTANCE = new FrameworkParent();
        private final ClassLoader sandbox = ShadowHostLoadedApk.class.getClassLoader();
        private final Method frameworkLookup;

        private FrameworkParent() {
            super(ClassLoader.getPlatformClassLoader());
            try {
                Class<?> type = Class.forName("org.robolectric.internal.bytecode.SandboxClassLoader", false, sandbox);
                frameworkLookup = type.getDeclaredMethod("sandboxLoadClass", String.class, boolean.class);
                frameworkLookup.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing apklayer sandbox framework lookup", e);
            }
        }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return (Class<?>) frameworkLookup.invoke(sandbox, name, false);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ClassNotFoundException missing) throw missing;
                if (cause instanceof RuntimeException failure) throw failure;
                if (cause instanceof Error failure) throw failure;
                throw new ClassNotFoundException(name, cause);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Android calls this when it refreshes a cached LoadedApk after an install update. */
    @Implementation protected synchronized void createOrUpdateClassLoaderLocked(List<String> addedPaths) {
        foreignLoader = null;
        foreignSource = null;
    }

    /** The installed splits' resources join the base's (getResources), as PackageManagerService's split paths make them on Android; the app sees them in its ApplicationInfo. */
    @Implementation protected String[] getSplitPaths(String splitName) {
        android.content.pm.ApplicationInfo info = loaded.getApplicationInfo();
        ShadowHostPackageManager.installedPaths(info);
        return info.splitSourceDirs;
    }

    /** The same split paths for an activity's context (ContextImpl.createActivityContext reads mSplitResDirs, set from the ApplicationInfo
     *  before installedPaths filled its split paths: an activity saw only the base's resources). */
    @Implementation protected String[] getSplitResDirs() { return getSplitPaths(null); }
}
