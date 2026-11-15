package hello.files;

import android.os.SharedMemory;
import android.system.OsConstants;
import java.nio.ByteBuffer;

/** libcore as apps see it: sun.misc.Cleaner resolves and cleans, and SharedMemory maps PROT_EXEC and PROT_NONE (the mapping's permissions in /proc/self/maps). Prints `[libcore] ...`. */
final class LibCore {
    private LibCore() {}

    static void print() throws Exception {
        Class<?> cleaner = Class.forName("sun.misc.Cleaner");
        boolean[] ran = {false};
        Object c = cleaner.getMethod("create", Object.class, Runnable.class).invoke(null, new Object(), (Runnable) () -> ran[0] = true);
        cleaner.getMethod("clean").invoke(c);
        System.out.println("[libcore] cleaner ran=" + ran[0]);
        try (SharedMemory shm = SharedMemory.create("apklayer-libcore", 8192)) {
            for (int prot : new int[] {OsConstants.PROT_READ | OsConstants.PROT_EXEC, OsConstants.PROT_NONE, OsConstants.PROT_READ | OsConstants.PROT_WRITE}) {
                ByteBuffer b = shm.map(prot, 0, 8192);
                System.out.println("[libcore] shm prot=" + prot + " perms=" + perms());
                SharedMemory.unmap(b);
            }
        }
        System.out.println("[libcore] DONE");
    }

    private static String perms() throws java.io.IOException {
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/maps"))) if (line.contains("memfd:apklayer-libcore")) return line.split(" ")[1];
        return "none";
    }
}
