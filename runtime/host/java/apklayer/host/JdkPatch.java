package apklayer.host;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generator ({@code JdkPatch outDir}) for the classes of java.base that Android's native libraries expect to be libcore's: the JVM runs with
 * {@code --patch-module java.base=outDir} (bin/apklayer). Chromium's JNI looks up {@code FileDescriptor.getOwnerId$()} and {@code getInt$()}; OpenJDK's FileDescriptor has neither.
 */
public final class JdkPatch {
    private JdkPatch() {}

    public static void main(String[] args) throws Exception {
        byte[] original;
        try (InputStream in = Object.class.getResourceAsStream("/java/io/FileDescriptor.class")) { original = in.readAllBytes(); }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public void visitEnd() {
                MethodVisitor owner = cv.visitMethod(Opcodes.ACC_PUBLIC, "getOwnerId$", "()J", null, null); // fdsan owner tags: never owned
                owner.visitCode(); owner.visitInsn(Opcodes.LCONST_0); owner.visitInsn(Opcodes.LRETURN); owner.visitMaxs(2, 1); owner.visitEnd();
                MethodVisitor get = cv.visitMethod(Opcodes.ACC_PUBLIC, "getInt$", "()I", null, null);
                get.visitCode(); get.visitVarInsn(Opcodes.ALOAD, 0); get.visitFieldInsn(Opcodes.GETFIELD, "java/io/FileDescriptor", "fd", "I"); get.visitInsn(Opcodes.IRETURN); get.visitMaxs(1, 1); get.visitEnd();
                MethodVisitor set = cv.visitMethod(Opcodes.ACC_PUBLIC, "setInt$", "(I)V", null, null);
                set.visitCode(); set.visitVarInsn(Opcodes.ALOAD, 0); set.visitVarInsn(Opcodes.ILOAD, 1); set.visitFieldInsn(Opcodes.PUTFIELD, "java/io/FileDescriptor", "fd", "I");
                set.visitInsn(Opcodes.RETURN); set.visitMaxs(2, 2); set.visitEnd();
                super.visitEnd();
            }
        }, 0);
        Path out = Path.of(args[0], "java/io/FileDescriptor.class");
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }
}
