package org.apache.http.config;
/** Immutable socket-level configuration (4.3 config API). */
public class SocketConfig {
    public static final SocketConfig DEFAULT = new SocketConfig(0, false, false, 0, 0, -1, -1);
    private final int soTimeout, sndBufSize, rcvBufSize, backlogSize;
    private final boolean soReuseAddress, soKeepAlive, tcpNoDelay;
    SocketConfig(int soTimeout, boolean soReuseAddress, boolean soKeepAlive,
                 int sndBufSize, int rcvBufSize, int soLinger, int backlogSize) {
        this.soTimeout = soTimeout; this.soReuseAddress = soReuseAddress; this.soKeepAlive = soKeepAlive;
        this.sndBufSize = sndBufSize; this.rcvBufSize = rcvBufSize; this.tcpNoDelay = true; this.backlogSize = backlogSize;
    }
    public int getSoTimeout() { return soTimeout; }
    public boolean isSoReuseAddress() { return soReuseAddress; }
    public boolean isSoKeepAlive() { return soKeepAlive; }
    public boolean isTcpNoDelay() { return tcpNoDelay; }
    public int getSndBufSize() { return sndBufSize; }
    public int getRcvBufSize() { return rcvBufSize; }
    public int getBacklogSize() { return backlogSize; }
    public static Builder custom() { return new Builder(); }
    public static Builder copy(SocketConfig config) { return new Builder(); }
    public static final class Builder {
        private int soTimeout, sndBufSize, rcvBufSize, backlogSize;
        private boolean soReuseAddress, soKeepAlive;
        Builder() {}
        public Builder setSoTimeout(int soTimeout) { this.soTimeout = soTimeout; return this; }
        public Builder setSoReuseAddress(boolean soReuseAddress) { this.soReuseAddress = soReuseAddress; return this; }
        public Builder setSoKeepAlive(boolean soKeepAlive) { this.soKeepAlive = soKeepAlive; return this; }
        public Builder setTcpNoDelay(boolean tcpNoDelay) { return this; }
        public Builder setSndBufSize(int sndBufSize) { this.sndBufSize = sndBufSize; return this; }
        public Builder setRcvBufSize(int rcvBufSize) { this.rcvBufSize = rcvBufSize; return this; }
        public Builder setBacklogSize(int backlogSize) { this.backlogSize = backlogSize; return this; }
        public SocketConfig build() {
            return new SocketConfig(soTimeout, soReuseAddress, soKeepAlive, sndBufSize, rcvBufSize, -1, backlogSize);
        }
    }
}
