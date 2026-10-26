package apklayer.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
 * Also relocates an app's bundled Guava into a private package: an older or shrunk copy would
 * otherwise mix with the host's Guava 33 in the same class loader (NoSuchMethodError).
 *
 * Usage: java -cp <asm>:... apklayer.tools.DenyInstrument <in.jar> <out.jar>
 */
public final class DenyInstrument {
    private static final String ANNO = "Lorg/robolectric/annotation/internal/DoNotInstrument;";

    /** Libraries the host itself runs (Guava 33) whose app copy is relocated. */
    private static final String[][] LIBRARIES = {
        {"com/google/common/", "com/google/thirdparty/"}};
    // not BouncyCastle: it registers its providers by class-name strings, which relocation cannot follow
    private static final java.util.List<String> RELOCATED = new java.util.ArrayList<>(); // active prefixes
    private static final String RELOCATED_TO = "apklayer/relocated/";

    /** Maps the bundled Guava's internal names into the private package. */
    private static final Remapper RELOCATOR = new Remapper() {
        @Override
        public String map(String name) {
            for (String prefix : RELOCATED) {
                if (name.startsWith(prefix)) return RELOCATED_TO + name;
            }
            return name;
        }
    };

    public static void main(String[] args) throws Exception {
        try (JarFile probe = new JarFile(args[0])) {
            for (String[] library : LIBRARIES) {
                boolean bundled = Collections.list(probe.entries()).stream()
                        .anyMatch(en -> en.getName().startsWith(library[0]));
                if (bundled) RELOCATED.addAll(java.util.Arrays.asList(library));
            }
        }
        boolean relocate = !RELOCATED.isEmpty();
        try (JarFile in = new JarFile(args[0]);
             JarOutputStream out = new JarOutputStream(
                     new BufferedOutputStream(new FileOutputStream(args[1])))) {
            int count = 0;
            for (JarEntry e : Collections.list(in.entries())) {
                if (!e.getName().endsWith(".class")) continue;
                byte[] bytes;
                try (InputStream is = in.getInputStream(e)) {
                    bytes = is.readAllBytes();
                }
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new ClassWriter(0);
                ClassVisitor target = new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                                       String signature, String[] exceptions) {
                        return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9,
                                super.visitMethod(access, name, desc, signature, exceptions)) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mname, String mdesc,
                                                        boolean itf) {
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
                cr.accept(relocate ? new ClassRemapper(target, RELOCATOR) : target, 0);
                String entryName = e.getName();
                if (relocate) entryName = RELOCATOR.map(entryName.substring(0, entryName.length() - 6)) + ".class";
                JarEntry ne = new JarEntry(entryName);
                out.putNextEntry(ne);
                out.write(cw.toByteArray());
                out.closeEntry();
                count++;
            }
            System.out.println("[apklayer] DoNotInstrument stamped on " + count + " classes"
                    + (relocate ? " (relocated " + RELOCATED + ")" : ""));
        }
    }
}
