package apklayer.host;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageParser;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.nfc.IAppCallback;
import android.nfc.INfcTag;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NdefRecord;
import android.nfc.TechListParcel;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;

/**
 * The instance's "nfc" service (NfcService, which apklayer does not run, and the NFC HAL under it): the framework's NfcAdapter, NfcManager, Tag and the tag
 * technologies in each app run unchanged over this INfcAdapter and the tag's INfcTag (HubNfcTag). The controller is a source of tags: the mock directory
 * (HubNfcMock) or a PC/SC reader (HubNfcPcsc). A tag is delivered as NfcDispatcher does: to the app in reader mode; else to the foreground dispatch's PendingIntent;
 * else the system starts the activity for NDEF_DISCOVERED, then TECH_DISCOVERED (its tech-list), then TAG_DISCOVERED. Published only when a source exists (HostNfc).
 */
final class HubNfc extends HubNfcDefaults {
    static final String SERVICE = "nfc";
    private static final int STATE_OFF = 1, STATE_ON = 3;  // NfcAdapter.STATE_*

    private final HubDirectory directory;
    private final Path apps = Path.of(System.getProperty("apklayer.apps", "/nonexistent"));
    private volatile int state = STATE_OFF;
    private volatile HubNfcTag current;
    private IAppCallback reader;  // reader mode (guarded by this)
    private IBinder readerToken, foreground;  // the reader's death token; the foreground dispatch's PendingIntent sender
    private IntentFilter[] filters;
    private String[][] techLists;

    private final HubNfcTag none = new HubNfcTag(new byte[0], new int[0], new Bundle[0], null, command -> { throw new IOException("no tag"); }, null);

    private HubNfc(HubDirectory directory) { this.directory = directory; none.lost(); }

    /** Publishes the service and starts the source, when this host has one. */
    static void publishIfPresent(HubDirectory directory) {
        if (!HostNfc.present()) return;
        HubNfc service = new HubNfc(directory);
        if (!RealBinder.publish(SERVICE, service)) { System.out.println("[apklayer-hub] cannot publish " + SERVICE); return; }
        Path mock = HostNfc.mockDir();
        try {
            if (mock != null) { service.state = STATE_ON; HubNfcMock.watch(mock, service); }
            else HubNfcPcsc.watch(service);
        } catch (IOException failed) { System.out.println("[apklayer-hub] nfc: " + failed); }
    }

    /** The PC/SC source's reader count: the adapter is on while one is connected. */
    void readers(int count) { state = count > 0 ? STATE_ON : STATE_OFF; }

    void arrived(HubNfcTag tag) {
        current = tag;
        System.out.println("[apklayer-hub] nfc: tag " + hex(tag.tag.getId()) + " " + String.join(",", tag.tag.getTechList()));
        dispatch(tag);
    }

    void left(HubNfcTag tag) {
        tag.lost();
        if (current == tag) current = null;
        System.out.println("[apklayer-hub] nfc: tag " + hex(tag.tag.getId()) + " left");
    }

    // ---- INfcAdapter
    /** The framework asks once for a tag service of its own (NfcAdapter.sTagService); every Tag the apps hold carries its own binder, so this one has no tag. */
    @Override public INfcTag getNfcTagInterface() { return none; }
    @Override public int getState() { return state; }
    @Override public boolean enable(String pkg) { return state == STATE_ON; }  // the controller is the host's: nothing to switch on
    @Override public boolean disable(boolean persist, String pkg) { return false; }
    @Override public boolean isTagPresent() { return current != null; }
    @Override public void dispatch(android.nfc.Tag tag) {}

    @Override public synchronized void setForegroundDispatch(PendingIntent intent, IntentFilter[] filters, TechListParcel techLists) {
        foreground = intent == null ? null : intent.getTarget().asBinder();
        this.filters = filters;
        this.techLists = techLists == null ? null : techLists.getTechLists();
    }

    @Override public synchronized void setReaderMode(IBinder token, IAppCallback callback, int flags, Bundle extras, String pkg) {
        if (callback == null || flags == 0) { reader = null; readerToken = null; return; }  // flags 0 is how NfcActivityManager leaves reader mode (the callback stays)
        reader = callback; readerToken = token;
        try { token.linkToDeath(() -> { synchronized (HubNfc.this) { if (readerToken == token) { reader = null; readerToken = null; } } }, 0); }
        catch (RemoteException dead) { reader = null; readerToken = null; }
        HubNfcTag now = current;
        if (now != null) try { callback.onTagDiscovered(now.tag); } catch (RemoteException gone) { /* it died */ }  // a tag already there is found at once
    }

