// android.net.TransportInfo -- a marker interface for transport-specific
// network info (e.g. WifiInfo implements it). NetworkCapabilities.
// getTransportInfo() returns one. AOSP declares it as an empty public
// interface; concrete transports implement it.
// AOSP: packages/modules/Connectivity/framework/src/android/net/TransportInfo.java
package android.net;

public interface TransportInfo {
}
