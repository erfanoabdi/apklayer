# Fake services hit by the corpus

Run 2026-11-03 16:55, apps: nextcloud

First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).

| apps | service | call | which |
|---|---|---|---|
| 1 | accessibility | getSystemService=ShadowAccessibilityManager | nextcloud |
| 1 | account | getSystemService=ShadowAccountManager | nextcloud |
| 1 | appops | getSystemService=ShadowAppOpsManager | nextcloud |
| 1 | autofill | getSystemService=ShadowAutofillManager | nextcloud |
| 1 | batterymanager | getSystemService=ShadowBatteryManager | nextcloud |
| 1 | clipboard | getSystemService=ShadowClipboardManager | nextcloud |
| 1 | content_capture | getSystemService=null | nextcloud |
| 1 | display | getSystemService=ShadowDisplayManager | nextcloud |
| 1 | input_method | IInputMethodManager.addClient | nextcloud |
| 1 | input_method | getSystemService=ShadowInputMethodManager | nextcloud |
| 1 | location | getSystemService=ShadowLocationManager | nextcloud |
| 1 | media | HostMediaStub.query | nextcloud |
| 1 | power | getSystemService=ShadowPowerManager | nextcloud |
| 1 | role | getSystemService=ShadowRoleManager | nextcloud |
| 1 | uimode | IUiModeManager.addCallback | nextcloud |
| 1 | uimode | IUiModeManager.getContrast | nextcloud |
| 1 | uimode | IUiModeManager.getForceInvertState | nextcloud |
| 1 | uimode | getSystemService=ShadowUIModeManager | nextcloud |
| 1 | usb | getSystemService=ShadowUsbManager | nextcloud |
| 1 | voiceinteraction | IVoiceInteractionManagerService.notifyActivityEventChanged | nextcloud |
| 1 | wifi | IWifiManager.getVerboseLoggingLevel | nextcloud |
| 1 | wifi | getSystemService=ShadowWifiManager | nextcloud |
| 1 | window | IWindowManager.hasNavigationBar | nextcloud |
| 1 | window | getSystemService=ShadowWindowManagerImpl | nextcloud |
