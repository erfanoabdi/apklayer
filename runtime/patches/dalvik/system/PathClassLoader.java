/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dalvik.system;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;

import android.annotation.SystemApi;

import libcore.util.NonNull;
import libcore.util.Nullable;

/**
 * Provides a simple {@link ClassLoader} implementation that operates on a list
 * of files and directories in the local file system, but does not attempt to
 * load classes from the network. Android uses this class for its system class
 * loader and for its application class loader(s).
 */
public class PathClassLoader extends BaseDexClassLoader {
    // apklayer patch: retain parallel class definition on HotSpot, as the converted index does.
    static { registerAsParallelCapable(); }

    /**
     * Creates a {@code PathClassLoader} that operates on a given list of files
     * and directories. This method is equivalent to calling
     * {@link #PathClassLoader(String, String, ClassLoader)} with a
     * {@code null} value for the second argument (see description there).
     *
     * @param dexPath the list of jar/apk files containing classes and
     * resources, delimited by {@code File.pathSeparator}, which
     * defaults to {@code ":"} on Android
     * @param parent the parent class loader
     */
    public PathClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, null, null, parent);
    }

    /**
     * Creates a {@code PathClassLoader} that operates on two given
     * lists of files and directories. The entries of the first list
     * should be one of the following:
     *
     * <ul>
     * <li>JAR/ZIP/APK files, possibly containing a "classes.dex" file as
     * well as arbitrary resources.
     * <li>Raw ".dex" files (not inside a zip file).
     * </ul>
     *
     * The entries of the second list should be directories containing
     * native library files.
     *
     * @param dexPath the list of jar/apk files containing classes and
     * resources, delimited by {@code File.pathSeparator}, which
     * defaults to {@code ":"} on Android
     * @param librarySearchPath the list of directories containing native
     * libraries, delimited by {@code File.pathSeparator}; may be
     * {@code null}
     * @param parent the parent class loader
     */
    public PathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, null, librarySearchPath, parent);
    }

    /**
     * Creates a {@code PathClassLoader} that operates on two given
     * lists of files and directories. The entries of the first list
     * should be one of the following:
     *
     * <ul>
     * <li>JAR/ZIP/APK files, possibly containing a "classes.dex" file as
     * well as arbitrary resources.
     * <li>Raw ".dex" files (not inside a zip file).
     * </ul>
     *
     * The entries of the second list should be directories containing
     * native library files.
     *
     * @param dexPath the list of jar/apk files containing classes and
     * resources, delimited by {@code File.pathSeparator}, which
     * defaults to {@code ":"} on Android
     * @param librarySearchPath the list of directories containing native
     * libraries, delimited by {@code File.pathSeparator}; may be
     * {@code null}
     * @param parent the parent class loader
     * @param sharedLibraryLoaders class loaders of Java shared libraries
     * used by this new class loader. The shared library loaders are always
     * checked before the {@code dexPath} when looking
     * up classes and resources.
     *
     * @hide
     */
    @SystemApi(client = MODULE_LIBRARIES)
    public PathClassLoader(
            @NonNull String dexPath, @Nullable String librarySearchPath, @Nullable ClassLoader parent,
            @Nullable ClassLoader[] sharedLibraryLoaders) {
        this(dexPath, librarySearchPath, parent, sharedLibraryLoaders, null);
    }

    /**
     * Creates a {@code PathClassLoader} that operates on two given
     * lists of files and directories. The entries of the first list
     * should be one of the following:
     *
     * <ul>
     * <li>JAR/ZIP/APK files, possibly containing a "classes.dex" file as
     * well as arbitrary resources.
     * <li>Raw ".dex" files (not inside a zip file).
     * </ul>
     *
     * The entries of the second list should be directories containing
     * native library files.
     *
     * @param dexPath the list of jar/apk files containing classes and
     * resources, delimited by {@code File.pathSeparator}, which
     * defaults to {@code ":"} on Android
     * @param librarySearchPath the list of directories containing native
     * libraries, delimited by {@code File.pathSeparator}; may be
     * {@code null}
     * @param parent the parent class loader
     * @param sharedLibraryLoaders class loaders of Java shared libraries
     * used by this new class loader. The shared library loaders are always
     * checked before the {@code dexPath} when looking
     * up classes and resources.
     * @param sharedLibraryLoadersAfter class loaders of Java shared libraries
     * used by this new class loader. These shared library loaders are always
     * checked <b>after</b> the {@code dexPath} when looking
     * up classes and resources.
     *
     * @hide
     */
    @SystemApi(client = MODULE_LIBRARIES)
    public PathClassLoader(
            @NonNull String dexPath, @Nullable String librarySearchPath,
            @Nullable ClassLoader parent, @Nullable ClassLoader[] sharedLibraryLoaders,
            @Nullable ClassLoader[] sharedLibraryLoadersAfter) {
        super(dexPath, librarySearchPath, parent, sharedLibraryLoaders, sharedLibraryLoadersAfter);
    }

    // apklayer patch: converted class bytes are indexed by the host loader, but are defined
    // in this real PathClassLoader. The delegate retains Android's library/app search order.
    private ClassLoader apklayerDelegate;
    private java.util.function.Function<String, String> apklayerNativeLibrary;

    private void setApklayerDelegate(ClassLoader delegate,
            java.util.function.Function<String, String> nativeLibrary) {
        if (apklayerDelegate != null) throw new IllegalStateException("loader already attached");
        apklayerDelegate = delegate;
        apklayerNativeLibrary = nativeLibrary;
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        ClassLoader delegate = apklayerDelegate;
        if (delegate == null) return super.loadClass(name, resolve);
        Class<?> result = delegate.loadClass(name);
        if (resolve) resolveClass(result);
        return result;
    }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        ClassLoader delegate = apklayerDelegate;
        if (delegate == null) return super.findClass(name);
        @SuppressWarnings("unchecked")
        java.util.function.Function<String, Class<?>> own =
                (java.util.function.Function<String, Class<?>>) delegate;
        Class<?> result = own.apply(name);
        if (result == null) throw new ClassNotFoundException(name);
        return result;
    }

    @Override public java.net.URL getResource(String name) {
        ClassLoader delegate = apklayerDelegate;
        return delegate == null ? super.getResource(name) : delegate.getResource(name);
    }

    @Override public java.util.Enumeration<java.net.URL> getResources(String name) throws java.io.IOException {
        ClassLoader delegate = apklayerDelegate;
        return delegate == null ? super.getResources(name) : delegate.getResources(name);
    }

    @Override public String findLibrary(String name) {
        java.util.function.Function<String, String> lookup = apklayerNativeLibrary;
        return lookup == null ? super.findLibrary(name) : lookup.apply(name);
    }
}
