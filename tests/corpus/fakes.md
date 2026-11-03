# Fake services hit by the corpus

Run 2026-11-04 02:40, apps: astro, device-info, sensors, microg, documentsui, unciv, openttd

First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).

| apps | service | call | which |
|---|---|---|---|
| 7 | appops | getSystemService=ShadowAppOpsManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | autofill | getSystemService=ShadowAutofillManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | batterymanager | getSystemService=ShadowBatteryManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | clipboard | getSystemService=ShadowClipboardManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | content_capture | getSystemService=null | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | display | getSystemService=ShadowDisplayManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | input_method | IInputMethodManager.addClient | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | input_method | getSystemService=ShadowInputMethodManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | location | getSystemService=ShadowLocationManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | power | getSystemService=ShadowPowerManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | role | getSystemService=ShadowRoleManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | uimode | IUiModeManager.addCallback | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | uimode | IUiModeManager.getContrast | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | uimode | IUiModeManager.getForceInvertState | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | uimode | getSystemService=ShadowUIModeManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | usb | getSystemService=ShadowUsbManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | voiceinteraction | IVoiceInteractionManagerService.notifyActivityEventChanged | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | wifi | IWifiManager.getVerboseLoggingLevel | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | wifi | getSystemService=ShadowWifiManager | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | window | IWindowManager.hasNavigationBar | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 7 | window | getSystemService=ShadowWindowManagerImpl | astro, device-info, documentsui, microg, openttd, sensors, unciv |
| 4 | accessibility | getSystemService=ShadowAccessibilityManager | device-info, documentsui, microg, sensors |
| 3 | alarm | getSystemService=ShadowAlarmManager | astro, microg, sensors |
| 3 | sensor | getSystemService=ShadowSensorManager | device-info, sensors, unciv |
| 2 | phone | getSystemService=ShadowTelephonyManager | astro, sensors |
| 2 | telephony_subscription_service | getSystemService=ShadowSubscriptionManager | astro, sensors |
| 1 | camera | getSystemService=ShadowCameraManager | device-info |
| 1 | device_policy | IDevicePolicyManager.getString | documentsui |
| 1 | device_policy | getSystemService=ShadowDevicePolicyManager | documentsui |
| 1 | input | IInputManager.getVelocityTrackerStrategy | sensors |
| 1 | overlay | getSystemService=null | documentsui |
| 1 | storagestats | getSystemService=ShadowStorageStatsManager | astro |
| 1 | uimode | IUiModeManager.removeCallback | documentsui |
| 1 | vibrator | getSystemService=ShadowSystemVibrator | unciv |
