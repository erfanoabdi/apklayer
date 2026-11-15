/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * A {@code ClassLoader} implementation that implements a <b>delegate last</b> lookup policy: the boot class loader first, then this loader's own
 * dex path, then the parent (libcore's DelegateLastClassLoader, the same members and order).
 *
 * apklayer patch: libcore asks the boot class loader as {@code Object.class.getClassLoader()}, which is java.lang.BootClassLoader on ART and null on
 * the JVM: every loadClass of a class not yet loaded threw NullPointerException (Play services' Chimera/Dynamite modules: its Cronet network
 * module failed, the exception reached the uncaught handler and Play services ended its process). Here the boot loader is Android's
 * java.lang.BootClassLoader of the patched java.base (apklayer.host.AppClassLoader gives it the sandbox as its source), else this class's loader;
 * getResources joins its parts itself (sun.misc.CompoundEnumeration is absent on the host JDK).
 */
public final class DelegateLastClassLoader extends PathClassLoader {

    /** Whether resource loading delegates to the parent class loader. True by default. */
    private final boolean delegateResourceLoading;

    public DelegateLastClassLoader(String dexPath, ClassLoader parent) {
        this(dexPath, null, parent, true);
    }

    public DelegateLastClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        this(dexPath, librarySearchPath, parent, true);
    }

    public DelegateLastClassLoader(String dexPath, String librarySearchPath, ClassLoader parent, boolean delegateResourceLoading) {
        super(dexPath, librarySearchPath, parent);
        this.delegateResourceLoading = delegateResourceLoading;
    }

    public DelegateLastClassLoader(String dexPath, String librarySearchPath, ClassLoader parent, ClassLoader[] sharedLibraryLoaders) {
        this(dexPath, librarySearchPath, parent, sharedLibraryLoaders, null);
    }

    public DelegateLastClassLoader(String dexPath, String librarySearchPath, ClassLoader parent, ClassLoader[] sharedLibraryLoaders,
            ClassLoader[] sharedLibraryLoadersAfter) {
        super(dexPath, librarySearchPath, parent, sharedLibraryLoaders, sharedLibraryLoadersAfter);
        this.delegateResourceLoading = true;
    }

    /** apklayer patch: what Object.class.getClassLoader() is on ART (see the class comment). */
    private static ClassLoader bootLoader() {
        ClassLoader boot = Object.class.getClassLoader();
        if (boot != null) return boot;
        try {
            java.lang.reflect.Method get = Class.forName("java.lang.BootClassLoader").getDeclaredMethod("getInstance");
            get.setAccessible(true);
            return (ClassLoader) get.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException unpatched) {
            return DelegateLastClassLoader.class.getClassLoader();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // First, check whether the class has already been loaded. Return it if that's the case.
        Class<?> cl = findLoadedClass(name);
        if (cl != null) {
            return cl;
        }

        // Next, check whether the class in question is present in the boot classpath.
        try {
            return bootLoader().loadClass(name);  // apklayer patch: Object.class.getClassLoader() in libcore
        } catch (ClassNotFoundException ignored) {
        }

        // Next, check whether the class in question is present in the dexPath that this classloader operates on, or its shared libraries.
        ClassNotFoundException fromSuper = null;
        try {
            return findClass(name);
        } catch (ClassNotFoundException ex) {
            fromSuper = ex;
        }

        // Finally, check whether the class in question is present in the parent classloader.
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException cnfe) {
            // The exception we're catching here is the CNFE thrown by the parent of this classloader. However, we would like to throw a CNFE that
            // provides details about the class path / list of dex files associated with *this* classloader, so we choose to throw the exception
            // thrown above instead.
            throw fromSuper;
        }
    }

    @Override
    public URL getResource(String name) {
        // The lookup order we use here is the same as for classes.
        URL resource = bootLoader().getResource(name);  // apklayer patch: Object.class.getClassLoader() in libcore
        if (resource != null) {
            return resource;
        }

        resource = findResource(name);
        if (resource != null) {
            return resource;
        }

        if (delegateResourceLoading) {
            final ClassLoader cl = getParent();
            return (cl == null) ? null : cl.getResource(name);
        }
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        @SuppressWarnings("unchecked")
        final Enumeration<URL>[] resources = (Enumeration<URL>[]) new Enumeration<?>[] {
                bootLoader().getResources(name),  // apklayer patch: Object.class.getClassLoader() in libcore
                findResources(name),
                (getParent() == null || !delegateResourceLoading) ? null : getParent().getResources(name),
        };

        // apklayer patch: sun.misc.CompoundEnumeration is absent on the host JDK (as in BaseDexClassLoader's overlay): the same order, joined here
        java.util.List<URL> all = new java.util.ArrayList<>();
        for (Enumeration<URL> part : resources) if (part != null) all.addAll(java.util.Collections.list(part));
        return java.util.Collections.enumeration(all);
    }
}
