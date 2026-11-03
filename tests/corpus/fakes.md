# Fake services hit by the corpus

Run 2026-11-03 14:59, apps: sensors

First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).

| apps | service | call | which |
|---|---|---|---|
| 1 | accessibility | getSystemService=ShadowAccessibilityManager | sensors |
| 1 | alarm | getSystemService=ShadowAlarmManager | sensors |
| 1 | appops | getSystemService=ShadowAppOpsManager | sensors |
| 1 | autofill | getSystemService=ShadowAutofillManager | sensors |
| 1 | batterymanager | getSystemService=ShadowBatteryManager | sensors |
| 1 | clipboard | getSystemService=ShadowClipboardManager | sensors |
| 1 | content_capture | getSystemService=null | sensors |
| 1 | display | getSystemService=ShadowDisplayManager | sensors |
| 1 | input | IInputManager.getVelocityTrackerStrategy | sensors |
| 1 | input_method | IInputMethodManager.addClient | sensors |
| 1 | input_method | getSystemService=ShadowInputMethodManager | sensors |
| 1 | location | getSystemService=ShadowLocationManager | sensors |
| 1 | phone | getSystemService=ShadowTelephonyManager | sensors |
| 1 | power | getSystemService=ShadowPowerManager | sensors |
| 1 | role | getSystemService=ShadowRoleManager | sensors |
| 1 | sensor | getSystemService=ShadowSensorManager | sensors |
| 1 | telephony_subscription_service | getSystemService=ShadowSubscriptionManager | sensors |
| 1 | uimode | IUiModeManager.addCallback | sensors |
| 1 | uimode | IUiModeManager.getContrast | sensors |
| 1 | uimode | IUiModeManager.getForceInvertState | sensors |
| 1 | uimode | getSystemService=ShadowUIModeManager | sensors |
| 1 | usb | getSystemService=ShadowUsbManager | sensors |
| 1 | voiceinteraction | IVoiceInteractionManagerService.notifyActivityEventChanged | sensors |
| 1 | wifi | IWifiManager.getVerboseLoggingLevel | sensors |
| 1 | wifi | getSystemService=ShadowWifiManager | sensors |
| 1 | window | IWindowManager.hasNavigationBar | sensors |
| 1 | window | getSystemService=ShadowWindowManagerImpl | sensors |
