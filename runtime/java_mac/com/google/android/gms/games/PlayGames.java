package com.google.android.gms.games;

import android.app.Activity;
import android.content.Intent;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.games.PlayGames -- the v2 entry point. Each accessor
// returns a clean-unavailable client: sign-in reports NOT authenticated / fails
// with SIGN_IN_REQUIRED, achievement/leaderboard writes are no-ops, and any
// "show UI" / immediate Task fails with SERVICE_MISSING. NEVER fabricates a
// player, achievement, or score (de-Googled policy). Mirrors the published static
// factory. Ref: developers.google.com/android/reference/.../games/PlayGames
public final class PlayGames {
    private PlayGames() {}

    public static GamesSignInClient getGamesSignInClient(Activity activity) {
        return SIGN_IN;
    }

    public static AchievementsClient getAchievementsClient(Activity activity) {
        return ACHIEVEMENTS;
    }

    public static LeaderboardsClient getLeaderboardsClient(Activity activity) {
        return LEADERBOARDS;
    }

    private static <T> Task<T> unavailable() {
        return Tasks.forException(new ApiException(
                new Status(CommonStatusCodes.SERVICE_MISSING, "Play Games unavailable")));
    }

    private static final GamesSignInClient SIGN_IN = new GamesSignInClient() {
        @Override public Task<AuthenticationResult> isAuthenticated() {
            // Honest negative: no Play Games account on a de-Googled device.
            return Tasks.forResult(new AuthenticationResult(false));
        }
        @Override public Task<AuthenticationResult> signIn() {
            return Tasks.forResult(new AuthenticationResult(false));
        }
        @Override public Task<String> requestServerSideAccess(String serverClientId, boolean forceRefreshToken) {
            return unavailable();
        }
    };

    private static final AchievementsClient ACHIEVEMENTS = new AchievementsClient() {
        @Override public void unlock(String achievementId) {}
        @Override public void increment(String achievementId, int numSteps) {}
        @Override public void reveal(String achievementId) {}
        @Override public void setSteps(String achievementId, int numSteps) {}
        @Override public Task<Void> unlockImmediate(String achievementId) { return unavailable(); }
        @Override public Task<Boolean> incrementImmediate(String achievementId, int numSteps) { return unavailable(); }
        @Override public Task<Void> revealImmediate(String achievementId) { return unavailable(); }
        @Override public Task<Boolean> setStepsImmediate(String achievementId, int numSteps) { return unavailable(); }
        @Override public Task<Intent> getAchievementsIntent() { return unavailable(); }
    };

    private static final LeaderboardsClient LEADERBOARDS = new LeaderboardsClient() {
        @Override public void submitScore(String leaderboardId, long score) {}
        @Override public void submitScore(String leaderboardId, long score, String scoreTag) {}
        @Override public Task<Intent> getAllLeaderboardsIntent() { return unavailable(); }
        @Override public Task<Intent> getLeaderboardIntent(String leaderboardId) { return unavailable(); }
        @Override public Task<Intent> getLeaderboardIntent(String leaderboardId, int timeSpan) { return unavailable(); }
        @Override public Task<Intent> getLeaderboardIntent(String leaderboardId, int timeSpan, int collection) { return unavailable(); }
    };
}
