package com.google.android.gms.games;

import android.content.Intent;
import com.google.android.gms.tasks.Task;

// com.google.android.gms.games.AchievementsClient -- the v2 achievements client.
// With no Play Games backend, unlock/increment/reveal are no-ops (NEVER records a
// fabricated achievement) and the "show achievements UI" Task fails cleanly.
// unlockImmediate/incrementImmediate return a Task whose result is honest
// (false / no-op). Ref: developers.google.com/android/reference/.../AchievementsClient
public interface AchievementsClient {

    void unlock(String achievementId);

    void increment(String achievementId, int numSteps);

    void reveal(String achievementId);

    void setSteps(String achievementId, int numSteps);

    Task<Void> unlockImmediate(String achievementId);

    Task<Boolean> incrementImmediate(String achievementId, int numSteps);

    Task<Void> revealImmediate(String achievementId);

    Task<Boolean> setStepsImmediate(String achievementId, int numSteps);

    Task<Intent> getAchievementsIntent();
}
