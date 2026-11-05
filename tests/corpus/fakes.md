# Fake services hit by the corpus

Run 2026-11-05 02:47, apps: opencamera

First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).

| apps | service | call | which |
|---|---|---|---|
| 1 | accessibility | getSystemService=ShadowAccessibilityManager | opencamera |
| 1 | appops | getSystemService=ShadowAppOpsManager | opencamera |
| 1 | audio | IAudioService.isCameraSoundForced | opencamera |
| 1 | autofill | getSystemService=ShadowAutofillManager | opencamera |
| 1 | batterymanager | getSystemService=ShadowBatteryManager | opencamera |
| 1 | clipboard | getSystemService=ShadowClipboardManager | opencamera |
| 1 | content_capture | getSystemService=null | opencamera |
| 1 | display | getSystemService=ShadowDisplayManager | opencamera |
| 1 | input_method | IInputMethodManager.addClient | opencamera |
| 1 | input_method | getSystemService=ShadowInputMethodManager | opencamera |
| 1 | keyguard | getSystemService=ShadowKeyguardManager | opencamera |
| 1 | location | getSystemService=ShadowLocationManager | opencamera |
| 1 | power | getSystemService=ShadowPowerManager | opencamera |
| 1 | role | getSystemService=ShadowRoleManager | opencamera |
| 1 | sensor | getSystemService=ShadowSensorManager | opencamera |
| 1 | uimode | IUiModeManager.addCallback | opencamera |
| 1 | uimode | IUiModeManager.getContrast | opencamera |
| 1 | uimode | IUiModeManager.getForceInvertState | opencamera |
| 1 | uimode | getSystemService=ShadowUIModeManager | opencamera |
| 1 | voiceinteraction | IVoiceInteractionManagerService.notifyActivityEventChanged | opencamera |
| 1 | wifi | IWifiManager.getVerboseLoggingLevel | opencamera |
| 1 | wifi | getSystemService=ShadowWifiManager | opencamera |
| 1 | window | IWindowManager.hasNavigationBar | opencamera |
| 1 | window | getSystemService=ShadowWindowManagerImpl | opencamera |
