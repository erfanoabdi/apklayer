import java.io.FileOutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** java -cp asm.jar VerifyCases.java <out.jar>: three classes for tests/vm/install-verify.sh's bytecode cases. v/BadType returns an int as a String
 *  (VerifyError), v/BadMissing passes a v/Missing where a Number is wanted (the verifier loads v/Missing: NoClassDefFoundError), v/MissingField only has
 *  a field of the missing type (reflection fails on it, the verifier does not: not refused). */
public class VerifyCases implements Opcodes {
    static byte[] cls(String name, String desc, int[] code, String field) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        cw.visit(V17, ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (field != null) cw.visitField(ACC_PUBLIC | ACC_STATIC, "f", field, null, null).visitEnd();
        if (desc != null) {
            MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "m", desc, null, null);
            m.visitCode();
            for (int op : code) if (op >= 0) m.visitInsn(op); else m.visitVarInsn(ALOAD, 0);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(args[0]))) {
            out.putNextEntry(new ZipEntry("v/BadType.class"));
            out.write(cls("v/BadType", "()Ljava/lang/String;", new int[] {ICONST_0, ARETURN}, null));
            out.putNextEntry(new ZipEntry("v/BadMissing.class"));
            out.write(cls("v/BadMissing", "(Lv/Missing;)Ljava/lang/Number;", new int[] {-1, ARETURN}, null));
            out.putNextEntry(new ZipEntry("v/MissingField.class"));
            out.write(cls("v/MissingField", null, null, "Lv/Missing;"));
        }
    }
}
