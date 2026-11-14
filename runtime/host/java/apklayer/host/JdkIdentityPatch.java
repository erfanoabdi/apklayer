package apklayer.host;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/**
 * The runtime-identity part of the java.base patch (run by {@link JdkPatch}): java.lang.ApklayerHooks and java.lang.BootClassLoader from their
 * templates (JdkHooks, JdkBootLoader), and the JDK methods that answer through the hooks: a Throwable's frames, another thread's frames,
 * Thread.getAllStackTraces, NullPointerException.getMessage, and the messages HotSpot gives the index, cast and arithmetic exceptions it throws.
 */
final class JdkIdentityPatch {
    private JdkIdentityPatch() {}

    static final String HOOKS = "java/lang/ApklayerHooks";
    private static final String FRAMES = "([Ljava/lang/StackTraceElement;)[Ljava/lang/StackTraceElement;";

    static void write(Path dir) throws Exception {
        SimpleRemapper names = new SimpleRemapper(java.util.Map.of("apklayer/host/JdkHooks", HOOKS, "apklayer/host/JdkBootLoader", "java/lang/BootClassLoader"));
        copy("apklayer/host/JdkHooks", HOOKS, names, dir);
        copy("apklayer/host/JdkBootLoader", "java/lang/BootClassLoader", names, dir);
        // a Throwable's frames, made once from its backtrace: through the hook before they are kept
        patch(dir, "java/lang/Throwable", (owner, name, desc, mv) -> name.equals("getOurStackTrace") ? new MethodVisitor(Opcodes.ASM9, mv) {
            @Override public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
                super.visitMethodInsn(op, o, n, d, itf);
                if (op == Opcodes.INVOKESTATIC && o.equals("java/lang/StackTraceElement") && n.equals("of") && d.endsWith(")[Ljava/lang/StackTraceElement;"))
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "frames", FRAMES, false);
            }
        } : mv);
        wrap(dir, "java/lang/Thread", "getAllStackTraces", "()Ljava/util/Map;", "threads", "(Ljava/util/Map;)Ljava/util/Map;");
        wrap(dir, "java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;", "frames", FRAMES);
        wrap(dir, "java/lang/NullPointerException", "getMessage", "()Ljava/lang/String;", "npe", "(Ljava/lang/String;)Ljava/lang/String;");
        message(dir, "java/lang/ArrayIndexOutOfBoundsException", JdkHooks.INDEX);
        message(dir, "java/lang/StringIndexOutOfBoundsException", JdkHooks.INDEX);
        message(dir, "java/lang/IndexOutOfBoundsException", JdkHooks.LIST);
        message(dir, "java/lang/ClassCastException", JdkHooks.CAST);
        message(dir, "java/lang/ArithmeticException", JdkHooks.ARITHMETIC);
        message(dir, "java/lang/NoSuchMethodException", JdkHooks.METHOD);
        // Class.getMethod / getDeclaredMethod ask the hidden-API policy first; getDeclaredFields filters for app callers
        patch(dir, "java/lang/Class", (owner, name, desc, mv) -> {
            if ((name.equals("getMethod") || name.equals("getDeclaredMethod")) && desc.startsWith("(Ljava/lang/String;[Ljava/lang/Class;)")) return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override public void visitCode() {
                    super.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "checkMethod", "(Ljava/lang/Class;Ljava/lang/String;)V", false);
                }
            };
            if (name.equals("getDeclaredFields") && desc.equals("()[Ljava/lang/reflect/Field;")) return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override public void visitInsn(int op) {
                    if (op == Opcodes.ARETURN) {
                        mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitInsn(Opcodes.SWAP);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "declaredFields", "(Ljava/lang/Class;[Ljava/lang/reflect/Field;)[Ljava/lang/reflect/Field;", false);
                    }
                    super.visitInsn(op);
                }
            };
            return mv;
        });
    }

    private static void copy(String template, String target, SimpleRemapper names, Path dir) throws Exception {
        byte[] bytes;
        try (InputStream in = JdkIdentityPatch.class.getResourceAsStream("/" + template + ".class")) { bytes = in.readAllBytes(); }
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassRemapper(writer, names), 0);
        Path out = dir.resolve(target + ".class");
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }

    interface Methods { MethodVisitor visit(String owner, String name, String desc, MethodVisitor next); }

    /** Rewrites {@code owner} (the copy in the patch directory when JdkPatch made one already, else the JDK's). */
    private static void patch(Path dir, String owner, Methods methods) throws Exception { patch(dir, owner, methods, null); }

    private static void patch(Path dir, String owner, Methods methods, java.util.function.Consumer<ClassVisitor> atEnd) throws Exception {
        Path out = dir.resolve(owner + ".class");
        byte[] original;
        if (Files.exists(out)) original = Files.readAllBytes(out);
        else try (InputStream in = Object.class.getResourceAsStream("/" + owner + ".class")) { original = in.readAllBytes(); }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return methods.visit(owner, name, desc, super.visitMethod(access, name, desc, signature, exceptions));
            }
            @Override public void visitEnd() { if (atEnd != null) atEnd.accept(cv); super.visitEnd(); }
        }, 0);
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }

    /** {@code name} keeps its body as apklayer$name; a new {@code name} returns {@code ApklayerHooks.hook(apklayer$name(...))}. */
    private static void wrap(Path dir, String owner, String name, String desc, String hook, String hookDesc) throws Exception {
        int[] access = {0};
        Path out = dir.resolve(owner + ".class");
        byte[] original;
        if (Files.exists(out)) original = Files.readAllBytes(out);
        else try (InputStream in = Object.class.getResourceAsStream("/" + owner + ".class")) { original = in.readAllBytes(); }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int acc, String n, String d, String signature, String[] exceptions) {
                if (!n.equals(name) || !d.equals(desc)) return super.visitMethod(acc, n, d, signature, exceptions);
                access[0] = acc;
                return super.visitMethod((acc & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE, "apklayer$" + n, d, signature, exceptions);
            }
            @Override public void visitEnd() {
                if (access[0] == 0) throw new IllegalStateException(owner + "." + name + desc + " not found");
                boolean isStatic = (access[0] & Opcodes.ACC_STATIC) != 0;
                MethodVisitor m = cv.visitMethod(access[0] & ~(Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_NATIVE), name, desc, null, null);
                m.visitCode();
                if (!isStatic) m.visitVarInsn(Opcodes.ALOAD, 0);
                m.visitMethodInsn(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, owner, "apklayer$" + name, desc, false);
                m.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, hook, hookDesc, false);
                Type ret = Type.getReturnType(desc);
                if (!ret.equals(Type.getReturnType(hookDesc))) m.visitTypeInsn(Opcodes.CHECKCAST, ret.getInternalName());
                m.visitInsn(Opcodes.ARETURN);
                m.visitMaxs(0, 0);
                m.visitEnd();
                super.visitEnd();
            }
        }, 0);
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }

    /** The exception's (String) constructor first passes its message through ApklayerHooks.message(message, kind). */
    private static void message(Path dir, String owner, int kind) throws Exception {
        patch(dir, owner, (o, name, desc, mv) -> !name.equals("<init>") || !desc.equals("(Ljava/lang/String;)V") ? mv : new MethodVisitor(Opcodes.ASM9, mv) {
            @Override public void visitCode() {
                super.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitLdcInsn(kind);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "message", "(Ljava/lang/String;I)Ljava/lang/String;", false);
                mv.visitVarInsn(Opcodes.ASTORE, 1);
            }
        });
    }
}
