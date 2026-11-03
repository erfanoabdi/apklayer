# Fake services hit by the corpus

Run 2026-11-03 23:38, apps: uibench

First call per process to a Robolectric null/deep proxy, a missing or Robolectric-shadowed manager, or HostMediaStub ("[fake]" lines, FakeLog).

| apps | service | call | which |
|---|---|---|---|
| 1 | appops | getSystemService=ShadowAppOpsManager | uibench |
| 1 | autofill | getSystemService=ShadowAutofillManager | uibench |
| 1 | batterymanager | getSystemService=ShadowBatteryManager | uibench |
| 1 | clipboard | getSystemService=ShadowClipboardManager | uibench |
| 1 | content_capture | getSystemService=null | uibench |
| 1 | display | getSystemService=ShadowDisplayManager | uibench |
| 1 | input_method | IInputMethodManager.addClient | uibench |
| 1 | input_method | getSystemService=ShadowInputMethodManager | uibench |
| 1 | location | getSystemService=ShadowLocationManager | uibench |
| 1 | power | getSystemService=ShadowPowerManager | uibench |
| 1 | role | getSystemService=ShadowRoleManager | uibench |
| 1 | uimode | IUiModeManager.addCallback | uibench |
| 1 | uimode | IUiModeManager.getContrast | uibench |
| 1 | uimode | IUiModeManager.getForceInvertState | uibench |
| 1 | uimode | getSystemService=ShadowUIModeManager | uibench |
| 1 | usb | getSystemService=ShadowUsbManager | uibench |
| 1 | voiceinteraction | IVoiceInteractionManagerService.notifyActivityEventChanged | uibench |
| 1 | wifi | IWifiManager.getVerboseLoggingLevel | uibench |
| 1 | wifi | getSystemService=ShadowWifiManager | uibench |
| 1 | window | IWindowManager.hasNavigationBar | uibench |
| 1 | window | getSystemService=ShadowWindowManagerImpl | uibench |
