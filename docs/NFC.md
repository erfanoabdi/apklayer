# NFC

Apps see an NFC adapter when the host has a source of tags, through Android's own classes: `NfcManager`, `NfcAdapter`, `Tag`, `Ndef`, `NfcA`,
`IsoDep`, `MifareUltralight` and the discovery intents run unchanged (the framework in android-all). apklayer provides what `NfcService` provides
behind them: the `nfc` binder service (`INfcAdapter`) and one `INfcTag` per tag, in the instance hub.

## Why this layer

The stack under a phone's `NfcAdapter` is `com.android.nfc` (NfcService) over libnfc-nci over the `android.hardware.nfc` HAL (`INfc`, NCI frames) of
the chip. A desktop reader does not speak NCI (PC/SC readers present APDUs; PN533/ST95 readers go through `neard` or libnfc), so there is no HAL to
start and NfcService would only add a state machine on top of a mock. The shim sits on the boundary the apps use, as USB (`HostUsb`/`HubUsb`) and
Bluetooth (`HostBluetooth`/`HubBluetooth`) do: the framework's Java stays real, the service behind it is the host's.

| Piece | Where |
|---|---|
| `INfcAdapter` service "nfc": state, reader mode (`setReaderMode`), foreground dispatch (`setForegroundDispatch`), the dispatch itself (`NfcDispatcher.dispatchTag`: reader, foreground overrides, then the system's NDEF, TECH, TAG activities) | `HubNfc.java`; the methods a desktop controller does not have are `HubNfcDefaults.java` (generated from the framework's interface, android-all 16) |
| `INfcTag`: connect, transceive, ndefRead/Write, per tag; the Tag the apps get carries this binder | `HubNfcTag.java` |
| Mock source: tag files dropped into a directory, a Type 2 (NTAG213 pages) and a Type 4 (NDEF application) emulation | `HubNfcMock.java` |
| PC/SC source (pcscd, `javax.smartcardio`) | `HubNfcPcsc.java` |
| Whether the host has NFC at all; the shadows that let the framework's own `NfcAdapter` and tech classes run (Robolectric's fakes of them are not used); `android.hardware.nfc` feature | `HostNfc.java`, `HostShadowProvider`, `HostPlainShadows`, `HostSystemConfig`, `ShadowHostServiceManager.hub()` |
| Test app and group | `tests/apps/hello-nfc`, `tests/vm/nfc.py`, group `nfc` |

Without a source nothing changes: no `android.hardware.nfc` feature, `NfcAdapter.getDefaultAdapter()` is null, as on a phone without NFC (the group
checks it).

## Sources

- **Mock directory** `$APKLAYER_DATA/apklayer/nfc-mock/` (the instance's data dir; `apklayer env` prints `APKLAYER_DATA`): if the directory exists, the
  adapter is on and `*.tag` files are tags in the field (inotify, no polling). Create a file elsewhere in the directory and `mv` it to `name.tag`; delete
  it to take the tag away. One `key=value` per line:

  ```
  type=t2|t4|nfca     t2: NfcA + MifareUltralight + Ndef (an NTAG213: READ 30h, WRITE A2h, capability container E1 10 12 00, NDEF TLV)
                      t4: NfcA + IsoDep + Ndef (the NFC Forum Type 4 NDEF application: SELECT D2760000850101, files E103/E104, READ BINARY)
                      nfca: a bare NfcA tag (only TAG_DISCOVERED takes it)
  uid=04a1b2c3d4e5f6  atqa=4400  sak=00        (hex; defaults by type)
  ndef.text=en:Hello  ndef.uri=https://example.org  ndef.mime=text/plain:body     (one record each, in this order; none: an empty formatted tag)
  writable=0          Ndef.writeNdefMessage fails
  apdu=FFCA000000 DEADBEEF9000                 (t4, repeatable) a canned command and its answer, tried before the built-in application
  ```

  A text record dispatches as `NDEF_DISCOVERED` with type `text/plain`, as on Android (`NdefRecord.toMimeType`).
- **PC/SC**: pcscd's socket `/run/pcscd/pcscd.comm` exists. The adapter is off until a reader is present (`SCardListReaders`). The JDK's
  `java.smartcardio` needs libpcsclite (`apt install pcscd libpcsclite1`). A card is a tag: UID from `FF CA 00 00 00`; an ISO 14443-4 card (ATR
  without the PC/SC contactless storage-card marker) is NfcA + IsoDep, its APDUs go through as they are, and a Type 4 NDEF application is read once at
  discovery (Ndef, read-only); a storage card (Type 2) is NfcA, raw frames through the reader's direct-transmit pseudo-APDU `FF 00 00 00 Lc data`
  (ACR122U and PN532-based readers).

The mock wins when its directory exists.

## What is delivered, and in which order

`HubNfc.dispatch` follows `NfcDispatcher`: (1) the app in reader mode (`enableReaderMode`; the framework registers it while the activity is resumed, so
call it in `onResume`; a tag already in the field is delivered at once); (2) the foreground dispatch's PendingIntent: the NDEF intent if its filters match,
else TECH if a tech-list covers the tag, else TAG if its filters match (`tryOverrides`); (3) the system starts the activity of an installed app for
`NDEF_DISCOVERED` (first record's URI or MIME type as data/type), else `TECH_DISCOVERED` (an exported activity whose `android.nfc.action.TECH_DISCOVERED`
meta-data tech-list is covered by the tag's technologies), else `TAG_DISCOVERED`. Intents carry `EXTRA_TAG`, `EXTRA_ID`, `EXTRA_NDEF_MESSAGES`.

## Mocked, and what needs hardware

Mocked (tested on the UTM VM, `tests/run nfc`, no hardware): the tags (their memory and answers), the controller (always on, in-process), the field.
Everything the apps call is the framework's real code over a real binder service: reader mode and foreground dispatch registration through
`NfcActivityManager`, the discovery intents through the hub's activity start, `Tag` parcelling with the hub's tag binder across processes, NDEF read
and write, NfcA/MifareUltralight/IsoDep transceive.

Not tested, needs a reader: `HubNfcPcsc` (compiles and loads; no reader or pcscd on the test VM): UID read, ATR classification, Type 4 NDEF read, the
direct-transmit wrapper. The mock's Type 2/4 behaviour follows the specifications and a real NTAG/Type 4 card may differ in details (GET_VERSION,
PWD_AUTH, extended-length APDUs, timing, tag removal during an exchange).

## Not covered, and why

- **Kernel `nfcsim`** (`modprobe nfcsim` makes two virtual devices `nfc0`/`nfc1` that talk NFC-DEP to each other over the NFC netlink family) emulates
  peer-to-peer, not tags: no device on it presents a Type 2/4 tag. It would only serve LLCP/SNEP (Android Beam, removed from Android in 14) through
  neard. Not used.
- Host card emulation (`android.hardware.nfc.hce`, `CardEmulation`, payment), `NfcAdapter.setNdefPushMessage`/Beam, secure NFC, observe mode, polling
  pause, `NfcF`/`NfcV`/`NfcB`/`MifareClassic` tags from the mock, `Ndef.makeReadOnly`, `NdefFormatable.format`, `ignore()` (tag removal callbacks),
  `ACTION_ADAPTER_STATE_CHANGED`, a chooser when several apps match a tag (the first match starts), the `NFC` permission check of the caller.
- A phone's own NFC chip through the device HAL on Halium (hwbinder `android.hardware.nfc@1.x`): the host-HAL forwarding of docs/HIDL.md would carry it,
  but the NCI protocol and NfcService's role (polling loop, routing) are not implemented.
