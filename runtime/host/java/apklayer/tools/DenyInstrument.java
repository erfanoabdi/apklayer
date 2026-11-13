package apklayer.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Marks every class in a jar with @org.robolectric.annotation.internal.DoNotInstrument so
 * the Robolectric sandbox loads it verbatim. Needed for dex2jvm output: its constructor
 * lowering (<init> -> __constructor__) collides with Robolectric's own <init> renaming
 * (VerifyError). App classes don't need shadow dispatch — only the framework does.
 *
 * Classes an app defines in a java.* package are moved to a private package (the JVM defines java.* only in the boot loader). An app's
 * own copies of libraries the host runs too (Guava, BouncyCastle) need nothing: the app has a class loader of its own (apklayer.host.AppClassLoader).
 *
 * Routes the app's startService/stopService/bindService/unbindService calls to
 * apklayer.host.Services, which runs the app's own services in-process.
 * Sends Unsafe.getUnsafe() (also through Method.invoke) to apklayer.host.UnsafeAccess.
 * And turns MultiDex.install(Context) into a no-op (all dex files are merged into one jar already).
 *
 * Usage: java -cp <asm>:... apklayer.tools.DenyInstrument <in.jar> <out.jar>
 */
public final class DenyInstrument {
    private static final String ANNO = "Lorg/robolectric/annotation/internal/DoNotInstrument;";

    private static final String RELOCATED_TO = "apklayer/relocated/";
    // classes an app defines in a java.* package (WhatsApp's java.lang.RunnableEBaseShape*): the JVM refuses to define them outside the boot loader
    private final java.util.Set<String> JAVA_OWN = new java.util.HashSet<>();

    /** Maps the app's own java.* classes into the private package. */
    private final Remapper RELOCATOR = new Remapper() {
        @Override
        public String map(String name) {
            return JAVA_OWN.contains(name) ? RELOCATED_TO + name : name;
        }
    };

    /** class -> super class, and the "name+desc" methods each jar class declares (for service call routing). */
    private final java.util.Map<String, String> SUPER = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.Set<String>> DECLARED = new java.util.HashMap<>();
    private static final java.util.Map<String, String> SERVICE_CALLS = java.util.Map.of(
            "bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z",
            "(Landroid/content/Context;Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z",
            "unbindService(Landroid/content/ServiceConnection;)V",
            "(Landroid/content/Context;Landroid/content/ServiceConnection;)V",
            "startService(Landroid/content/Intent;)Landroid/content/ComponentName;",
            "(Landroid/content/Context;Landroid/content/Intent;)Landroid/content/ComponentName;",
            "startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;",
            "(Landroid/content/Context;Landroid/content/Intent;)Landroid/content/ComponentName;",
            "stopService(Landroid/content/Intent;)Z",
            "(Landroid/content/Context;Landroid/content/Intent;)Z");

    private static boolean propertyCall(String owner, String name, String desc) {
        if (owner.equals("java/lang/System")) return java.util.Set.of(
                "getProperty(Ljava/lang/String;)Ljava/lang/String;",
                "getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                "setProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                "clearProperty(Ljava/lang/String;)Ljava/lang/String;",
                "getProperties()Ljava/util/Properties;", "setProperties(Ljava/util/Properties;)V").contains(name + desc);
        if (owner.equals("java/lang/Boolean")) return name.equals("getBoolean") && desc.equals("(Ljava/lang/String;)Z");
        if (owner.equals("java/lang/Integer")) return name.equals("getInteger") && java.util.Set.of(
                "(Ljava/lang/String;)Ljava/lang/Integer;", "(Ljava/lang/String;I)Ljava/lang/Integer;",
                "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/lang/Integer;").contains(desc);
        return owner.equals("java/lang/Long") && name.equals("getLong") && java.util.Set.of(
                "(Ljava/lang/String;)Ljava/lang/Long;", "(Ljava/lang/String;J)Ljava/lang/Long;",
                "(Ljava/lang/String;Ljava/lang/Long;)Ljava/lang/Long;").contains(desc);
    }

