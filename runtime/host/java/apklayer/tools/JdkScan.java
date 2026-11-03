package apklayer.tools;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Finds, before any crash, the java.* / javax.* / sun.* / jdk.* members Android code expects from libcore that the JDK this JVM runs lacks
 * (or has with another static-ness, field type or a narrower access): those native libraries look up through JNI and those app bytecode uses.
 * Native libraries: a (class, name, signature) counts when the class name, the name and the signature are all strings in the library and
 * libcore's class has that member (string pairing, no disassembly). Bytecode: every field and method instruction on such an owner.
 * Each finding is "covered" when the jdk-patch dir (apklayer.host.JdkPatch), the runtime shim's JNI renames, a native source patch (COVERED)
 * or, in sandbox-instrumented classes (android-all as an input), one of Robolectric's interceptors supply it.
 * Behaviour differences (iterator semantics, accepted auth types) are not visible here.
 *
 * Usage: java -cp asm.jar:... apklayer.tools.JdkScan --libcore core-all.jar [--patch native/jdk-patch] [--android-all jar] input...
 *   input: a .so, a classes .jar, an .apk (its lib/arm64-v8a/*.so) or a directory (its *.so, *.apk and classes.jar, recursively).
 *   --self-check runs the built-in check (needs --libcore and --patch) instead.
 *   core-all.jar: the tree's ~/los24/out/soong/.intermediates/libcore/core-all/android_common/javac/core-all.jar (read-only copy). Run on the JDK apklayer uses, e.g. on the VM:
 *   java -cp <host-classes>:<native>/aosp/Robolectric_all.jar apklayer.tools.JdkScan --libcore core-all.jar --patch <native>/jdk-patch
 *     --android-all <native>/aosp/android-all/android-all-16-*.jar <that jar> ~/android-arm64/lib64 ~/.local/share/apklayer/apps
 */
public final class JdkScan {
    private JdkScan() {}

    private static final String[] PREFIXES = {"java/", "javax/", "sun/", "jdk/"};
    /** Findings covered outside the jdk-patch dir (regex on the finding key -> where). */
    private static final Map<String, String> COVERED = Map.of(
            "java/io/FileDescriptor\\.descriptor:I", "runtime_shim GetFieldID descriptor->fd",
            "java/nio/(NIOAccess|[A-Za-z]*Buffer\\._elementSizeShift:I)", "scripts/patch-nativehelper.sh: public Buffer API");

    record Member(String owner, String name, String desc, int access) {
        boolean isStatic() { return (access & Opcodes.ACC_STATIC) != 0; }
    }

    record Info(String name, String superName, String[] interfaces, Map<String, Member> fields, Map<String, Member> methods) {}

    /** A class hierarchy read from class files (JVM-style member resolution). */
    static final class Repo {
        private final Function<String, byte[]> source;
        private final Map<String, Info> cache = new HashMap<>();
        Repo(Function<String, byte[]> source) { this.source = source; }

        Info get(String name) {
            if (cache.containsKey(name)) return cache.get(name);
            byte[] bytes = source.apply(name);
            Info info = null;
            if (bytes != null) {
                ClassReader reader = new ClassReader(bytes);
                Map<String, Member> fields = new HashMap<>(), methods = new HashMap<>();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) { fields.put(n, new Member(name, n, d, a)); return null; }
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) { methods.put(n + d, new Member(name, n, d, a)); return null; }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                info = new Info(name, reader.getSuperName(), reader.getInterfaces(), fields, methods);
            }
            cache.put(name, info);
            return info;
        }

        /** Field by name (JVMS 5.4.3.2 order: class, its interfaces, its super class); the caller compares the type. */
        Member field(String owner, String name) {
            Info info = get(owner);
            if (info == null) return null;
            if (info.fields.containsKey(name)) return info.fields.get(name);
            for (String i : info.interfaces) { Member m = field(i, name); if (m != null) return m; }
            return info.superName == null ? null : field(info.superName, name);
        }

        /** Method by name and descriptor: the class and its super classes, then the interfaces. */
        Member method(String owner, String nameDesc) {
            for (String c = owner; c != null; ) {
                Info info = get(c);
                if (info == null) return null;
                if (info.methods.containsKey(nameDesc)) return info.methods.get(nameDesc);
                c = info.superName;
            }
            return interfaceMethod(owner, nameDesc, new HashSet<>());
        }

        private Member interfaceMethod(String owner, String nameDesc, Set<String> seen) {
            Info info = get(owner);
            if (info == null || !seen.add(owner)) return null;
            for (String i : info.interfaces) {
                Info ii = get(i);
                if (ii != null && ii.methods.containsKey(nameDesc)) return ii.methods.get(nameDesc);
                Member m = interfaceMethod(i, nameDesc, seen);
                if (m != null) return m;
            }
            return info.superName == null ? null : interfaceMethod(info.superName, nameDesc, seen);
        }
    }

    static Repo libcore, jdk, patched, androidAll;
    /** status -> finding -> users. */
    static final Map<String, Map<String, Set<String>>> FINDINGS = new TreeMap<>();

    static boolean javaOwner(String owner) {
        for (String p : PREFIXES) if (owner.startsWith(p)) return true;
        return false;
    }

    static byte[] jdkBytes(String name) {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) { return in == null ? null : in.readAllBytes(); }
        catch (java.io.IOException e) { return null; }
    }

    static Function<String, byte[]> zipSource(String jar) throws java.io.IOException {
        ZipFile zip = new ZipFile(jar);
        return name -> {
            ZipEntry e = zip.getEntry(name + ".class");
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) { return in.readAllBytes(); } catch (java.io.IOException x) { return null; }
        };
    }

    /** The difference between libcore's member and the JDK's (null when they agree) for a reference by name (+ descriptor for methods). */
    static String compare(Repo repo, boolean field, String owner, String name, String desc, boolean bytecode) {
        Member want = field ? libcore.field(owner, name) : libcore.method(owner, name + desc);
        if (want == null || (field && !want.desc.equals(desc))) return null; // not libcore's either: nothing Android code could rely on
        if (repo.get(owner) == null) return "class missing";
        Member have = field ? repo.field(owner, name) : repo.method(owner, name + desc);
        if (have == null) return "missing";
        if (field && !have.desc.equals(desc)) return "type " + have.desc + " in the JDK";
        if (want.isStatic() != have.isStatic()) return have.isStatic() ? "static in the JDK" : "instance in the JDK";
        if (bytecode && (want.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && (have.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0
                && !owner.equals(have.owner)) return null; // inherited non-public: the JVM resolves further, rare; not reported
        if (bytecode && (want.access & Opcodes.ACC_PUBLIC) != 0 && (have.access & Opcodes.ACC_PUBLIC) == 0) return "not public in the JDK";
        return null;
    }

    /** "owner.name" (or "owner.*") calls Robolectric's interceptors rewrite in the classes its sandbox instruments (the framework, not DoNotInstrument app classes). */
    static final Set<String> INTERCEPTED = new HashSet<>();

    static void report(boolean field, String owner, String name, String desc, boolean bytecode, String user) { report(field, owner, name, desc, bytecode, user, false); }

    static void report(boolean field, String owner, String name, String desc, boolean bytecode, String user, boolean sandboxed) {
        String diff = compare(jdk, field, owner, name, desc, bytecode);
        if (diff == null || diff.equals("class missing")) return; // a missing class is reported once, by reportClass
        String key = owner + "." + name + (field ? ":" : "") + desc;
        String status;
        if (compare(patched, field, owner, name, desc, bytecode) == null) status = "covered (JdkPatch)";
        else if (sandboxed && !field && (INTERCEPTED.contains(owner + "." + name) || INTERCEPTED.contains(owner + ".*"))) status = "covered (Robolectric interceptor)";
        else status = shortcutCovered(key);
        FINDINGS.computeIfAbsent(status, k -> new TreeMap<>()).computeIfAbsent((bytecode ? "app " : "jni ") + key + "  [" + diff + "]", k -> new TreeSet<>()).add(user);
    }

    static String shortcutCovered(String key) {
        for (var e : COVERED.entrySet()) if (key.matches(e.getKey())) return "covered (" + e.getValue() + ")";
        return "NOT covered";
    }

    /** A libcore class the JDK lacks; android-all supplies it only outside java.* (the JVM defines java.* in the boot layer alone). */
    static void reportClass(String owner, String user) {
        if (libcore.get(owner) == null || jdk.get(owner) != null || (androidAll != null && !owner.startsWith("java/") && androidAll.get(owner) != null)) return;
        String status = patched.get(owner) != null ? "covered (JdkPatch)" : shortcutCovered(owner);
        FINDINGS.computeIfAbsent(status, k -> new TreeMap<>()).computeIfAbsent("class " + owner + "  [class missing]", k -> new TreeSet<>()).add(user);
    }

    static int libraries, classes;

    static boolean inherited(Member m) { return !m.isStatic() && (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0; }

    /** NUL-separated printable strings of a binary. Names must be whole strings (a tail match, "count" in "account", is noise); a signature may be the tail of a longer one (lld tail merging, "I"). */
    static void scanNative(byte[] data, String user) {
        libraries++;
        Set<String> strings = new HashSet<>();
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i < data.length && data[i] >= 0x20 && data[i] < 0x7f) continue;
            if (i > start && i < data.length && data[i] == 0) strings.add(new String(data, start, i - start, StandardCharsets.US_ASCII));
            start = i + 1;
        }
        List<String> all = new ArrayList<>(strings);
        String[] reversed = all.stream().map(t -> new StringBuilder(t).reverse().toString()).sorted().toArray(String[]::new);
        Function<String, Boolean> has = k -> { // a string, or the end of one (signatures): binary search among the reversed strings
            if (strings.contains(k)) return true;
            String r = new StringBuilder(k).reverse().toString();
            int at = java.util.Arrays.binarySearch(reversed, r);
            return at < 0 && -at - 1 < reversed.length && reversed[-at - 1].startsWith(r);
        };
        for (String s : all) {
            String owner = s.startsWith("L") && s.endsWith(";") ? s.substring(1, s.length() - 1) : s;
            if (!javaOwner(owner) || !owner.matches("[A-Za-z0-9_/$]+")) continue;
            reportClass(owner, user);
            for (String c = owner; c != null; ) { // members declared in the class, and the public/protected instance ones of its libcore super classes
                Info info = libcore.get(c);
                if (info == null) break;
                boolean self = c.equals(owner);
                for (Member m : info.fields.values()) if ((self || inherited(m)) && strings.contains(m.name) && has.apply(m.desc)) report(true, owner, m.name, m.desc, false, user);
                for (Member m : info.methods.values()) if ((self || inherited(m)) && strings.contains(m.name) && has.apply(m.desc)) report(false, owner, m.name, m.desc, false, user);
                c = info.superName;
            }
        }
    }

    static void scanJar(JarFile jar, String user) throws java.io.IOException {
        Set<String> own = new HashSet<>();
        for (JarEntry e : Collections.list(jar.entries())) if (e.getName().endsWith(".class")) own.add(e.getName().substring(0, e.getName().length() - 6));
        for (JarEntry e : Collections.list(jar.entries())) {
            if (!e.getName().endsWith(".class")) continue;
            ClassReader reader;
            try (InputStream in = jar.getInputStream(e)) { reader = new ClassReader(in.readAllBytes()); }
            String self = reader.getClassName();
            classes++;
            Function<String, Boolean> wanted = owner -> javaOwner(owner) && !own.contains(owner);
            if (reader.getSuperName() != null && wanted.apply(reader.getSuperName())) reportClass(reader.getSuperName(), user + ":" + self);
            boolean[] sandboxed = {true}; // DenyInstrument stamps app classes DoNotInstrument: no interceptors there
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.equals("Lorg/robolectric/annotation/internal/DoNotInstrument;")) sandboxed[0] = false;
                    return null;
                }
                @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] x) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
                            if (wanted.apply(owner)) { reportClass(owner, user + ":" + self); report(true, owner, name, desc, true, user + ":" + self, sandboxed[0]); }
                        }
                        @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                            if (wanted.apply(owner)) { reportClass(owner, user + ":" + self); report(false, owner, name, desc, true, user + ":" + self, sandboxed[0]); }
                        }
                        @Override public void visitTypeInsn(int op, String type) { if (wanted.apply(type)) reportClass(type, user + ":" + self); }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    static void scan(Path input) throws java.io.IOException {
        String name = input.getFileName().toString();
        if (Files.isDirectory(input)) {
            try (Stream<Path> walk = Files.walk(input)) {
                for (Path p : walk.filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".apk") || p.getFileName().toString().equals("classes.jar")).sorted().toList()) scan(p);
            }
        } else if (name.endsWith(".so")) {
            scanNative(Files.readAllBytes(input), name);
        } else if (name.endsWith(".apk")) {
            try (ZipFile apk = new ZipFile(input.toFile())) {
                for (ZipEntry e : Collections.list(apk.entries())) {
                    if (!e.getName().startsWith("lib/arm64-v8a/") || !e.getName().endsWith(".so")) continue;
                    try (InputStream in = apk.getInputStream(e)) { scanNative(in.readAllBytes(), name + "!" + e.getName().substring(14)); }
                }
            }
        } else if (name.endsWith(".jar")) {
            try (JarFile jar = new JarFile(input.toFile())) { scanJar(jar, input.getParent() == null ? name : input.getParent().getFileName() + "/" + name); }
        }
    }

    public static void main(String[] args) throws Exception {
        List<Path> inputs = new ArrayList<>();
        String patchDir = null;
        boolean selfCheck = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--libcore" -> libcore = new Repo(zipSource(args[++i]));
                case "--patch" -> patchDir = args[++i];
                case "--android-all" -> androidAll = new Repo(zipSource(args[++i]));
                case "--self-check" -> selfCheck = true;
                default -> inputs.add(Path.of(args[i]));
            }
        }
        if (libcore == null) throw new IllegalArgumentException("--libcore core-all.jar is required");
        jdk = new Repo(JdkScan::jdkBytes);
        Path patch = patchDir == null ? null : Path.of(patchDir);
        patched = new Repo(n -> {
            Path p = patch == null ? null : patch.resolve(n + ".class");
            try { return p != null && Files.exists(p) ? Files.readAllBytes(p) : jdkBytes(n); } catch (java.io.IOException e) { return null; }
        });
        for (var interceptor : org.robolectric.interceptors.AndroidInterceptors.all())
            for (var ref : interceptor.getMethodRefs()) INTERCEPTED.add(ref.className.replace('.', '/') + "." + ref.methodName);
        if (selfCheck) { selfCheck(); return; }
        for (Path input : inputs) scan(input);
        System.out.println("scanned " + libraries + " native libraries, " + classes + " app classes");
        for (var status : FINDINGS.entrySet()) {
            System.out.println("== " + status.getKey() + " (" + status.getValue().size() + ")");
            for (var f : status.getValue().entrySet()) {
                List<String> users = new ArrayList<>(f.getValue());
                System.out.println(f.getKey() + "\t" + users.size() + " users: " + String.join(" ", users.subList(0, Math.min(4, users.size()))) + (users.size() > 4 ? " ..." : ""));
            }
        }
    }

    /** Conscrypt's and Chromium's FileDescriptor lookups as a fake library: descriptor is the shim's, getInt$ JdkPatch's, a libcore-only Unix class is not covered. */
    static void selfCheck() {
        byte[] lib = "\0java/io/FileDescriptor\0descriptor\0I\0getInt$\0()I\0java/io/FileDescriptor\0fd\0".getBytes(StandardCharsets.US_ASCII);
        scanNative(lib, "self");
        String all = FINDINGS.toString();
        check(all.contains("covered (runtime_shim GetFieldID descriptor->fd)={jni java/io/FileDescriptor.descriptor:I"), "descriptor via the shim", all);
        check(all.contains("covered (JdkPatch)={jni java/io/FileDescriptor.getInt$()I"), "getInt$ via JdkPatch", all);
        check(!all.contains(".fd:I"), "fd exists in the JDK", all);
        check(compare(patched, false, "java/lang/Thread", "stop", "(Ljava/lang/Throwable;)V", true) == null, "Thread.stop(Throwable) copied from JdkMembers", "missing in --patch");
        System.out.println("PASS JdkScan self-check");
    }

    static void check(boolean ok, String what, String got) { if (!ok) throw new AssertionError(what + ": " + got); }
}
