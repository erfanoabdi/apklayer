// com.google.android.gms.ads.VideoOptions
//
// GMS Ads reference: video-playback options for video ads. Pure local
// config; no video ad ever plays on a no-backend device.
package com.google.android.gms.ads;

public final class VideoOptions {
    private final boolean startMuted;
    private final boolean customControlsRequested;
    private final boolean clickToExpandRequested;

    private VideoOptions(Builder b) {
        this.startMuted = b.startMuted;
        this.customControlsRequested = b.customControlsRequested;
        this.clickToExpandRequested = b.clickToExpandRequested;
    }

    public boolean getStartMuted() { return startMuted; }

    public boolean getCustomControlsRequested() { return customControlsRequested; }

    public boolean getClickToExpandRequested() { return clickToExpandRequested; }

    public static final class Builder {
        private boolean startMuted = true;
        private boolean customControlsRequested = false;
        private boolean clickToExpandRequested = false;

        public Builder setStartMuted(boolean v) { this.startMuted = v; return this; }

        public Builder setCustomControlsRequested(boolean v) {
            this.customControlsRequested = v; return this;
        }

        public Builder setClickToExpandRequested(boolean v) {
            this.clickToExpandRequested = v; return this;
        }

        public VideoOptions build() { return new VideoOptions(this); }
    }
}
