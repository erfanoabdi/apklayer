# Game controllers

Linux gamepads reach Android apps as Android's own `InputDevice`, `KeyEvent` and `MotionEvent`, the way InputReader would make them.

## Path
1. **Shell** (`bridge/shell/gamepad.c`). Opens `/dev/input/event*` nodes that have a gamepad or joystick button (`BTN_SOUTH` or `BTN_TRIGGER`) and `ABS_X`: mice, touchpads, tablets and keyboards have neither. An inotify watch on `/dev/input` (`IN_CREATE`, `IN_ATTRIB`: a node whose permissions udev sets later is tried again) gives hot-plug for USB and Bluetooth pads. The shell sends raw evdev, no interpretation:
   - `MSG_PAD_DEVICE` to every app: added (id, vendor, product, version, name, each `ABS_*` axis with min, max, flat and current value, rumble capability) or removed. An app that connects later gets the pads that are there.
   - `MSG_PAD_EVENT` to the app of the window with keyboard focus: the `EV_KEY`, `EV_ABS` and `SYN_REPORT` events of a read.
   - `MSG_PAD_RUMBLE` (app to shell): strong and weak magnitude and milliseconds; the shell uploads one `FF_RUMBLE` effect per pad (`EVIOCSFF`), plays it and stops it (ms 0).
2. **Host** (`runtime/host/java/apklayer/host/HostGamepad.java`). Per pad: the key layout, the `InputDevice` and the events.
   - Layout: Android's own `.kl` files (`runtime/host/resources/keylayout`: Generic, Xbox 360, Xbox One and One S Bluetooth, DualShock 4 and the older DS3 id, DualSense, Switch Pro) in Android's lookup order (`Vendor_xxxx_Product_xxxx_Version_xxxx`, `Vendor_xxxx_Product_xxxx`, `Generic`). A button is a `KeyEvent` `BUTTON_A/B/X/Y/L1/R1/L2/R2/THUMBL/THUMBR/START/SELECT/MODE` with `SOURCE_GAMEPAD`; kernel pads that report the D-pad as keys `0x220..0x223` give `DPAD_*`. More pads: copy their `.kl` from `frameworks/base/data/keyboards` into that directory.
   - Device: `SOURCE_GAMEPAD | SOURCE_JOYSTICK`, plus `SOURCE_DPAD` with a hat, external, a controller number (1, 2, ...), a vibrator when the node takes `FF_RUMBLE`. Motion ranges are normalised like InputReader's JoystickInputMapper: sticks -1..1 (`AXIS_X/Y` left, `AXIS_Z/RZ` right on the Xbox and PlayStation layouts), triggers 0..1 (`AXIS_LTRIGGER/RTRIGGER`), hat -1/0/1 (`AXIS_HAT_X/Y`), flat from the `.kl` or the device.
   - Events: one `ACTION_MOVE` `MotionEvent` (`SOURCE_JOYSTICK`) per `SYN_REPORT` that changed an axis. The window's `ViewRootImpl` turns hat and sticks into DPAD keys when the app leaves the motion event unhandled (Android's `SyntheticJoystickHandler`).
   - `InputManagerGlobal`: `InputDevice.getDeviceIds()`, `getDevice(id)` and `InputManager.registerInputDeviceListener` (`onInputDeviceAdded/Removed`) work by calling the real `onInputDevicesChanged`. Robolectric's shadow has no `IInputManager`; `HostGamepad` installs a stand-in that answers `getInputDevice`, the vibrator calls and nothing else.
   - Rumble: `InputDevice.getVibratorManager()` (and `getVibrator()`) `vibrate()` / `cancel()` go to `MSG_PAD_RUMBLE`. A `VibrationEffect` is reduced to the loudest step as both motors and its length (cap 5 s; a repeating or unknown length plays 1 s until cancelled; primitives and predefined effects half strength).
3. `ShadowHostKeyEvent` / `ShadowHostMotionEvent`: `InputEvent.getDevice()` is `InputDevice.getDevice(getDeviceId())` as on Android. Robolectric's returned a field nothing sets, so `event.getDevice()` was null for every event (now also the virtual device 0 for touch and keys).

## Not done
- Gamepad events go to the app of the focused window only; two apps sharing one shell do not both see a pad. The pad's state is not replayed when focus changes (a button held across a focus change is lost).
- Gyro, touchpad, LED and battery of DualShock/DualSense (`sensor` lines of the `.kl`, `InputDevice.getBatteryState`, `getLightsManager`) are not mapped.
- `.kl` lines `axis ... split` and `key usage` are ignored; `.idc` files are not read.
- Multiple vibrator motors (`getVibratorIds` has one id) and combined effects other than `Mono`.
- No grab: the pad is not claimed from the desktop, so the compositor or a game launcher reading it too sees the same events.

## Test
`tests/run gamepad` (VM, `sudo -n`, `/dev/uinput`). `tests/lib/uinput_pad.py` makes a virtual Xbox 360 pad (`045e:028e`) with rumble and puts its event node in a directory of the test (`APKLAYER_INPUT_DIR`; a node needs a mount without `nodev`, so not `/tmp`). `tests/apps/hello-gamepad` prints what a game sees: device list and listener, sources and ranges, buttons, axes, hat, the DPAD keys from the hat, rumble. The test also answers the kernel's force-feedback upload and checks the effect played (strength, length, stop). It does not use udev: the uaccess/`input` group permission path and the `IN_ATTRIB` retry need a real session.

Environment: `APKLAYER_INPUT_DIR` (shell): the directory of event nodes (default `/dev/input`); `APKLAYER_DEBUG_INPUT=1` logs the events the shell forwards, `-Dapklayer.debugkeys=true` the host's mapping.
