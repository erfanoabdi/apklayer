# Fake services hit by the corpus

Run 2026-11-04 11:26, apps: newpipe, astro, device-info, sensors, microg, documentsui, unciv, openttd

First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).

| apps | service | call | which |
|---|---|---|---|
| 8 | appops | getSystemService=ShadowAppOpsManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | autofill | getSystemService=ShadowAutofillManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | batterymanager | getSystemService=ShadowBatteryManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | clipboard | getSystemService=ShadowClipboardManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | content_capture | getSystemService=null | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | display | getSystemService=ShadowDisplayManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | input_method | IInputMethodManager.addClient | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | input_method | getSystemService=ShadowInputMethodManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | location | getSystemService=ShadowLocationManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | power | getSystemService=ShadowPowerManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | role | getSystemService=ShadowRoleManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | uimode | IUiModeManager.addCallback | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | uimode | IUiModeManager.getContrast | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | uimode | IUiModeManager.getForceInvertState | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | uimode | getSystemService=ShadowUIModeManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | usb | getSystemService=ShadowUsbManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | voiceinteraction | IVoiceInteractionManagerService.notifyActivityEventChanged | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | wifi | IWifiManager.getVerboseLoggingLevel | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | wifi | getSystemService=ShadowWifiManager | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | window | IWindowManager.hasNavigationBar | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 8 | window | getSystemService=ShadowWindowManagerImpl | astro, device-info, documentsui, microg, newpipe, openttd, sensors, unciv |
| 5 | accessibility | getSystemService=ShadowAccessibilityManager | device-info, documentsui, microg, newpipe, sensors |
| 4 | sensor | getSystemService=ShadowSensorManager | device-info, openttd, sensors, unciv |
| 3 | input | IInputManager.getVelocityTrackerStrategy | newpipe, openttd, sensors |
| 2 | alarm | getSystemService=ShadowAlarmManager | microg, sensors |
| 2 | camera | getSystemService=ShadowCameraManager | device-info, newpipe |
| 2 | input | getSystemService=ShadowInputManager | newpipe, openttd |
| 2 | phone | getSystemService=ShadowTelephonyManager | astro, sensors |
| 2 | telephony_subscription_service | getSystemService=ShadowSubscriptionManager | astro, sensors |
| 1 | device_policy | IDevicePolicyManager.getString | documentsui |
| 1 | device_policy | getSystemService=ShadowDevicePolicyManager | documentsui |
| 1 | keyguard | getSystemService=ShadowKeyguardManager | openttd |
| 1 | locale | getSystemService=ShadowLocaleManager | newpipe |
| 1 | media_session | getSystemService=ShadowMediaSessionManager | newpipe |
| 1 | overlay | getSystemService=null | documentsui |
| 1 | restrictions | getSystemService=ShadowRestrictionsManager | newpipe |
| 1 | systemhealth | getSystemService=ShadowSystemHealthManager | newpipe |
| 1 | uimode | IUiModeManager.removeCallback | documentsui |
| 1 | window | IWindowManager.attachWindowContextToDisplayArea | newpipe |
| 1 | window | IWindowManager.getWindowInsets | newpipe |
