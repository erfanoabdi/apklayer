// One cross-app start, on disk: the Parcel equivalent of the Intent that
// ActivityTaskManager would carry between processes on a device.
//
// docs/cross-app-launch-and-implicit-start-design_2026-09-26.md sections 4 and 6.
// A SENDER (CrossAppLauncher, in the calling app's runtime) writes one file per
// start into the TARGET bundle's inbox; the target's runtime (IntentInbox)
// reads it. The envelope is DATA only: nothing in it grants a permission or a
// result, and the receiver re-checks every component rule itself.
//
// Format (android.os.Parcel, marshall()):
//   int magic 0x4D415849 ('MAXI') | int version 1 | long createdAtMillis
//   String senderPackage | String targetPackage | String targetClass
//   int requestCode (always -1: results do not cross) | Intent (writeToParcel)
// Published as `.<name>.tmp` then rename(2)d to `<epochMillis>-<nanoTime>-<rand>.intent`
// (mode 0600), so a reader never sees a partial file, names sort FIFO, and two
// senders cannot collide.
package com.macapk.loader;

import android.content.Intent;
import android.os.Parcel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

final class IntentEnvelope {
    static final int MAGIC = 0x4D415849;
    static final int VERSION = 1;
    /** BINDER_VM_SIZE at a 4 KiB page (1 MiB - 8 KiB): past it a device's start
     *  fails with TransactionTooLargeException (Instrumentation.java:1879-1880). */
    static final int MAX_BYTES = 1_040_384;
    static final String SUFFIX = ".intent";

    final long createdAtMillis;
    final String senderPackage, targetPackage, targetClass;
    final int requestCode;
    final Intent intent;

    private IntentEnvelope(long createdAtMillis, String senderPackage, String targetPackage,
                           String targetClass, int requestCode, Intent intent) {
        this.createdAtMillis = createdAtMillis;
        this.senderPackage = senderPackage;
        this.targetPackage = targetPackage;
        this.targetClass = targetClass;
        this.requestCode = requestCode;
        this.intent = intent;
    }

    /** The inbox of an installed bundle: <bundle>/Contents/android/.macapk/intents. */
    static File inboxOfBundle(String bundlePath) {
        return new File(bundlePath, "Contents/android/.macapk/intents");
    }

    static byte[] encode(String senderPackage, String targetPackage, String targetClass,
                         Intent intent) {
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(MAGIC);
            p.writeInt(VERSION);
            p.writeLong(System.currentTimeMillis());
            p.writeString(senderPackage);
            p.writeString(targetPackage);
            p.writeString(targetClass);
            p.writeInt(-1);
            intent.writeToParcel(p, 0);
            return p.marshall();
        } finally {
            p.recycle();
        }
    }

    /** Publish atomically into `inbox`. Throws on I/O failure; the caller turns
     *  that into its own error. */
    static File publish(File inbox, byte[] bytes) throws IOException {
        if (!inbox.isDirectory() && !inbox.mkdirs() && !inbox.isDirectory()) {
            throw new IOException("cannot create inbox " + inbox);
        }
        try {
            Files.setPosixFilePermissions(inbox.toPath(), PosixFilePermissions.fromString("rwx------"));
        } catch (Throwable ignored) {
            // Not ours to chmod (another user's bundle would already have failed
            // validation); the envelope file itself is still 0600.
        }
        String name = System.currentTimeMillis() + "-" + System.nanoTime() + "-"
                + Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong())
                + SUFFIX;
        Path tmp = inbox.toPath().resolve("." + name + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
        } catch (Throwable ignored) {}
        Path fin = inbox.toPath().resolve(name);
        Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE);
        return fin.toFile();
    }

    /** Read and validate one envelope. Returns null (and says why in `why[0]`)
     *  for anything that is not a well-formed, fresh envelope of this user. The
     *  file is NOT deleted here; the inbox deletes every file it has looked at. */
    static IntentEnvelope read(File f, long ttlMs, String[] why) {
        try {
            Path path = f.toPath();
            if (Files.isSymbolicLink(path)) { why[0] = "symlink"; return null; }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { why[0] = "not a regular file"; return null; }
            String owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName();
            String me = System.getProperty("user.name");
            if (me != null && !me.equals(owner)) { why[0] = "owned by " + owner; return null; }
            long size = Files.size(path);
            if (size <= 0 || size > MAX_BYTES) { why[0] = "size " + size; return null; }
            byte[] bytes = Files.readAllBytes(path);
            Parcel p = Parcel.obtain();
            try {
                p.unmarshall(bytes, 0, bytes.length);
                p.setDataPosition(0);
                if (p.readInt() != MAGIC) { why[0] = "bad magic"; return null; }
                int ver = p.readInt();
                if (ver != VERSION) { why[0] = "version " + ver; return null; }
                long created = p.readLong();
                long age = System.currentTimeMillis() - created;
                if (age > ttlMs) { why[0] = "stale (" + age + " ms old, ttl " + ttlMs + ")"; return null; }
                if (age < -5_000L) { why[0] = "created in the future"; return null; }
                String sender = p.readString();
                String pkg = p.readString();
                String cls = p.readString();
                int rc = p.readInt();
                Intent in = Intent.CREATOR.createFromParcel(p);
                if (pkg == null || cls == null || in == null) { why[0] = "incomplete"; return null; }
                return new IntentEnvelope(created, sender, pkg, cls, rc, in);
            } finally {
                p.recycle();
            }
        } catch (Throwable t) {
            why[0] = "undecodable: " + t;
            return null;
        }
    }
}
