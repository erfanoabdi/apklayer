package hello.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An installer as stores are written (tests/vm/installer.py): PackageInstaller sessions of the payload APKs in its assets, the status through a
 * PendingIntent, the confirmation intent started when the status asks for it. Prints "[inst] PASS|FAIL <step>"; the test answers the dialogs.
 */
public class Main extends Activity {
    private static final String STATUS = "hello.installer.STATUS", PAYLOAD = "hello.payload";
    private final LinkedBlockingQueue<Intent> statuses = new LinkedBlockingQueue<>(), packages = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> reports = new LinkedBlockingQueue<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerReceiver(new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { statuses.add(i); } }, new IntentFilter(STATUS), RECEIVER_NOT_EXPORTED);
        IntentFilter changes = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REPLACED); changes.addAction(Intent.ACTION_PACKAGE_REMOVED); changes.addDataScheme("package");
        registerReceiver(new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) {
            System.out.println("[inst] broadcast " + i.getAction() + " " + i.getData() + " replacing=" + i.getBooleanExtra(Intent.EXTRA_REPLACING, false));
            packages.add(i);
        } }, changes, RECEIVER_EXPORTED);
        registerReceiver(new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { reports.add(String.valueOf(i.getStringExtra("report"))); } },
                new IntentFilter("hello.payload.REPORT"), RECEIVER_EXPORTED);
        new Thread(this::steps, "installer-steps").start();
    }

    private void steps() {
        try {
            System.out.println("[inst] canRequestPackageInstalls=" + getPackageManager().canRequestPackageInstalls());
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            int abandoned = installer.createSession(params());
            try (PackageInstaller.Session s = installer.openSession(abandoned)) { write(s, "hello-payload.apk"); s.abandon(); }
            check("abandon", installer.getSessionInfo(abandoned) == null, "the session is still there");
            int status = commit("hello-payload.apk", "hello-payload-split_xxhdpi.apk");
            check("cancel", status == PackageInstaller.STATUS_FAILURE_ABORTED, "status " + status);
            status = commit("hello-payload.apk", "hello-payload-split_xxhdpi.apk");
            PackageInfo info = status == 0 ? getPackageManager().getPackageInfo(PAYLOAD, 0) : null;
            check("install", info != null && info.getLongVersionCode() == 1 && info.splitNames != null && java.util.Arrays.asList(info.splitNames).contains("config.xxhdpi")
                    && broadcast(Intent.ACTION_PACKAGE_ADDED), "status " + status + " info " + (info == null ? null : info.getLongVersionCode() + " " + java.util.Arrays.toString(info.splitNames)));
            String report = launchPayload();
            check("split resource", report.contains("version=1 split=from split splits=[config.xxhdpi]"), report);
            status = commit("hello-payload2.apk");
            info = status == 0 ? getPackageManager().getPackageInfo(PAYLOAD, 0) : null;
            check("update", info != null && info.getLongVersionCode() == 2 && broadcast(Intent.ACTION_PACKAGE_REPLACED), "status " + status + " version " + (info == null ? null : info.getLongVersionCode()));
            report = launchPayload();
            check("data kept", report.contains("version=2 split=no-id splits=null data=v1"), report);
            status = commit("hello-payload-other.apk");
            check("mismatch", status == PackageInstaller.STATUS_FAILURE_CONFLICT && getPackageManager().getPackageInfo(PAYLOAD, 0).getLongVersionCode() == 2, "status " + status);
            status = commit("hello-payload.apk", "hello-payload-split_xxhdpi.apk");
            check("downgrade", status == PackageInstaller.STATUS_FAILURE_INVALID, "status " + status);
            installer.uninstall(PAYLOAD, senderOf(0x7fff));
            status = await();
            boolean gone;
            try { getPackageManager().getPackageInfo(PAYLOAD, 0); gone = false; } catch (PackageManager.NameNotFoundException removed) { gone = true; }
            check("uninstall", status == 0 && gone && broadcast(Intent.ACTION_PACKAGE_REMOVED), "status " + status + " gone " + gone);
            check("source allowed", getPackageManager().canRequestPackageInstalls(), "canRequestPackageInstalls is false after the person allowed it");
            // the legacy intents: an APK to view (the installer's activity is the confirmation), then the package to delete
            java.io.File apk = new java.io.File(getCacheDir(), "legacy.apk");
            try (InputStream in = getAssets().open("hello-payload2.apk")) { java.nio.file.Files.copy(in, apk.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.fromFile(apk), "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            check("view apk", broadcast(Intent.ACTION_PACKAGE_ADDED), "no PACKAGE_ADDED");
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_DELETE, android.net.Uri.fromParts("package", PAYLOAD, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            check("delete intent", broadcast(Intent.ACTION_PACKAGE_REMOVED), "no PACKAGE_REMOVED");
        } catch (Exception failure) {
            System.out.println("[inst] FAIL " + failure);
            failure.printStackTrace(System.out);
        }
        System.out.println("[inst] DONE");
    }

    private static PackageInstaller.SessionParams params() {
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(PAYLOAD);
        return p;
    }

    private void write(PackageInstaller.Session session, String asset) throws Exception {
        try (InputStream in = getAssets().open(asset); OutputStream out = session.openWrite(asset, 0, -1)) { in.transferTo(out); session.fsync(out); }
    }

    /** A session with these assets, committed: its final status (a confirmation it asks for is started on the way). */
    private int commit(String... assets) throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        int id = installer.createSession(params());
        try (PackageInstaller.Session session = installer.openSession(id)) {
            for (String asset : assets) write(session, asset);
            session.commit(sender(id).getIntentSender());
        }
        return await();
    }

    private PendingIntent sender(int code) {
        return PendingIntent.getBroadcast(this, code, new Intent(STATUS).setPackage(getPackageName()), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private android.content.IntentSender senderOf(int code) { return sender(code).getIntentSender(); }

    private int await() throws InterruptedException {
        for (;;) {
            Intent result = statuses.poll(150, TimeUnit.SECONDS);
            if (result == null) return Integer.MIN_VALUE;
            int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
            System.out.println("[inst] status " + status + " " + result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return status;
            Intent confirm = result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            runOnUiThread(() -> startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
    }

    private boolean broadcast(String action) throws InterruptedException {
        for (Intent i; (i = packages.poll(30, TimeUnit.SECONDS)) != null; ) if (action.equals(i.getAction()) && PAYLOAD.equals(i.getData().getSchemeSpecificPart())) return true;
        return false;
    }

    /** Starts the installed payload and returns what it reports about itself. */
    private String launchPayload() throws InterruptedException {
        Intent launch = getPackageManager().getLaunchIntentForPackage(PAYLOAD);
        System.out.println("[inst] launching the payload: " + launch);
        if (launch == null) return "no launch intent";
        reports.clear();
        runOnUiThread(() -> startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        String report = reports.poll(120, TimeUnit.SECONDS);
        System.out.println("[inst] payload: " + report);
        return String.valueOf(report);
    }

    private static void check(String step, boolean ok, String detail) { System.out.println("[inst] " + (ok ? "PASS " : "FAIL ") + step + (ok ? "" : ": " + detail)); }
}
