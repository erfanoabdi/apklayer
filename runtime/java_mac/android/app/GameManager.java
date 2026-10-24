package android.app;
/** Per-app game-mode manager (API 31). Mac.apk applies no game-mode tuning, so
 *  the mode is STANDARD and setGameState is inert. */
public final class GameManager {
    public static final int GAME_MODE_UNSUPPORTED = 0, GAME_MODE_STANDARD = 1,
        GAME_MODE_PERFORMANCE = 2, GAME_MODE_BATTERY = 3, GAME_MODE_CUSTOM = 4;
    public GameManager() {}
    public int getGameMode() { return GAME_MODE_STANDARD; }
    public void setGameState(GameState gameState) {}
}
