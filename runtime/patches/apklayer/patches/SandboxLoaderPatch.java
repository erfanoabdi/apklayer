package apklayer.patches;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generator ({@code SandboxLoaderPatch outDir}), run by scripts/build-runtime-overlay.sh: Robolectric's SandboxClassLoader.loadClass cannot load array classes
 * ("[Ljava.lang.String;"), which ART's class loaders can and Chromium's JNI layer asks for. The original loadClass(String, boolean) is renamed and a new one
 * answers array names with Class.forName(name, false, this) and hands everything else to the original.
 */
public final class SandboxLoaderPatch {
    private static final String CLASS = "org/robolectric/internal/bytecode/SandboxClassLoader";
    private SandboxLoaderPatch() {}

    public static void main(String[] args) throws Exception {
        byte[] original;
        try (InputStream in = SandboxLoaderPatch.class.getResourceAsStream("/" + CLASS + ".class")) { original = in.readAllBytes(); }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals("loadClass") && descriptor.equals("(Ljava/lang/String;Z)Ljava/lang/Class;")) name = "apklayerLoadClass";
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
            @Override public void visitEnd() {
                MethodVisitor m = cv.visitMethod(Opcodes.ACC_PROTECTED, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;", null, new String[] {"java/lang/ClassNotFoundException"});
                Label plain = new Label();
                m.visitCode();
                m.visitVarInsn(Opcodes.ALOAD, 1); m.visitLdcInsn("["); m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
                m.visitJumpInsn(Opcodes.IFEQ, plain);
                m.visitVarInsn(Opcodes.ALOAD, 1); m.visitIntInsn(Opcodes.BIPUSH, '/'); m.visitIntInsn(Opcodes.BIPUSH, '.');
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(CC)Ljava/lang/String;", false);
                m.visitInsn(Opcodes.ICONST_0); m.visitVarInsn(Opcodes.ALOAD, 0);
                m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
                m.visitInsn(Opcodes.ARETURN);
                m.visitLabel(plain); m.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                m.visitVarInsn(Opcodes.ALOAD, 0); m.visitVarInsn(Opcodes.ALOAD, 1); m.visitVarInsn(Opcodes.ILOAD, 2);
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLASS, "apklayerLoadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;", false);
                m.visitInsn(Opcodes.ARETURN);
                m.visitMaxs(3, 3); m.visitEnd();
                super.visitEnd();
            }
        }, 0);
        Path out = Path.of(args[0], CLASS + ".class");
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }
}