    private static boolean environmentCall(String owner, String name, String desc) {
        if (owner.equals("java/lang/System")) return name.equals("getenv")
                && (desc.equals("(Ljava/lang/String;)Ljava/lang/String;") || desc.equals("()Ljava/util/Map;"));
        if (owner.equals("java/lang/ProcessBuilder")) return (name.equals("environment") && desc.equals("()Ljava/util/Map;"))
                || (name.equals("start") && desc.equals("()Ljava/lang/Process;"))
                || (name.equals("startPipeline") && desc.equals("(Ljava/util/List;)Ljava/util/List;"));
        return owner.equals("java/lang/Runtime") && name.equals("exec") && java.util.Set.of(
                "(Ljava/lang/String;)Ljava/lang/Process;", "([Ljava/lang/String;)Ljava/lang/Process;",
                "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;", "([Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;",
                "(Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;",
                "([Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;").contains(desc);
    }

    private static String withReceiver(String owner, String desc) { return "(L" + owner + ";" + desc.substring(1); }

    private static Object propertyHandle(Object value) {
        if (value instanceof Handle) {
            Handle h = (Handle) value;
            if ((h.getTag() == Opcodes.H_INVOKESTATIC || h.getTag() == Opcodes.H_INVOKEVIRTUAL)
                    && environmentCall(h.getOwner(), h.getName(), h.getDesc()))
                return new Handle(Opcodes.H_INVOKESTATIC, "apklayer/host/GuestEnvironment", h.getName(),
                        h.getTag() == Opcodes.H_INVOKESTATIC ? h.getDesc() : withReceiver(h.getOwner(), h.getDesc()), false);
            if (h.getTag() == Opcodes.H_INVOKESTATIC && propertyCall(h.getOwner(), h.getName(), h.getDesc()))
                return new Handle(h.getTag(), "apklayer/host/GuestSystemProperties", h.getName(), h.getDesc(), false);
        }
        return value;
    }

    /** True if the call lands on the framework's Context method (not on a method the app declares). */
    private boolean isFrameworkContextCall(String owner, String name, String desc) {
        String c = sourceName(owner);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (c != null && seen.add(c)) {
            // InMemoryDexClassLoader delegates to its parent before its own DEX. A bundled
            // copy of an app class must not override the metadata of the class actually used.
            // Android framework owners remain the known service-routing boundary below.
            if (parentFirst && !c.startsWith("android/")) indexParent(c);
            if (!DECLARED.containsKey(c)) {
                if (c.startsWith("android/")) return true;
                indexParent(c);
                if (!DECLARED.containsKey(c)) return false;
            }
            if (DECLARED.get(c).contains(name + desc)) return false;
            c = SUPER.get(c);
        }
        return false;
    }

    /** The signature if ASM can parse it, else null. */
    private static String wellFormed(String signature) {
        if (signature == null) return null;
        try { new org.objectweb.asm.signature.SignatureReader(signature).accept(new org.objectweb.asm.signature.SignatureVisitor(Opcodes.ASM9) {}); return signature; }
        catch (RuntimeException malformed) { return null; }
    }

    private final ClassLoader parent;
    private final boolean parentFirst;
    private final java.util.Set<String> missingParents = new java.util.HashSet<>();

    /** One conversion session's metadata; never shared between applications or dynamic loaders. */
    public DenyInstrument(ClassLoader parent) { this(parent, false); }

    public DenyInstrument(ClassLoader parent, boolean parentFirst) {
        this.parent = parent;
        this.parentFirst = parentFirst;
    }

    /** Register DEX metadata without converting any method bodies. First definition wins. */
    public void addClass(String name, String superclass, java.util.Set<String> methods) {
        if (DECLARED.containsKey(name)) return;
        SUPER.put(name, superclass);
        DECLARED.put(name, java.util.Set.copyOf(methods));
        if (name.startsWith("java/") && ClassLoader.getPlatformClassLoader().getResource(name + ".class") == null)
            JAVA_OWN.add(name);
    }