    // ---- dispatch (NfcDispatcher.dispatchTag)
    private void dispatch(HubNfcTag found) {
        IAppCallback callback; IBinder sender; IntentFilter[] overrideFilters; String[][] overrideTechs;
        synchronized (this) { callback = reader; sender = foreground; overrideFilters = filters; overrideTechs = techLists; }
        android.nfc.Tag tag = found.tag;
        System.out.println("[apklayer-hub] nfc: dispatching, reader mode " + (callback != null) + ", foreground dispatch " + (sender != null));
        if (callback != null) {
            try { callback.onTagDiscovered(tag); } catch (RemoteException gone) { System.out.println("[apklayer-hub] nfc: the reader app is gone"); }
            return;
        }
        NdefMessage ndef = found.ndefRead(0);
        Intent ndefIntent = ndef == null ? null : ndefIntent(tag, ndef);
        Intent techIntent = intent(NfcAdapter.ACTION_TECH_DISCOVERED, tag, ndef), tagIntent = intent(NfcAdapter.ACTION_TAG_DISCOVERED, tag, ndef);
        Set<String> techs = new HashSet<>(List.of(tag.getTechList()));
        if (sender != null) {  // NfcDispatcher.tryOverrides
            Intent pick = ndefIntent != null && filterMatch(ndefIntent, overrideFilters, overrideTechs != null) ? ndefIntent
                    : techMatch(overrideTechs, techs) ? techIntent : filterMatch(tagIntent, overrideFilters, overrideTechs != null) ? tagIntent : null;
            if (pick != null) {
                int status = directory.pendingIntents().send(sender, 0, pick, null, null);
                System.out.println("[apklayer-hub] nfc: foreground dispatch " + pick.getAction() + " status " + status);
                if (status < 0) synchronized (this) { if (foreground == sender) foreground = null; }  // its PendingIntent was canceled
                return;
            }
        }
        if (ndefIntent != null && start(ndefIntent)) return;
        for (String pkg : directory.packages().installed()) {  // TECH_DISCOVERED: an activity whose tech-list the tag fulfils
            ComponentName activity = techActivity(pkg, techs);
            if (activity != null) {
                System.out.println("[apklayer-hub] nfc: " + NfcAdapter.ACTION_TECH_DISCOVERED + " to " + activity.flattenToShortString());
                directory.startSystem(new Intent(techIntent).setComponent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
        if (!start(tagIntent)) System.out.println("[apklayer-hub] nfc: no app handles the tag");
    }

    private boolean start(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (directory.packages().activity(intent, "android") == null) return false;
        System.out.println("[apklayer-hub] nfc: " + intent.getAction() + " " + (intent.getData() != null ? intent.getData() : intent.getType()));
        directory.startSystem(intent);
        return true;
    }

    private static Intent intent(String action, android.nfc.Tag tag, NdefMessage ndef) {
        Intent intent = new Intent(action).putExtra(NfcAdapter.EXTRA_TAG, tag).putExtra(NfcAdapter.EXTRA_ID, tag.getId());
        if (ndef != null) intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[] {ndef});
        return intent;
    }

    /** NDEF_DISCOVERED: the first record's URI or MIME type is the intent's data (a text record has neither: no NDEF intent, as on a device). */
    private static Intent ndefIntent(android.nfc.Tag tag, NdefMessage ndef) {
        NdefRecord first = ndef.getRecords()[0];
        Uri uri = first.toUri();
        String mime = first.toMimeType();
        if (uri == null && mime == null) return null;
        Intent intent = intent(NfcAdapter.ACTION_NDEF_DISCOVERED, tag, ndef);
        if (uri != null) intent.setData(uri); else intent.setType(mime);
        return intent;
    }

    private static boolean filterMatch(Intent intent, IntentFilter[] filters, boolean hasTechFilter) {
        if (filters == null) return !hasTechFilter;
        for (IntentFilter filter : filters)
            if (filter != null && filter.match(intent.getAction(), intent.getType(), intent.getScheme(), intent.getData(), intent.getCategories(), "nfc") >= 0) return true;
        return false;
    }

    private static boolean techMatch(String[][] lists, Set<String> techs) {
        if (lists == null) return false;
        for (String[] list : lists) if (techs.containsAll(List.of(list))) return true;
        return false;
    }

    /** An activity of the package that takes TECH_DISCOVERED with a tech-list (its meta-data XML) the tag's technologies cover. */
    private ComponentName techActivity(String pkg, Set<String> techs) {
        PackageParser.Package parsed;
        try { parsed = directory.packages().get(pkg); } catch (RuntimeException unreadable) { return null; }
        if (parsed == null) return null;
        for (PackageParser.Activity activity : parsed.activities) {
            if (!activity.info.exported || activity.intents.stream().noneMatch(filter -> filter.hasAction(NfcAdapter.ACTION_TECH_DISCOVERED))) continue;
            int xml = activity.metaData == null ? 0 : activity.metaData.getInt(NfcAdapter.ACTION_TECH_DISCOVERED);
            if (xml == 0) continue;
            try (XmlResourceParser parser = HubUsb.xml(apps, pkg, xml)) {
                List<String> list = null;
                for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                    if (event == XmlPullParser.START_TAG && "tech-list".equals(parser.getName())) list = new ArrayList<>();
                    else if (event == XmlPullParser.START_TAG && "tech".equals(parser.getName()) && list != null) list.add(parser.nextText().trim());
                    else if (event == XmlPullParser.END_TAG && "tech-list".equals(parser.getName()) && list != null && techs.containsAll(list)) return new ComponentName(pkg, activity.info.name);
                }
            } catch (Exception unreadable) { System.out.println("[apklayer-hub] nfc: tech list of " + pkg + " not readable: " + unreadable); }
        }
        return null;
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) text.append(String.format("%02x", b));
        return text.toString();
    }
}
