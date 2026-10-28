# Compatibility results (2026-10-28, UTM VM, device-library route)

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
| AnkiDroid | com.ichi2.anki | no window | Chromium startup from Application.onCreate throws (WebView statics before any WebView) |
| Puzzles (Simon Tatham) | name.boyle.chris.sgtpuzzles | exits | opens its Play Store page and finishes |
| Open Sudoku | org.moire.opensudoku | crashes | `duplicate column name: rating_level` creating its database |
| Rocks 'n' Diamonds | org.artsoft.rocksndiamonds | aborts | SDL_image: IMG_Load failed (asset read) |
| WireGuard | com.wireguard.android | draws | fixed by `android_get_device_api_level` in the translated libc (Go takes the Android Q TLS path) |
| DRM Info | com.androidfung.drminfo | crashes | Google Ads / Firebase need Play Services |
| Open Camera, Barcode Scanner, Motorola Camera | | no camera | no camera HAL |
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
