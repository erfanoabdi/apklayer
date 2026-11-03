package apklayer.host;

import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageParser;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The instance's package installer, the part PackageInstallerService and the PackageInstaller app play on a phone. A committed session's APKs come
 * here (the app's PackageInstaller, ShadowHostPackageInstaller), are installed by the host's own `apklayer install` (conversion, verification,
 * signature and version checks: cli/apps.cpp), then every running app's package manager learns the change and PACKAGE_ADDED/REPLACED/REMOVED go out.
 * As on Android: an installer that does not hold INSTALL_PACKAGES / DELETE_PACKAGES never installs or removes silently. Its commit answers
 * STATUS_PENDING_USER_ACTION with a confirmation intent; when the installer starts it the hub asks the person (HubInstallDialog), first whether this
 * installer may install apps at all (the per-source "install unknown apps" setting, kept in the instance), then about the package itself.
 */
final class HubInstaller {
    /** Extra of the confirmation intent: which pending request the installer's startActivity confirms. */
    static final String CONFIRM = "apklayer.installer.confirm";
    private static final String INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES", DELETE_PACKAGES = "android.permission.DELETE_PACKAGES",
            REQUEST_INSTALL = "android.permission.REQUEST_INSTALL_PACKAGES", REQUEST_DELETE = "android.permission.REQUEST_DELETE_PACKAGES";

    private enum Kind { INSTALL, PREAPPROVE, UNINSTALL }
    private record Request(Kind kind, String installer, int sessionId, String pkg, String label, Path staged, IBinder status) {}

    private final HubDirectory directory;
    private final HubPackages packages;
    private final HubPendingIntents pendingIntents;
    private final Path staging, sources;
    private final Map<String, Request> pending = new HashMap<>();
    /** Sessions whose installer had the person approve the package beforehand (requestUserPreapproval): "installer#session" -> package. */
    private final Map<String, String> preapproved = new HashMap<>();
    private final ExecutorService queue = Executors.newSingleThreadExecutor(run -> { Thread t = new Thread(run, "hub-installer"); t.setDaemon(true); return t; });
    private final AtomicInteger busy = new AtomicInteger(), tokens = new AtomicInteger();

    HubInstaller(HubDirectory directory, HubPackages packages, HubPendingIntents pendingIntents, Path instanceData) {
        this.directory = directory; this.packages = packages; this.pendingIntents = pendingIntents;
        staging = instanceData.resolve("install-staging"); sources = instanceData.resolve("install-sources");
    }

    /** An install or a question is in progress (the hub stays up for it). */
    boolean busy() { return busy.get() > 0; }

    /** A committed session (or a legacy install intent, {@code now}: the installer's activity is the confirmation): the APKs are copied out of the app's files. */
    void commit(HubDirectory.Peer caller, int sessionId, String expected, String label, List<String> names, List<ParcelFileDescriptor> files, IBinder status, boolean now) throws IOException {
        Path dir = Files.createDirectories(staging.resolve(caller.name() + "-" + sessionId + "-" + tokens.incrementAndGet()));
        for (int i = 0; i < files.size(); i++) {
            try (var in = new ParcelFileDescriptor.AutoCloseInputStream(files.get(i))) {
                Files.copy(in, dir.resolve(i + "-" + names.get(i).replaceAll("[^A-Za-z0-9_.-]", "_") + ".apk"));
            }
        }
        String pkg = null;
        try (var listing = Files.list(dir)) {
            for (Path apk : listing.toList()) {
                PackageParser.ApkLite lite = PackageParser.parseApkLite(apk.toFile(), 0);
                if (lite.splitName == null) pkg = lite.packageName;
            }
        } catch (PackageParser.PackageParserException | RuntimeException unreadable) { finish(dir, status, sessionId, null, PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_PARSE_FAILED: " + unreadable.getMessage()); return; }
        if (pkg == null) { finish(dir, status, sessionId, null, PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_INVALID_APK: no base APK"); return; }
        if (expected != null && !expected.equals(pkg)) { finish(dir, status, sessionId, pkg, PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_PACKAGE_CHANGED: the session is for " + expected); return; }
        String denied = mayRequest(caller.name(), REQUEST_INSTALL, 26);
        if (denied != null) { finish(dir, status, sessionId, pkg, PackageInstaller.STATUS_FAILURE, denied); return; }
        Request request = new Request(Kind.INSTALL, caller.name(), sessionId, pkg, label != null ? label : labelOf(pkg, pkg), dir, status);
        String approved;
        synchronized (pending) { approved = preapproved.remove(caller.name() + "#" + sessionId); }
        boolean silent = packages.holds(caller.name(), INSTALL_PACKAGES) || pkg.equals(approved);
        System.out.println("[apklayer-hub] install of " + pkg + " committed by " + caller.name() + " (session " + sessionId + ", " + files.size() + " APKs" + (silent ? ", no question needed" : "") + ")");
        if (silent) submit(() -> install(request));
        else if (now) submit(() -> confirmed(request));
        else askLater(request);
    }

    /** requestUserPreapproval: the person approves the package before the installer downloads it; the commit then needs no question. */
    void preapprove(HubDirectory.Peer caller, int sessionId, String pkg, String label, IBinder status) {
        String denied = mayRequest(caller.name(), REQUEST_INSTALL, 26);
        if (denied != null) { send(status, sessionId, pkg, PackageInstaller.STATUS_FAILURE, denied, null); return; }
        askLater(new Request(Kind.PREAPPROVE, caller.name(), sessionId, pkg, label != null ? label : labelOf(pkg, pkg), null, status));
    }

    void uninstall(HubDirectory.Peer caller, String pkg, IBinder status, boolean now) {
        if (packages.get(pkg) == null) { send(status, 0, pkg, PackageInstaller.STATUS_FAILURE, "DELETE_FAILED_INTERNAL_ERROR: " + pkg + " is not installed", null); return; }
        if (packages.system(pkg)) { send(status, 0, pkg, PackageInstaller.STATUS_FAILURE_BLOCKED, "DELETE_FAILED_SYSTEM_APP: " + pkg + " is part of the system", null); return; }
        String denied = mayRequest(caller.name(), REQUEST_DELETE, 28);
        if (denied != null) { send(status, 0, pkg, PackageInstaller.STATUS_FAILURE, denied, null); return; }
        Request request = new Request(Kind.UNINSTALL, caller.name(), 0, pkg, labelOf(pkg, pkg), null, status);
        if (packages.holds(caller.name(), DELETE_PACKAGES)) submit(() -> remove(request));
        else if (now) submit(() -> confirmed(request));
        else askLater(request);
    }

    /** PackageManager.canRequestPackageInstalls: the installer asks for REQUEST_INSTALL_PACKAGES and the person allowed it as a source; {@code ask}: ask now (ACTION_MANAGE_UNKNOWN_APP_SOURCES). */
    boolean source(HubDirectory.Peer caller, boolean ask) {
        if (allowed(caller.name())) return true;
        if (!ask || mayRequest(caller.name(), REQUEST_INSTALL, 0) != null) return false;
        submit(() -> allowSource(caller.name()));
        return false;
    }

    /** startActivity of a confirmation intent this hub handed out: true when it was one (the caller's own). */
    boolean confirm(HubDirectory.Peer caller, Intent intent) {
        String token = intent.getStringExtra(CONFIRM);
        if (token == null) return false;
        Request request;
        synchronized (pending) { request = pending.get(token); if (request != null && request.installer.equals(caller.name())) pending.remove(token); else request = null; }
        if (request == null) { System.out.println("[apklayer-hub] " + caller.name() + " starts an unknown or used install confirmation"); return true; }
        submit(() -> confirmed(request));
        return true;
    }

    private void askLater(Request request) {
        String token = request.installer + "-" + tokens.incrementAndGet();
        synchronized (pending) { pending.put(token, request); }
        Intent confirmation = new Intent(request.kind == Kind.PREAPPROVE ? PackageInstaller.ACTION_CONFIRM_PRE_APPROVAL : request.kind == Kind.INSTALL ? PackageInstaller.ACTION_CONFIRM_INSTALL
                : Intent.ACTION_UNINSTALL_PACKAGE).putExtra(PackageInstaller.EXTRA_SESSION_ID, request.sessionId).putExtra(CONFIRM, token);
        System.out.println("[apklayer-hub] " + request.kind + " of " + request.pkg + " by " + request.installer + " waits for the person");
        send(request.status, request.sessionId, request.pkg, PackageInstaller.STATUS_PENDING_USER_ACTION, null, confirmation);
    }

    /** The person's answers (on the installer thread), then the work. */
    private void confirmed(Request request) {
        if (request.kind != Kind.UNINSTALL && !allowed(request.installer) && !allowSource(request.installer)) { abort(request); return; }
        boolean update = request.kind != Kind.UNINSTALL && packages.get(request.pkg) != null;
        String verb = request.kind == Kind.UNINSTALL ? "Uninstall" : update ? "Update" : "Install";
        String body = request.kind == Kind.UNINSTALL ? "Remove " + request.label + " (" + request.pkg + ") and its data?"
                : (update ? "An update of " : "") + request.label + " (" + request.pkg + ")" + (update ? "" : " will be installed") + ", requested by " + labelOf(request.installer, request.installer) + ".";
        if (!HubInstallDialog.ask(verb + " " + request.label + "?", body, verb, "Cancel")) { abort(request); return; }
        switch (request.kind) {
            case INSTALL -> install(request);
            case UNINSTALL -> remove(request);
            case PREAPPROVE -> {
                synchronized (pending) { preapproved.put(request.installer + "#" + request.sessionId, request.pkg); }
                send(request.status, request.sessionId, request.pkg, PackageInstaller.STATUS_SUCCESS, null, null);
            }
        }
    }

    private void abort(Request request) {
        delete(request.staged);
        send(request.status, request.sessionId, request.pkg, PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions", null);
    }

    /** "Install unknown apps" for this installer, as Settings asks it on Android; the answer is kept for the instance. */
    private boolean allowSource(String installer) {
        if (allowed(installer)) return true;
        String label = labelOf(installer, installer);
        if (!HubInstallDialog.ask("Allow " + label + " to install apps?", label + " (" + installer + ") will be able to install and update apps after asking you each time.", "Allow", "Cancel")) return false;
        try { Files.writeString(sources, installer + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException failure) { System.out.println("[apklayer-hub] cannot keep the install source " + installer + ": " + failure); }
        return true;
    }

    private boolean allowed(String installer) {
        if (packages.holds(installer, INSTALL_PACKAGES)) return true;
        try { return Files.readAllLines(sources).contains(installer); } catch (IOException none) { return false; }
    }

    /** Why the caller may not ask for installs/removals (Android requires the request permission from that target SDK on), or null. */
    private String mayRequest(String caller, String permission, int fromSdk) {
        PackageParser.Package installer = packages.get(caller);
        if (installer == null || packages.holds(caller, permission.equals(REQUEST_DELETE) ? DELETE_PACKAGES : INSTALL_PACKAGES)) return null;
        if (installer.applicationInfo.targetSdkVersion < fromSdk || installer.requestedPermissions.contains(permission)) return null;
        return "Installer " + caller + " does not declare " + permission;
    }

    private void install(Request request) {
        boolean update = packages.get(request.pkg) != null;
        List<String> command = new ArrayList<>(List.of(System.getProperty("apklayer.cli"), "install"));
        try (var listing = Files.list(request.staged)) { listing.sorted().forEach(apk -> command.add(apk.toString())); }
        catch (IOException failure) { send(request.status, request.sessionId, request.pkg, PackageInstaller.STATUS_FAILURE, failure.toString(), null); return; }
        if (update) directory.stopPackage(request.pkg);  // as Android kills an app it replaces: its classes are swapped under it
        int[] exit = {0};
        String output = run(command, exit);
        delete(request.staged);
        if (exit[0] != 0) {
            int status = exit[0] == 3 ? PackageInstaller.STATUS_FAILURE_CONFLICT : exit[0] == 4 ? PackageInstaller.STATUS_FAILURE_INVALID : PackageInstaller.STATUS_FAILURE;  // cli/apps.h kInstall*
            send(request.status, request.sessionId, request.pkg, status, output, null);
            return;
        }
        directory.packageChanged(request.pkg, false);
        int uid = packages.uid(request.pkg);
        if (update) {
            broadcast(Intent.ACTION_PACKAGE_REMOVED, request.pkg, uid, true);
            broadcast(Intent.ACTION_PACKAGE_ADDED, request.pkg, uid, true);
            broadcast(Intent.ACTION_PACKAGE_REPLACED, request.pkg, uid, true);
            directory.sendSystem(new Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(request.pkg), false);
        } else broadcast(Intent.ACTION_PACKAGE_ADDED, request.pkg, uid, false);
        send(request.status, request.sessionId, request.pkg, PackageInstaller.STATUS_SUCCESS, null, null);
    }

    private void remove(Request request) {
        int uid = packages.uid(request.pkg);
        directory.stopPackage(request.pkg);
        int[] exit = {0};
        String output = run(List.of(System.getProperty("apklayer.cli"), "uninstall", request.pkg), exit);
        if (exit[0] != 0) { send(request.status, 0, request.pkg, PackageInstaller.STATUS_FAILURE, output, null); return; }
        directory.packageChanged(request.pkg, true);
        broadcast(Intent.ACTION_PACKAGE_REMOVED, request.pkg, uid, false);
        directory.sendSystem(new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED, Uri.fromParts("package", request.pkg, null)).putExtra(Intent.EXTRA_UID, uid), false);  // exempt from the background limits: manifest receivers too
        send(request.status, 0, request.pkg, PackageInstaller.STATUS_SUCCESS, null, null);
    }

    /** A package broadcast as the package manager sends it; to registered receivers only (manifest receivers of apps targeting 26+ do not get these on Android). */
    private void broadcast(String action, String pkg, int uid, boolean replacing) {
        Intent intent = new Intent(action, Uri.fromParts("package", pkg, null)).putExtra(Intent.EXTRA_UID, uid).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        if (replacing) intent.putExtra(Intent.EXTRA_REPLACING, true);
        if (action.equals(Intent.ACTION_PACKAGE_REMOVED) && !replacing) intent.putExtra(Intent.EXTRA_DATA_REMOVED, true);
        directory.sendSystem(intent, false);
    }

    /** The host's own install or uninstall; its output in the instance's logs and, on failure, as the status message (its last line). */
    private static String run(List<String> command, int[] exit) {
        StringBuilder out = new StringBuilder();
        String last = "";
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                for (String line; (line = lines.readLine()) != null; ) { out.append(line).append('\n'); if (line.startsWith("apklayer: ")) last = line.substring(10); }
            }
            exit[0] = process.waitFor();
        } catch (IOException | InterruptedException failure) { exit[0] = 1; last = failure.toString(); }
        System.out.println("[apklayer-hub] " + String.join(" ", command.subList(1, Math.min(3, command.size()))) + " exited with " + exit[0] + ": " + last);
        if (exit[0] != 0) System.out.print(out);
        return last;
    }

    private void finish(Path staged, IBinder status, int sessionId, String pkg, int code, String message) {
        delete(staged);
        send(status, sessionId, pkg, code, message, null);
    }

    /** The session's status, through the installer's IntentSender (a PendingIntent of the hub), as PackageInstallerService reports it. */
    private void send(IBinder status, int sessionId, String pkg, int code, String message, Intent confirmation) {
        System.out.println("[apklayer-hub] install status " + code + " session " + sessionId + " " + pkg + (message == null ? "" : ": " + message));
        if (status == null) return;  // a legacy install intent has no status receiver
        Intent fill = new Intent().putExtra(PackageInstaller.EXTRA_STATUS, code).putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        if (pkg != null) fill.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, pkg);
        if (message != null) fill.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message);
        if (confirmation != null) fill.putExtra(Intent.EXTRA_INTENT, confirmation);
        if (pendingIntents.send(status, 0, fill, null, null) != 0) System.out.println("[apklayer-hub] the install status receiver is gone");
    }

    private void submit(Runnable work) {
        busy.incrementAndGet();
        queue.execute(() -> {
            try { work.run(); }
            catch (RuntimeException failure) { System.out.println("[apklayer-hub] installer failed: " + failure); failure.printStackTrace(System.out); }
            finally { busy.decrementAndGet(); }
        });
    }

    /** The label an installed package's launcher shows (its info.properties), or the fallback. */
    private String labelOf(String pkg, String fallback) {
        Properties info = new Properties();
        try (var in = Files.newBufferedReader(Path.of(System.getProperty("apklayer.apps"), pkg, "info.properties"))) { info.load(in); }
        catch (IOException | RuntimeException none) { return fallback; }
        return info.getProperty("label", fallback);
    }

    private static void delete(Path dir) {
        if (dir == null) return;
        try (var files = Files.walk(dir)) { for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        catch (IOException failure) { System.out.println("[apklayer-hub] cannot remove " + dir + ": " + failure); }
    }
}
