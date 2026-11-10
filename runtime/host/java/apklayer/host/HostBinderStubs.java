package apklayer.host;

import android.os.Binder;
import android.os.IInterface;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A real AIDL Stub (its onTransact unparcels each call) whose methods all go to an implementation of the interface, usually its AIDL Default with a few
 * overrides. A hand-written Stub subclass would have to implement every method of a large system interface (IPackageManager has ~250); Robolectric's own
 * binder services attach the implementation to a plain Binder, so a caller that transacts (an app's wrapper binder) got no answer. The subclass is generated
 * once per Stub (ASM, as JdkPatch uses it) and defined beside this class.
 */
final class HostBinderStubs {
    static <T extends IInterface> Binder delegating(Class<? extends Binder> stub, Class<T> iface, T impl) {
        String name = "apklayer/host/" + stub.getName().replace('.', '_').replace('$', '_') + "$Delegating", sup = Type.getInternalName(stub);
        String owner = Type.getInternalName(iface), field = Type.getDescriptor(iface);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, name, null, sup, null);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "impl", field, null, null).visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(" + field + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, sup, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, name, "impl", field);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        Set<String> done = new HashSet<>();
        for (Method m : stub.getMethods()) {  // the interface's methods the Stub leaves abstract
            String desc = Type.getMethodDescriptor(m);
            if (!Modifier.isAbstract(m.getModifiers()) || !done.add(m.getName() + desc)) continue;
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, m.getName(), desc, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, name, "impl", field);
            int slot = 1;
            for (Type arg : Type.getArgumentTypes(m)) { mv.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot); slot += arg.getSize(); }
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, m.getName(), desc, true);
            mv.visitInsn(Type.getReturnType(m).getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        try {
            return (Binder) MethodHandles.lookup().defineClass(cw.toByteArray()).getDeclaredConstructor(iface).newInstance(impl);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
