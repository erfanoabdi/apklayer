package apklayer.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the person sees and decides about the VPN (HubVpn): the consent question (Android's ConfirmDialog, "Allow X to set up a VPN connection?"),
 * remembered per app in {@code <state>/vpn/consent}, the always-on setting, and the ongoing "VPN is active" desktop notification with a Disconnect
 * action (what Android shows under the key icon).
 */
final class HubVpnUi {
    private final Path consentFile, alwaysOnFile;
    private final Runnable disconnect;
    private final DesktopNotifications desktop = new DesktopNotifications(new DesktopNotifications.Listener() {
        @Override public void action(int id, String key) { if (id == popup && key.equals("disconnect")) disconnect.run(); }
        @Override public void closed(int id, int reason) { if (id == popup) popup = 0; }
    });
    private volatile int popup;

    HubVpnUi(Path dir, Runnable disconnect) {
        this.consentFile = dir.resolve("consent");
        this.alwaysOnFile = dir.resolve("always-on");
        this.disconnect = disconnect;
    }

    private synchronized Set<String> consented() {
        try { return new LinkedHashSet<>(Files.readAllLines(consentFile)); } catch (IOException none) { return new LinkedHashSet<>(); }
    }

    boolean authorized(String pkg) { return consented().contains(pkg); }

    synchronized void authorize(String pkg) {
        Set<String> all = consented();
        if (!all.add(pkg)) return;
        try { Files.createDirectories(consentFile.getParent()); Files.write(consentFile, all, StandardCharsets.UTF_8); }
        catch (IOException failure) { System.out.println("[apklayer-hub] vpn: cannot remember the consent of " + pkg + ": " + failure); }
    }

    /** Asks the person; without a shell the answer is no. */
    boolean ask(String pkg) {
        String label = HubInstaller.labelOf(pkg, pkg);
        return HubInstallDialog.ask("Allow " + label + " to set up a VPN connection?",
                label + " (" + pkg + ") wants to set up a VPN connection. While it runs, the network traffic of this whole computer (not only of Android apps) goes through "
                        + label + ", which can monitor and change it. Only accept if you trust it. If NetworkManager does not allow the connection to be made, the "
                        + "computer's password is asked next. A notification shows while the VPN is active, with a button to disconnect.", "OK", "Cancel");
    }

    void show(String owner, String session) {
        popup = desktop.show("Android System", popup, "", "VPN is active", "Session: " + session + " (" + HubInstaller.labelOf(owner, owner) + ")",
                List.of("disconnect", "Disconnect"), java.util.Map.of());
    }

    void hide() {
        int id = popup;
        popup = 0;
        if (id != 0) desktop.close(id);
    }

    // ---- always-on: remembered, the flags accepted (the app reconnects itself; the hub does not start it) ----

    synchronized boolean alwaysOn(String pkg, boolean lockdown) {
        try {
            if (pkg == null) Files.deleteIfExists(alwaysOnFile);
            else { Files.createDirectories(alwaysOnFile.getParent()); Files.writeString(alwaysOnFile, pkg + "\n" + lockdown + "\n"); }
            return true;
        } catch (IOException failure) { return false; }
    }

    synchronized String alwaysOnPackage() {
        try { return Files.readAllLines(alwaysOnFile).get(0); } catch (IOException | IndexOutOfBoundsException none) { return null; }
    }

    synchronized boolean lockdown() {
        try { return Files.readAllLines(alwaysOnFile).get(1).equals("true"); } catch (IOException | IndexOutOfBoundsException none) { return false; }
    }
}
