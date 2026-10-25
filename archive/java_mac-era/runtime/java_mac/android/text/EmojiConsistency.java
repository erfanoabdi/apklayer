// android.text.EmojiConsistency (API 35) -- the set of emoji whose rendering
// the platform guarantees is consistent across the system font and any
// EmojiCompat font an app installs. Text pickers and emoji keyboards read it
// to decide which glyphs are safe to offer.
//
// Mirrors frameworks/base/core/java/android/text/EmojiConsistency.java's public
// surface. AOSP populates the set from a framework resource
// (com.android.internal.R.array.emoji_consistency_set, a build-time list of
// codepoint sequences) that Mac.apk does not ship; there is no macOS-side
// equivalent to route to, because the answer is a property of the ANDROID font
// bundle rather than of the host. An empty set is the honest answer -- and it
// is a legal one: AOSP's own contract is "the returned set may be empty", and
// every caller iterates it rather than assuming a member.
package android.text;

import java.util.Collections;
import java.util.Set;

public final class EmojiConsistency {

    private EmojiConsistency() { }

    /**
     * AOSP EmojiConsistency.getEmojiConsistencySet(): an unmodifiable set of
     * int[] codepoint sequences. Never null.
     */
    public static Set<int[]> getEmojiConsistencySet() {
        return Collections.emptySet();
    }
}
