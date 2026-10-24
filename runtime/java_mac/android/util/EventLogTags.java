// android.util.EventLogTags -- the API-1 /system/etc/event-log-tags reader.
// AOSP itself gutted this class: since the tag file stopped being readable by
// apps every method returns null and both constructors are no-ops that merely
// keep the declared `throws IOException`. Mirrored exactly, so an app that
// still constructs one gets AOSP's behaviour (a live object answering null)
// rather than ClassNotFoundException.
//
// AOSP: frameworks/base/core/java/android/util/EventLogTags.java
package android.util;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * @deprecated This class is no longer functional. Use {@link android.util.EventLog} instead.
 */
@Deprecated
public class EventLogTags {
    @Deprecated
    public static class Description {
        @Deprecated public final int mTag;
        @Deprecated public final String mName;

        Description(int tag, String name) {
            mTag = tag;
            mName = name;
        }
    }

    @Deprecated
    public EventLogTags() throws IOException {}

    @Deprecated
    public EventLogTags(BufferedReader input) throws IOException {}

    @Deprecated
    public Description get(String name) { return null; }

    @Deprecated
    public Description get(int tag) { return null; }
}
