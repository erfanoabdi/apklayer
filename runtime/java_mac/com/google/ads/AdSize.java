package com.google.ads;
public class AdSize {
    public static final AdSize BANNER       = new AdSize();
    public static final AdSize SMART_BANNER = new AdSize();
    public static final AdSize MEDIUM_RECTANGLE = new AdSize();
    public static final AdSize FULL_BANNER  = new AdSize();
    public static final AdSize LEADERBOARD  = new AdSize();
    public AdSize() {}
    public AdSize(int width, int height) {}
    public int getWidth()  { return 0; }
    public int getHeight() { return 0; }
}
