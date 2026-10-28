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
 * Android's ArrayList iterator ends after the list's size at its creation ({@code limit}), so a loop may remove the element it is on (DocumentsUI does, in the last one); OpenJDK's throws.
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
        arrayListIterator(Path.of(args[0], "java/util/ArrayList$Itr.class"));
        limitAdjusted("java/util/ArrayList$Itr", "remove", -1, Path.of(args[0], "java/util/ArrayList$Itr.class"));  // Android: remove() lowers limit
        limitAdjusted("java/util/ArrayList$ListItr", "add", 1, Path.of(args[0], "java/util/ArrayList$ListItr.class"));  // and ListIterator.add() raises it
    }

    /** Before every return of {@code method}: {@code limit += delta} (the class read from the patch dir when it is already there, else from the JDK). */
    private static void limitAdjusted(String owner, String method, int delta, Path out) throws Exception {
        byte[] original;
        if (Files.exists(out)) original = Files.readAllBytes(out);
        else try (InputStream in = Object.class.getResourceAsStream("/" + owner + ".class")) { original = in.readAllBytes(); }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals(method)) return next;
                return new MethodVisitor(Opcodes.ASM9, next) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitInsn(Opcodes.DUP); mv.visitFieldInsn(Opcodes.GETFIELD, "java/util/ArrayList$Itr", "limit", "I");
                            mv.visitInsn(delta > 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_M1); mv.visitInsn(Opcodes.IADD); mv.visitFieldInsn(Opcodes.PUTFIELD, "java/util/ArrayList$Itr", "limit", "I");
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }

    private static void arrayListIterator(Path out) throws Exception {
        byte[] original;
        try (InputStream in = Object.class.getResourceAsStream("/java/util/ArrayList$Itr.class")) { original = in.readAllBytes(); }
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public void visitEnd() { cv.visitField(Opcodes.ACC_PROTECTED, "limit", "I", null, null).visitEnd(); super.visitEnd(); }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("hasNext") && descriptor.equals("()Z")) { // cursor < limit
                    next.visitCode();
                    org.objectweb.asm.Label no = new org.objectweb.asm.Label();
                    next.visitVarInsn(Opcodes.ALOAD, 0); next.visitFieldInsn(Opcodes.GETFIELD, "java/util/ArrayList$Itr", "cursor", "I");
                    next.visitVarInsn(Opcodes.ALOAD, 0); next.visitFieldInsn(Opcodes.GETFIELD, "java/util/ArrayList$Itr", "limit", "I");
                    next.visitJumpInsn(Opcodes.IF_ICMPGE, no);
                    next.visitInsn(Opcodes.ICONST_1); next.visitInsn(Opcodes.IRETURN);
                    next.visitLabel(no); next.visitInsn(Opcodes.ICONST_0); next.visitInsn(Opcodes.IRETURN);
                    next.visitMaxs(2, 1); next.visitEnd();
                    return null;
                }
                if (name.equals("<init>")) return new MethodVisitor(Opcodes.ASM9, next) { // after the super call: limit = outer.size
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        super.visitMethodInsn(opcode, owner, method, desc, itf);
                        if (opcode == Opcodes.INVOKESPECIAL && owner.equals("java/lang/Object") && method.equals("<init>")) {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitFieldInsn(Opcodes.GETFIELD, "java/util/ArrayList$Itr", "this$0", "Ljava/util/ArrayList;");
                            mv.visitFieldInsn(Opcodes.GETFIELD, "java/util/ArrayList", "size", "I");
                            mv.visitFieldInsn(Opcodes.PUTFIELD, "java/util/ArrayList$Itr", "limit", "I");
                        }
                    }
                };
                return next;
            }
        }, 0);
        Files.createDirectories(out.getParent());
        Files.write(out, writer.toByteArray());
    }
}
