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
| TIC-80 | com.nesbox.tic | black window | game loop runs (SDL), landscape request |
| Super Retro Mega Wars | com.serwylo.retrowars | runs | not looked at |
| AnkiDroid | com.ichi2.anki | no window | Chromium startup from Application.onCreate throws (WebView statics before any WebView) |
| Puzzles (Simon Tatham) | name.boyle.chris.sgtpuzzles | exits | opens its Play Store page and finishes |
| Open Sudoku | org.moire.opensudoku | crashes | `duplicate column name: rating_level` creating its database |
| Rocks 'n' Diamonds | org.artsoft.rocksndiamonds | aborts | SDL_image: IMG_Load failed (asset read) |
| WireGuard | com.wireguard.android | aborts | Go runtime/cgo `inittls: could not find pthread key` (bionic TLS slots) |
| DRM Info | com.androidfung.drminfo | crashes | Google Ads / Firebase need Play Services |
| Open Camera, Barcode Scanner, Motorola Camera | | no camera | no camera HAL |
| Clash, SSR | | not supported | 32-bit (armeabi-v7a) only |
| Aegis | com.beemdevelopment.aegis | draws welcome | needed the app's BouncyCastle relocated |
| Mindustry | io.anuke.mindustry | draws its map | libgdx |
| Lemuroid | com.swordfish.lemuroid | runs, black window after 40 s | |
| OpenTTD | org.openttd.fdroid | aborts | `*** buffer overflow detected ***` (fortified libc call under the bionic translation) |
