package apklayer.host;

import android.content.Intent;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The boot broadcasts, once per start of the instance's stack (its property store and runtime directory are new each time, as a device's /run is):
 * ACTION_LOCKED_BOOT_COMPLETED, then ACTION_BOOT_COMPLETED, sent as UserController does (FLAG_RECEIVER_NO_ABORT | FLAG_RECEIVER_INCLUDE_BACKGROUND, EXTRA_USER_HANDLE,
 * permission RECEIVE_BOOT_COMPLETED required of the receiver), after the boot settings are written. Ordered, as UserController sends them (a result receiver):
 * the receivers get it one after the other, so the closed apps with a receiver are started one at a time, each after the previous one finished. The hub
 * of a stack that was already announced (it ended when idle and came back) sends nothing: the marker lives in the stack's runtime directory.
 */
final class HubBootBroadcasts {
    private HubBootBroadcasts() {}

    static void sendOnce(HubDirectory directory) {
        // only an always-on instance is a phone that boots (at login); an on-demand one starts when an app needs it, and waking every app with a boot
        // receiver (each its own JVM) for that would cost more than the one app asked for. APKLAYER_BOOT_BROADCASTS=0: not even then.
        if (!directory.alwaysOn() || "0".equals(System.getenv("APKLAYER_BOOT_BROADCASTS"))) return;
        HubBootSettings.awaitBoot();
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");  // <runtime dir>/android-root
        if (root == null || Path.of(root).getParent() == null) return;
        try { Files.createFile(Path.of(root).getParent().resolve("boot_completed")); }
        catch (FileAlreadyExistsException already) { return; }
        catch (IOException failure) { System.out.println("[apklayer-hub] boot broadcasts not sent: " + failure); return; }
        int flags = Intent.FLAG_RECEIVER_NO_ABORT | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND | Intent.FLAG_RECEIVER_OFFLOAD;
        for (String action : new String[] {Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED}) {
            System.out.println("[apklayer-hub] sending " + action);
            directory.sendSystemOrdered(new Intent(action).putExtra(Intent.EXTRA_USER_HANDLE, 0).addFlags(flags), "android.permission.RECEIVE_BOOT_COMPLETED");
        }
    }
}
