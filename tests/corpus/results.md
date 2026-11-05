# Compatibility results

## 2026-11-05 (current F-Droid versions, index of 2026-11-05; UTM and Parallels, instances corpus1/corpus2, both from this tree's Meson build)

Every app on both VMs: install, launch, the app's own screenshot once its frames settle, a tap at the window centre, Back, a screenshot after each,
`apklayer logs <pkg>`. "both" = the same result on UTM and Parallels. The 13 apps below the line are new to the corpus. The non-F-Droid entries of
apps.txt (OTR2, Telegram, WhatsApp, CPU-Z, ...) were not part of this round.

| App (version) | Kind | VM | State | Note |
|---|---|---|---|---|
| NewPipe 0.29.1 | Java, media | both | draws (Live list with thumbnails, "Keep Android Open" dialog) | the Trending list fills now; Back leaves the dialog (it is not cancelable) |
| Nextcloud 35.0.2 | Java, network | both | draws (login) | |
| GPSTest 3.10.6 | location | both | draws (Status, What's new); Back closes it, "GPS disabled" dialog follows | |
| OpenVPN 0.7.69 | native | both | draws (profiles) | |
| Shelter 1.9.1 | Kotlin | both | draws (welcome) | |
| Unciv 4.22.16 (was 4.22.15) | libgdx | Parallels | draws (language picker); the tap selects a language | |
| | | UTM | black window | frames arrive, every pixel 0; GL context on virgl (ES 3.2 Mesa 26) and rulesets load, no error (open) |
| Feeder 2.25.0 | Compose, RSS | both | draws (All feeds); the tap opens an article, Back returns | |
| Tusky 33.1 | Kotlin views | both | draws (login) | |
| Breezy Weather 6.2.2 | Compose | both | draws (Locations); the tap opens the search, Back returns | |
| Auxio 4.1.5 | media3 | both | draws (Songs) | |
| Loop Habit Tracker 2.3.2 | SQLite | both | draws (welcome); Back reaches the habit list | |
| LocalSend 1.18.2 | Flutter | both | draws (Receive) | |
| Standard Notes 3.202.11 | React Native, WebView | both | draws (notes, account sheet) | the first screenshot is black while the WebView starts |
| KeePassDX 4.5.8 (was 4.5.6) | native crypto | both | draws (create/open database) | |
| Tasks.org 15.12 | Compose, Room | both | draws (welcome) | |
| Thunderbird 24.0 | Compose | both | draws (welcome) | |
| Element 1.6.62 | Rust | both | draws (welcome carousel; the tap moves it) | |
| VLC 3.7.2 | native player | both | draws (welcome) | |
| LibreTube 32.1 | media3 | both | draws (Trending); the tap opens a video page | the player stays black, `FileNotFoundException: ""` from media3's FileDataSource (open) |
| Material Files 1.7.5 | file manager | both | draws (Internal storage); the tap opens a folder | getfilecon ENODATA logged (known: no SELinux labels) |
| mpv 2026-09-17 | native, GL | both | draws; the tap opens "Open URL" | |
| Organic Maps 2026.09.29 | native maps | both | draws (map download) | |
| OsmAnd 5.4.11 | maps | both | draws (welcome) | StrictMode reports MapActivity as a "non-UI Context" (open) |
| FluffyChat 2.11.4 (was 2.11.3) | Flutter | both | draws (welcome) | |
| Aurora Store 4.8.4 | Compose | both | draws (welcome) | 2026-11-06 Parallels: onboarding (restart, install permission), anonymous login, app pages; downloads were cancelled by WorkManager's network constraint (fixed, not yet re-run) |
| F-Droid 2.0.2 | store client | both | draws (Discover, fetching repositories) | 2026-11-06 Parallels: index loaded, Search > Calculator lists apps, Install works (checked by the owner) |
| Fossify Gallery 1.14.1 | MediaStore | both | draws (no media) | |
| Fossify Calendar 1.12.1 | CalendarContract | both | draws (month); the tap opens the day | |
| Fossify Contacts 1.6.0 | ContactsContract | both | draws (no contacts) | |
| Fossify File Manager 1.6.3 | file manager | both | draws (Internal); the tap opens Movies | |
| Catima 2.45.2 | SQLite | both | draws (welcome) | |
| Fossify Camera 1.5.0 | CameraX | both | draws controls, black preview | no camera on the VMs |
| AntennaPod 3.12.3 | media3 | both | draws (Home) | |
| Jellyfin 2.7.3 | WebView | both | draws (Connect to Server); the tap opens Downloads | |
| Termux 0.119.0-beta.3 | terminal | both | "Bootstrap Error" dialog | known (uid 1000, PLAN next phase 1) |
| StreetComplete 64.1 (was 64.0) | Compose MP, MapLibre (JavaCPP) | both | draws (welcome); dies when the map renders | fixed: SIGSEGV in MaplibreNativeC event_mask(J) (NativeLoader bound the getter's `__` symbol to the setter); then `Bitmap.createBitmap(Picture)` → Robolectric's createHardwareBitmap gets no ImageReader image (NPE, open) |
| Amaze 3.11.4 | file manager | both | draws (folders); the tap opens one | logback appenders missing (the app's own log config, harmless) |
| Etar 1.0.57 | CalendarContract | both | draws (week); the tap adds an event slot | fixed: canScheduleExactAlarms was Robolectric's false, so it opened REQUEST_SCHEDULE_EXACT_ALARM and died on ActivityNotFoundException |
| DAVx5 4.5.21 | Compose, accounts | both | draws (intro) | |
| Syncthing-Fork 2.1.7.0 | Go native | both | draws (welcome) | StrictMode "non-UI Context" reports as OsmAnd |
| Briar 1.5.24 (was 1.5.23) | Tor, H2 | both | splash only | regression since 2026-11-04 (1.5.23 stays on the splash too): NavDrawerActivity's startActivity(StartupActivity) is never launched (open) |
| QUIK 4.3.7 | SMS | both | dies at start | RoleManager's REQUEST_ROLE (SMS) intent has no handler: ActivityNotFoundException (open) |
| SimpleX Chat 7.0.4 | Haskell core | both | draws (welcome "Be free") | was a white window on 2026-11-04 |
| Wikipedia r/50614 | WebView, Room | both | draws (onboarding); Back reaches the Explore feed with articles | |
| Nextcloud Notes 35.0.0 | notes with sync (SSO) | both | draws (welcome, Choose account); Back reaches the empty list | |
| Librera Reader 10.0.22 | e-book reader, MuPDF | both | draws (library with the bundled book); Back opens its preferences panel | |
| Conversations 2.21.0 | XMPP | both | draws (Join the Conversation) | |
| Andor's Trail 0.8.18 | 2D game, Canvas | both | draws (title; the tap shows its menu) | |
| Trail Sense 8.2.0 | tool, sensors | both | draws (Explore intro) | |
| LibreTorrent 4.1.1 | libtorrent | both | draws (No torrents) | |
| Fulguris 2.1.2 | WebView browser | both | draws (welcome) | |
| Markor 2026.10.0 | Markdown editor | both | draws (intro) | |
| OpenTracks 4.29.0 | GPS tracker | both | draws (intro) | |
| Fossify Voice Recorder 1.7.4 | microphone | both | draws (recorder, folder confirmation dialog) | |
| CoMaps 2026.10.05 | native maps | both | draws (map download) | |
| App Manager 4.1.1 | package tool | both | draws (KeyStore password dialog) | fixed: setAccessible on SecretKeySpec.key (java.base was not open to app code) |
| OpenKeychain 6.0.4 | OpenPGP | both | draws (create key) | |

## 2026-11-04, second round (current F-Droid versions; UTM instance k3, Parallels k1/k2; screenshot after the frames stop for 3 s)

| App (version) | Kind | VM | State | Note |
|---|---|---|---|---|
| Aurora Store 4.8.4 | Compose, store client | Parallels | draws (welcome) | |
| F-Droid 2.0.2 | store client | Parallels | draws (Discover, "Fetching app repositories") | the index download (foreground worker) had not finished within the run; no error logged |
| Fossify Gallery 1.14.1 | MediaStore | UTM | draws (folders, "No media files") | |
| Fossify Calendar 1.12.1 | CalendarContract | both | draws (month) | |
| Fossify Contacts 1.6.0 | ContactsContract | both | draws (contacts, empty) | |
| Fossify File Manager 1.6.3 | file manager | Parallels | draws (Internal, folder list) | |
| Fossify Camera 1.5.0 | CameraX | Parallels | draws controls, black preview | no camera HAL (parked) |
| Catima 2.45.2 | SQLite | both | draws (welcome) | |
| AntennaPod 3.12.3 (was 2026-10-28) | media3 | Parallels | draws (Home) | |
| Jellyfin 2.7.3 | WebView | UTM | draws (Connect to Server) | needs com.android.webview in the data home |
| Wikipedia r/50614 | WebView, Room | UTM | draws (onboarding) | needs com.android.webview in the data home |
| Termux 0.119.0-beta.3 | terminal | Parallels | draws its "Bootstrap Error" dialog | hard-coded /data/data/com.termux/files: needs a /data/data/<pkg> view of the app's data dir (open) |
| StreetComplete 64.0 | Compose Multiplatform | Parallels | draws (welcome) | |
| Amaze 3.11.4 | file manager | UTM | draws (/storage/emulated/0, folders) | header says "8 folders" for 4 (not investigated) |
| Etar 1.0.57 | CalendarContract | Parallels | draws (week) | |
| DAVx5 4.5.21 | Compose, accounts | UTM, Parallels | draws (intro) | fixed: Robolectric 0016 (AuthenticationRequiredException failed instrumentation: NegativeArraySizeException in Application.onCreate) |
| Syncthing-Fork 2.1.7.0 | Go native | Parallels | draws (welcome) | |
| Briar 1.5.23 | Tor, H2 | UTM, Parallels | draws (nickname setup) | the first-frame screenshot showed its splash: the runner now waits for the frames to stop |
| QUIK 4.3.7 | SMS | Parallels | draws (inbox, empty) | |
| SimpleX Chat 7.0.4 | Compose, Haskell core | UTM | white window, no activity started | fixed: its libraries link now (bionic_translation 0032, sibling symbols); then libapp-lib.so loads (18 natives) but no activity starts within the run (the Haskell runtime start is the suspect, open) |

## 2026-11-04 (current F-Droid versions; UTM and Parallels, one instance per runner: `CORPUS_INSTANCE=<id> tests/corpus/run.py`; judged on each app's own SurfaceFlinger capture)

| App (version) | Kind | VM | State | Note |
|---|---|---|---|---|
| Feeder 2.25.0 | Compose, Room | UTM | draws (All feeds) | |
| Tusky 33.1 | Kotlin views | UTM | draws (login) | |
| Breezy Weather 6.2.2 | Compose | UTM | draws (Locations) | |
| Auxio 4.1.5 | media3 | UTM | draws (Songs) | |
| Loop Habit Tracker 2.3.2 | SQLite | UTM | draws (welcome) | |
| KeePassDX 4.5.6 | native crypto | UTM | draws (vault chooser) | |
| Tasks.org 15.12 | Compose, Room | UTM | draws (welcome) | |
| Thunderbird 24.0 | Compose | Parallels | draws (welcome) | |
| mpv 2026-09-17 | native player, GL | Parallels | draws (start screen) | |
| Organic Maps 2026.09.29 | native maps, GL | Parallels | draws (map download) | |
| OsmAnd 5.4.11 | maps, GL | Parallels | draws (welcome) | |
| LocalSend 1.18.2 | Flutter | Parallels | draws (Receive) | |
| Standard Notes 3.202.11 | React Native + WebView | UTM | draws (notes, account sheet) | needs com.android.webview in the data home (black without it) |
| Element 1.6.62 | Kotlin, Rust | UTM, Parallels | draws (welcome) | fixed: AndroidKeyStore getEntry(alias, null) |
| VLC 3.7.2 | native player | UTM | draws (welcome) | fixed: liblog's own stderr copy (VLC reads fd 2 back: the JVM hung); the runner's "launch" check misses it (VLC sends stdout to its log) |
| LibreTube 32.1 | media3 | UTM | draws (Home, loading) | |
| Material Files 1.7.5 | file manager | UTM, Parallels | draws (Internal storage, 5 folders with rows) | needs a native runtime with the real Os.strerror (`tools/build native`): with an older shim every getfilecon ENODATA threw UnsatisfiedLinkError on its AsyncTask (no rows / "Loading") |
| FluffyChat 2.11.3 | Flutter (Impeller) | UTM, Parallels | draws (welcome screen), second launch too | Impeller falls back to GLES by itself; black was Dart FFI: dlsym(RTLD_DEFAULT, "calloc") failed (bt 0031); dart:io TLS needs system/etc/security/cacerts (tools/stage root) |
| Nextcloud 35.0.2 (was 3.27, 2026-01) | Java | UTM, Parallels | draws (login) | fixed: hub attach at the first account use (attachBaseContext) |
| OpenVPN 0.7.69 (was 196, 2022) | native | UTM | draws (profiles) | |
| Shelter 1.9.1 (was 20, 2022) | Kotlin | UTM, Parallels | draws | fixed: dex2jvm constructors R8 names but deleted |
| GPSTest 3.10.6 (was 2020) | location | UTM, Parallels | draws (Status, What's new) | fixed: GnssCapabilities never null |
| Unciv 4.22.15 | libgdx | Parallels | draws (loading splash) | |

## 2026-11-03 (UTM VM, after the parallel round; judged on each app's own SurfaceFlinger capture)

| App | State | Note |
|---|---|---|
| UiBench, Nextcloud (login), Telegram (intro), NewPipe, CPU-Z, Device Info HW, WireGuard, OpenVPN, Shelter, Sensors Multitool, Vibrations, GPS Test, OpenCalc, 2048, Tasks | draw | first frame within 30 s, content check passes |
| Conversations | welcome screen | AndroidCAStore in HostTrust |
| ASTRO | consent screen | Robolectric 0015 (no looper idle in changeDisplay) |
| AnkiDroid | welcome screen | |
| Puzzles | game chooser; games generate | bionic executables through btload (exec_preload), navigateUpTo |
| Meet | welcome screen, "Failed to load accounts" | Sign In opens microG's LoginActivity; the Google sign-in needs a human check |
| microG | settings list | |
| Aegis, AntennaPod, Mindustry, Shattered Pixel Dungeon, Klooni | draw | |
| Logseq | welcome page | needs com.android.webview installed in the same data home |
| Rocks 'n' Diamonds | menu | |
| Lemuroid | Home screen | startService from another thread returns before onCreate |
| TIC-80 | console | the earlier crash was a stale build |
| Joplin | notes list, responds | |
| OTR2 | renders | inactive windows stay resumed |
| Fennec | pages draw, text fills missing on virgl | host GPU bug (tests/vm/gles-int-branch.c) |
| Unciv | black | draws nothing into its own surface (open) |
| OpenTTD | no video driver | needs GLES1 (open) |
| DRM Info | runs | needs Play Services: out of scope |
| Open Camera, barcode scanners | no camera | camera HAL is last priority |

## 2026-10-28 (UTM VM, device-library route)

One run per app (tests/vm/appcheck.sh for errors, tests/vm/appshot.py for a screenshot). "Draws" = a screenshot shows the app's own UI.

| App | Package | State | Notes |
|---|---|---|---|
| Google Meet 377 | com.google.android.apps.tachyon | draws welcome screen | account list needs the microG sign-in |
| Telegram FOSS | org.telegram.messenger | draws login (phone number) | intro illustration blank (TextureView) |
| WhatsApp 2.21.9 | com.whatsapp | draws, "Date is inaccurate" | too old a build for today's date; needs a newer APK |
| CPU-Z | com.cpuid.cpu_z | draws | no cpufreq on the VM |
| Nextcloud | com.nextcloud.client | draws login | |
| OpenVPN for Android | de.blinkt.openvpn | draws | |
| Shelter | net.typeblog.shelter | draws | work profile features are out of scope |
| Sensors Multitool | com.wered.sensorsmultitool | restarts its MainActivity over and over | its launch intent (`xflg=0x4`) is fired again by the app on every start (`-Dapklayer.tracestart=true` prints it); Battery page blank |
| NewPipe | org.schabi.newpipe | draws, Trending list empty | no error in the log; network works |
| ASTRO | com.metago.astro | splash only after 20 s | |
| OpenCalc | com.darkempire78.opencalculator | draws | |
| BobBall | org.bobstuff.bobball | draws | |
| 1010! Klooni | dev.lonami.klooni | draws | libgdx |
| Shattered Pixel Dungeon | com.shatteredpixel.shatteredpixeldungeon | draws | libgdx |
| Anarch RE | dev.serwin.AnarchRE | draws | |
| Anuto TD | ch.logixisland.anuto | draws | |
| Pekka Kana 2 | org.pgnapps.pk2 | draws | |
| PianOli | com.nicobrailo.pianoli | draws | |
| AntennaPod | de.danoeh.antennapod | draws | |
| 2048 (Privacy Friendly) | org.secuso.privacyfriendly2048 | draws | |
| Unciv | com.unciv.app | black window | starts, no error |
| TIC-80 | com.nesbox.tic | draws its console | |
| Super Retro Mega Wars | com.serwylo.retrowars | runs | not looked at |
| AnkiDroid | com.ichi2.anki | works (deck screen, Rust backend) | fixed by: startActivityIfNeeded, grace for the launcher trampoline, `Build.FINGERPRINT` no longer "robolectric" (Anki skipped its native library on it) |
| Puzzles (Simon Tatham) | name.boyle.chris.sgtpuzzles | exits | opens its Play Store page and finishes |
| Open Sudoku | org.moire.opensudoku | crashes | the app's own dex builds `CREATE TABLE puzzles` with `rating_level` twice (checked in the original classes.dex: the converter is faithful), so this F-Droid build fails on a fresh install on any SQLite |
| Rocks 'n' Diamonds | org.artsoft.rocksndiamonds | aborts | SDL_image: IMG_Load failed (asset read) |
| WireGuard | com.wireguard.android | draws | fixed by `android_get_device_api_level` in the translated libc (Go takes the Android Q TLS path) |
| DRM Info | com.androidfung.drminfo | crashes | Google Ads / Firebase need Play Services |
| Open Camera, Barcode Scanner | | no camera | no camera HAL |
| Clash, SSR | | not supported | 32-bit (armeabi-v7a) only |
| Aegis | com.beemdevelopment.aegis | draws welcome | needed the app's BouncyCastle relocated |
| Mindustry | io.anuke.mindustry | draws its map | libgdx |
| Lemuroid | com.swordfish.lemuroid | runs, black window after 40 s | |
| OpenTTD | org.openttd.fdroid | aborts | `*** buffer overflow detected ***` (fortified libc call under the bionic translation) |


## linux.apk's 31-app corpus, run on this route (2026-10-28, arm64 builds of the same packages)

| App | State |
|---|---|
| OpenCalc, SudoQ, Unit Converter Ultimate, Simple Solitaire, Mastodon, Floating Mute | run |
| Wikipedia (Views + Compose), Thunderbird, Aurora Store, F-Droid, Read You, Droid-ify, VLC | draw their UI (Read You: a Regex group index error in one SVG helper) |
| Tomato (org.nsh07.pomodoro), StreetComplete, FlorisBoard, Jellyfin for Android TV | run, not looked at |
| AntennaPod, Mindustry, Shattered Pixel Dungeon | draw |
| Saber, Ente Auth (Flutter) | draw |
| Open Sudoku | crashes: its CREATE TABLE has `rating_level` twice |
| Joplin (React Native) | blank white window; libQuickCrypto.so does not link (its OpenSSL libssl against the device's BoringSSL libcrypto) |
| Logseq (Capacitor, WebView) | draws its welcome page (it was our own regression: manifest providers in the WebView renderer host) |
| Pomodorot (Godot 4) | loads, then stops: VK_KHR_android_surface not found (Vulkan presentation is not available on this host stack) |
| Unciv | black window |
| NewPipe | Trending list stays empty |
| OpenTTD | past the libc fortify abort, then "Couldn't find any suitable video driver" |
| Fennec F-Droid (GeckoView) | SIGSEGV in libmozglue (GeckoLoader.putenv) |
| Rethink DNS | SIGSEGV in its Go runtime (like WireGuard) |
