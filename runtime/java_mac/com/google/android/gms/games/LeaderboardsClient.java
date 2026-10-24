package com.google.android.gms.games;

import android.content.Intent;
import com.google.android.gms.tasks.Task;

// com.google.android.gms.games.LeaderboardsClient -- the v2 leaderboards client.
// With no Play Games backend, submitScore is a no-op (NEVER records a fabricated
// score) and the "show leaderboard UI" / score-query Tasks fail cleanly. Mirrors
// the published method set apps call. Ref: developers.google.com/android/reference
public interface LeaderboardsClient {

    void submitScore(String leaderboardId, long score);

    void submitScore(String leaderboardId, long score, String scoreTag);

    Task<Intent> getAllLeaderboardsIntent();

    Task<Intent> getLeaderboardIntent(String leaderboardId);

    Task<Intent> getLeaderboardIntent(String leaderboardId, int timeSpan);

    Task<Intent> getLeaderboardIntent(String leaderboardId, int timeSpan, int collection);
}