    public void addClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        addClass(reader.getClassName(), reader.getSuperName(), methods(reader));
    }

    private static java.util.Set<String> methods(ClassReader reader) {
        java.util.Set<String> result = new java.util.HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                result.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }

    private void indexParent(String name) {
        if (parent == null || !missingParents.add(name)) return;
        try (InputStream input = parent.getResourceAsStream(name + ".class")) {
            if (input == null) return;
            ClassReader reader = new ClassReader(input);
            SUPER.put(name, reader.getSuperName());
            DECLARED.put(name, methods(reader));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read parent class metadata for " + name, failure);
        }
    }

    public String mappedName(String original) { return RELOCATOR.map(original); }

    /** A JVM relocated dependency can request the DEX class by its original name. */
    public String sourceName(String runtimeName) {
        if (runtimeName.startsWith(RELOCATED_TO)) {
            String original = runtimeName.substring(RELOCATED_TO.length());
            if (JAVA_OWN.contains(original)) return original;
        }
        return runtimeName;
    }

    /** Exactly the installed-jar postprocessing for one lazy class. Callers serialize per session:
     * the CLI is sequential and the dynamic loader holds its own shadow lock. */
    public byte[] transform(byte[] bytes) {
        boolean relocate = !JAVA_OWN.isEmpty();
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor target = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                               String signature, String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, desc, signature, exceptions)) {
                    @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(propertyHandle(value)); }
                    @Override public void visitInvokeDynamicInsn(String n, String d, Handle bootstrap, Object... args) {
                        for (int i = 0; i < args.length; i++) args[i] = propertyHandle(args[i]);
                        super.visitInvokeDynamicInsn(n, d, (Handle) propertyHandle(bootstrap), args);
                    }
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname, String mdesc,
                                                boolean itf) {
                        if ((opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKEVIRTUAL)
                                && environmentCall(owner, mname, mdesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "apklayer/host/GuestEnvironment", mname,
                                    opcode == Opcodes.INVOKESTATIC ? mdesc : withReceiver(owner, mdesc), false);
                            return;
                        }
                        if (opcode == Opcodes.INVOKESTATIC && propertyCall(owner, mname, mdesc)) {
                            super.visitMethodInsn(opcode, "apklayer/host/GuestSystemProperties", mname, mdesc, false);
                            return;
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/invoke/MethodHandles$Lookup")
                                && (((mname.equals("findStatic") || mname.equals("findVirtual")) && mdesc.equals("(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"))
                                || (mname.equals("bind") && mdesc.equals("(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"))
                                || (mname.equals("findSpecial") && mdesc.equals("(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"))
                                || (mname.equals("unreflectSpecial") && mdesc.equals("(Ljava/lang/reflect/Method;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"))
                                || (mname.equals("unreflect") && mdesc.equals("(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;")))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "apklayer/host/Reflect", mname,
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;" + mdesc.substring(1), false);
                            return;
                        }
                        // MultiDex.install(Context): the converter already merged every dex into one jar
                        if (opcode == Opcodes.INVOKESTATIC && mname.equals("install") && mdesc.equals("(Landroid/content/Context;)V")
                                && (owner.equals("androidx/multidex/MultiDex") || owner.equals("android/support/multidex/MultiDex"))) {
                            super.visitInsn(Opcodes.POP);
                            return;
                        }
                        // Unsafe.getUnsafe(): ART allows any caller, HotSpot only boot classes
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("sun/misc/Unsafe") && mname.equals("getUnsafe")) {
                            super.visitMethodInsn(opcode, "apklayer/host/UnsafeAccess", mname, mdesc, false);
                            return;
                        }
                        // reflective Unsafe.getUnsafe() (HiddenApiBypass and friends)
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/reflect/Method") && mname.equals("invoke")
                                && mdesc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "apklayer/host/Reflect", "invoke",
                                    "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                            return;
                        }
                        // the app's own services run in-process (apklayer.host.Services)
                        if (opcode == Opcodes.INVOKEVIRTUAL && SERVICE_CALLS.containsKey(mname + mdesc)
                                && isFrameworkContextCall(owner, mname, mdesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "apklayer/host/Services", mname,
                                    SERVICE_CALLS.get(mname + mdesc), false);
                            return;
                        }
                        // the app's System/Runtime loadLibrary/load -> apklayer.host.NativeLoader (P4)
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/System")
                                && (mname.equals("loadLibrary") || mname.equals("load"))
                                && mdesc.equals("(Ljava/lang/String;)V")) {
                            super.visitMethodInsn(opcode, "apklayer/host/NativeLoader", mname, mdesc, false);
                        } else if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Runtime")
                                && (mname.equals("loadLibrary") || mname.equals("load"))
                                && mdesc.equals("(Ljava/lang/String;)V")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "apklayer/host/NativeLoader",
                                    mname.equals("load") ? "runtimeLoad" : "runtimeLoadLibrary",
                                    "(Ljava/lang/Runtime;Ljava/lang/String;)V", false);
                        } else {
                            super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                        }
                    }
                };
            }

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                super.visitAnnotation(ANNO, true).visitEnd();
            }
        };
        ClassVisitor chain = relocate ? new ClassRemapper(target, RELOCATOR) : target;
        // Dalvik lets a static final field be written outside <clinit> (Scala's Predef$.MODULE$ is set by the constructor); the JVM, for class files of version 53+, does not (nor a final instance field outside <init>): the final goes
        java.util.Set<String> writtenLate = new java.util.HashSet<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            String self;
            @Override public void visit(int v, int a, String n, String sg, String sn, String[] in) { self = n; }
            @Override public org.objectweb.asm.MethodVisitor visitMethod(int a, String n, String d, String sg, String[] ex) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
                        if (owner.equals(self) && ((op == Opcodes.PUTSTATIC && !n.equals("<clinit>")) || (op == Opcodes.PUTFIELD && !n.equals("<init>")))) writtenLate.add(fname);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, chain) {  // the converter writes a few malformed generic signatures: dropped (the remapper would throw on them)
            @Override public void visit(int v, int a, String n, String sg, String sn, String[] in) { super.visit(v, a, n, wellFormed(sg), sn, in); }
            @Override public org.objectweb.asm.FieldVisitor visitField(int a, String n, String d, String sg, Object value) { if (writtenLate.contains(n)) a &= ~Opcodes.ACC_FINAL; return super.visitField(a, n, d, wellFormed(sg), value); }
            @Override public org.objectweb.asm.MethodVisitor visitMethod(int a, String n, String d, String sg, String[] ex) { return super.visitMethod(a, n, d, wellFormed(sg), ex); }
        }, 0);
        return cw.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        DenyInstrument transformer = new DenyInstrument(DenyInstrument.class.getClassLoader());
        try (JarFile scan = new JarFile(args[0])) {
            for (JarEntry entry : Collections.list(scan.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream input = scan.getInputStream(entry)) { transformer.addClass(input.readAllBytes()); }
            }
        }
        try (JarFile in = new JarFile(args[0]);
             JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])))) {
            int count = 0;
            for (JarEntry entry : Collections.list(in.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes;
                try (InputStream input = in.getInputStream(entry)) { bytes = input.readAllBytes(); }
                String name = entry.getName();
                name = transformer.mappedName(name.substring(0, name.length() - 6)) + ".class";
                out.putNextEntry(new JarEntry(name));
                out.write(transformer.transform(bytes));
                out.closeEntry();
                count++;
            }
            System.out.println("[apklayer] DoNotInstrument stamped on " + count + " classes"
                    + (transformer.JAVA_OWN.isEmpty() ? "" : " (" + transformer.JAVA_OWN.size() + " java.* classes relocated)"));
        }
    }
}
