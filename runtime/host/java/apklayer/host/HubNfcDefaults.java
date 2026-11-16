package apklayer.host;

/** What a desktop NFC controller does not have, as INfcAdapter answers (no HCE, no routing table, no Beam, no vendor extensions): every method HubNfc does not implement (generated from the framework's INfcAdapter, android-all 16). */
abstract class HubNfcDefaults extends android.nfc.INfcAdapter.Stub {
    @Override public android.nfc.INfcCardEmulation getNfcCardEmulationInterface() { return null; }
    @Override public android.nfc.INfcFCardEmulation getNfcFCardEmulationInterface() { return null; }
    @Override public android.nfc.INfcAdapterExtras getNfcAdapterExtrasInterface(java.lang.String p0) { return null; }
    @Override public android.nfc.INfcDta getNfcDtaInterface(java.lang.String p0) { return null; }
    @Override public int pausePolling(long p0) { return 0; }
    @Override public int resumePolling() { return 0; }
    @Override public void setAppCallback(android.nfc.IAppCallback p0) { }
    @Override public boolean ignore(int p0, int p1, android.nfc.ITagRemovedCallback p2) { return false; }
    @Override public void addNfcUnlockHandler(android.nfc.INfcUnlockHandler p0, int[] p1) { }
    @Override public void removeNfcUnlockHandler(android.nfc.INfcUnlockHandler p0) { }
    @Override public void verifyNfcPermission() { }
    @Override public boolean isNfcSecureEnabled() { return false; }
    @Override public boolean deviceSupportsNfcSecure() { return false; }
    @Override public boolean setNfcSecure(boolean p0) { return false; }
    @Override public android.nfc.NfcAntennaInfo getNfcAntennaInfo() { return null; }
    @Override public void setControllerAlwaysOn(int p0) { }
    @Override public boolean isControllerAlwaysOn() { return false; }
    @Override public boolean isControllerAlwaysOnSupported() { return false; }
    @Override public void registerControllerAlwaysOnListener(android.nfc.INfcControllerAlwaysOnListener p0) { }
    @Override public void unregisterControllerAlwaysOnListener(android.nfc.INfcControllerAlwaysOnListener p0) { }
    @Override public boolean isTagIntentAppPreferenceSupported() { return false; }
    @Override public java.util.Map getTagIntentAppPreferenceForUser(int p0) { return null; }
    @Override public int setTagIntentAppPreferenceForUser(int p0, java.lang.String p1, boolean p2) { return 0; }
    @Override public boolean isReaderOptionEnabled() { return false; }
    @Override public boolean isReaderOptionSupported() { return false; }
    @Override public boolean enableReaderOption(boolean p0, java.lang.String p1) { return false; }
    @Override public boolean isReaderModeAnnotationSupported() { return false; }
    @Override public boolean isObserveModeSupported() { return false; }
    @Override public boolean isObserveModeEnabled() { return false; }
    @Override public boolean setObserveMode(boolean p0, java.lang.String p1) { return false; }
    @Override public boolean isExitFramesSupported() { return false; }
    @Override public boolean isPowerSavingModeSupported() { return false; }
    @Override public boolean isPowerSavingModeEnabled() { return false; }
    @Override public void setPowerSavingMode(boolean p0) { }
    @Override public boolean setWlcEnabled(boolean p0) { return false; }
    @Override public boolean isWlcEnabled() { return false; }
    @Override public void registerWlcStateListener(android.nfc.INfcWlcStateListener p0) { }
    @Override public void unregisterWlcStateListener(android.nfc.INfcWlcStateListener p0) { }
    @Override public android.nfc.WlcListenerDeviceInfo getWlcListenerDeviceInfo() { return null; }
    @Override public void updateDiscoveryTechnology(android.os.IBinder p0, int p1, int p2, java.lang.String p3) { }
    @Override public void notifyPollingLoop(android.nfc.cardemulation.PollingFrame p0) { }
    @Override public void notifyHceDeactivated() { }
    @Override public void notifyTestHceData(int p0, byte[] p1) { }
    @Override public int sendVendorNciMessage(int p0, int p1, int p2, byte[] p3) { return 0; }
    @Override public void registerVendorExtensionCallback(android.nfc.INfcVendorNciCallback p0) { }
    @Override public void unregisterVendorExtensionCallback(android.nfc.INfcVendorNciCallback p0) { }
    @Override public void registerOemExtensionCallback(android.nfc.INfcOemExtensionCallback p0) { }
    @Override public void unregisterOemExtensionCallback(android.nfc.INfcOemExtensionCallback p0) { }
    @Override public void clearPreference() { }
    @Override public void setScreenState() { }
    @Override public void checkFirmware() { }
    @Override public java.util.Map fetchActiveNfceeList() { return null; }
    @Override public void triggerInitialization() { }
    @Override public boolean getSettingStatus() { return false; }
    @Override public java.util.List<android.nfc.Entry> getRoutingTableEntryList() { return null; }
    @Override public void indicateDataMigration(boolean p0, java.lang.String p1) { }
    @Override public int commitRouting() { return 0; }
    @Override public boolean isTagIntentAllowed(java.lang.String p0, int p1) { return false; }
    @Override public android.nfc.IT4tNdefNfcee getT4tNdefNfceeInterface() { return null; }
    @Override public long getMaxPausePollingTimeoutMs() { return 0; }
    @Override public int emulateNfcATag(boolean p0, byte p1, byte p2, byte p3, byte[] p4, byte p5, byte[] p6) { return 0; }
    @Override public void registerGestureExchangeCallback(android.nfc.IReaderCallback p0) { }
    @Override public void unregisterGestureExchangeCallback(android.nfc.IReaderCallback p0) { }
    @Override public java.lang.String getGestureExchangeAid() { return null; }
    @Override public void allowOneTransaction() { }
    @Override public java.util.List<android.nfc.RfDiscoverConfig> getRfDiscoverConfigurations() { return null; }

}
