package apklayer.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

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
 * Usage: java -cp <asm>:... apklayer.tools.DenyInstrument <in.jar> <out.jar>
 */
public final class DenyInstrument {
    private static final String ANNO = "Lorg/robolectric/annotation/internal/DoNotInstrument;";

    public static void main(String[] args) throws Exception {
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
                cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public void visit(int version, int access, String name, String signature,
                                      String superName, String[] interfaces) {
                        super.visit(version, access, name, signature, superName, interfaces);
                        super.visitAnnotation(ANNO, true).visitEnd();
                    }
                }, 0);
                JarEntry ne = new JarEntry(e.getName());
                out.putNextEntry(ne);
                out.write(cw.toByteArray());
                out.closeEntry();
                count++;
            }
            System.out.println("[apklayer] DoNotInstrument stamped on " + count + " classes");
        }
    }
}
