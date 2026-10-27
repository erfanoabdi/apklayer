package apklayer.host;

import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.List;

/**
 * PendingIntents owned by the hub (the part system_server's PendingIntentController plays): each is an IIntentSender binder that outlives its creator, with
 * the creator's identity. Sending starts the activity / broadcasts / starts the service as the creator, with the sender's fill-in intent when the PendingIntent
 * is mutable. FLAG_ONE_SHOT, CANCEL_CURRENT, UPDATE_CURRENT, NO_CREATE and IMMUTABLE follow Android.
 */
final class HubPendingIntents {
    private static final int ONE_SHOT = 0x40000000, NO_CREATE = 0x20000000, CANCEL_CURRENT = 0x10000000, UPDATE_CURRENT = 0x08000000, IMMUTABLE = 0x04000000;
    private static final int BROADCAST = 1, ACTIVITY = 2, SERVICE = 4, FOREGROUND_SERVICE = 5;
    private static final int CANCELED = -96;

    private final HubDirectory directory;
    private final HubBroadcasts broadcasts;
    private final HubActivities activities;
    private final HubStartedServices services;
    private final List<Record> records = new ArrayList<>();

    private final class Record extends IIntentSender.Stub {
        final String creator; final int uid, type, requestCode; final String resultWho;
        Intent[] intents; final String[] resolvedTypes; final int flags; Bundle options;
        boolean canceled;
        Record(String creator, int uid, int type, String resultWho, int requestCode, Intent[] intents, String[] resolvedTypes, int flags, Bundle options) {
            this.creator = creator; this.uid = uid; this.type = type; this.resultWho = resultWho; this.requestCode = requestCode; this.intents = intents;
            this.resolvedTypes = resolvedTypes; this.flags = flags; this.options = options;
        }
        Intent last() { return intents == null || intents.length == 0 ? new Intent() : intents[intents.length - 1]; }
        boolean same(String creator, int type, String resultWho, int requestCode, Intent[] other) {
            Intent mine = last(), theirs = other == null || other.length == 0 ? new Intent() : other[other.length - 1];
            return this.creator.equals(creator) && this.type == type && this.requestCode == requestCode && java.util.Objects.equals(this.resultWho, resultWho) && mine.filterEquals(theirs);
        }
        @Override public void send(int code, Intent intent, String resolvedType, IBinder whitelistToken, IIntentReceiver finished, String requiredPermission, Bundle options) {
            dispatch(this, code, intent, finished == null ? null : finished.asBinder(), requiredPermission);
        }
    }

    HubPendingIntents(HubDirectory directory, HubBroadcasts broadcasts, HubActivities activities, HubStartedServices services) {
        this.directory = directory; this.broadcasts = broadcasts; this.activities = activities; this.services = services;
    }

    /** The IIntentSender for the parameters (a new one, or the existing equal one); null for FLAG_NO_CREATE without one. */
    IBinder get(HubDirectory.Peer caller, int type, String packageName, String resultWho, int requestCode, Intent[] intents, String[] resolvedTypes, int flags, Bundle options) {
        if (!caller.name().equals(packageName)) throw new SecurityException(caller.name() + " cannot create a PendingIntent for " + packageName);
        synchronized (records) {
            Record existing = null;
            for (Record record : records) if (!record.canceled && record.same(caller.name(), type, resultWho, requestCode, intents)) { existing = record; break; }
            if (existing != null) {
                if ((flags & CANCEL_CURRENT) != 0) { existing.canceled = true; records.remove(existing); }
                else {
                    if ((flags & UPDATE_CURRENT) != 0 && (existing.flags & IMMUTABLE) == 0 && intents != null && intents.length > 0 && existing.intents.length > 0) {
                        Bundle extras = intents[intents.length - 1].getExtras();
                        existing.last().replaceExtras(extras == null ? null : new Bundle(extras));
                    }
                    return existing;
                }
            }
            if ((flags & NO_CREATE) != 0) return null;
            Record record = new Record(caller.name(), caller.uid(), type, resultWho, requestCode, intents, resolvedTypes, flags, options);
            records.add(record);
            return record;
        }
    }

    /** Sends through the sender's own PendingIntent (the sender's fill-in intent, result receiver and permission); the status is negative when it was canceled. */
    int send(IBinder sender, int code, Intent fillIn, IBinder finished, String requiredPermission) {
        return sender instanceof Record record ? dispatch(record, code, fillIn, finished, requiredPermission) : CANCELED;
    }

    private int dispatch(Record record, int code, Intent fillIn, IBinder finished, String requiredPermission) {
        synchronized (records) {
            if (record.canceled) return CANCELED;
            if ((record.flags & ONE_SHOT) != 0) { record.canceled = true; records.remove(record); }
        }
        Intent intent = new Intent(record.last());
        if (fillIn != null && (record.flags & IMMUTABLE) == 0) intent.fillIn(fillIn, 0);
        HubDirectory.Peer creator = new HubDirectory.Peer(-1, record.uid, record.creator, null, null, true);
        System.out.println("[apklayer-hub] PendingIntent of " + record.creator + " type " + record.type + " sent: " + intent.getAction() + " " + intent.getComponent());
        switch (record.type) {
            case ACTIVITY -> activities.start(creator, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null);
            case SERVICE, FOREGROUND_SERVICE -> services.start(creator, intent);
            case BROADCAST -> broadcasts.send(creator, intent, finished, code, null, null, requiredPermission == null ? null : new String[] {requiredPermission}, finished != null, false, 0);
            default -> { return CANCELED; }
        }
        return 0;
    }

    /** Only the creator cancels. */
    void cancel(HubDirectory.Peer caller, IBinder sender) {
        if (!(sender instanceof Record record)) return;
        if (!record.creator.equals(caller.name())) throw new SecurityException("Only the creator cancels a PendingIntent");
        synchronized (records) { record.canceled = true; records.remove(record); }
    }

    /** {creator package, creator uid, immutable, type}, also for a canceled one (as Android answers); null when it is not one of ours. */
    Object[] info(IBinder sender) {
        return sender instanceof Record record ? new Object[] {record.creator, record.uid, (record.flags & IMMUTABLE) != 0, record.type} : null;
    }
}
