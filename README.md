# Android TV: Debloat + Custom Launcher

Remove the bloatware and ads from an Android TV, and replace the stock home screen with a clean, Apple TV-style launcher with a rotating wallpaper. No root, no USB cable, and everything is reversible.

Written up end to end here: [How I Debloated My Android TV and Built My Own Launcher With Claude Code](https://princejain.me/blogs/debloat-android-tv-custom-launcher-claude-code).

![Home screen: frosted glass buttons over the rotating wallpaper, a favourites row running off the screen edge](docs/home.jpg)

![Inputs, Recent and All apps rows](docs/rows.jpg)

## Features

- **Apple TV-style look.** Frosted glass buttons, rows that run off the screen edge, focus shown by a lift and a glow in the app's colour (no thick borders), and the [Inter](https://rsms.me/inter/) font in text and display sizes.
- **Rotating wallpapers.** Random SFW 1080p art from [wallhaven.cc](https://wallhaven.cc) — no API key — mixed from movie, nature, scenic and mountain themes. A new wallpaper every 15 minutes, on every Home press, and via a refresh button in the header; a fresh batch every 6 hours. The last batch is cached on the TV, so it paints instantly on boot instead of blank.
- **Your apps, your order.** A favourites row and an all-apps row. Long-press any tile to **move it, add it to or remove it from favourites**, or open App info.
- **Recent** row of your most-used apps (favourites left out, since they're one row up).
- **HDMI inputs as tiles** on TVs, named after the connected device where the TV reports it (e.g. "Apple TV · HDMI 1"). Hidden on streaming boxes.
- **Header**: clock, greeting, date and weather ([Open-Meteo](https://open-meteo.com/), no key needed), plus Picture, Sound, Wi-Fi and Settings buttons.
- **OLED care**: pure black background, the layout drifts a few pixels every two minutes, and the screen dims after a minute of no input and 25% at night. One switch turns it off on LCD TVs.

## What's inside

```text
debloat/
  debloat.sh                hide every app in a list (reversible)
  restore.sh                bring one app, or all of them, back
  packages.txt              your removal list (empty template)
  examples/xiaomi-tv.txt    the 32 packages removed from a Xiaomi OLED TV
launcher/                   the home screen (Kotlin + Jetpack Compose for TV)
  local.properties.example  SDK path (the wallpaper engine needs no key)
  deploy.sh                 build + install + re-grant permissions in one command
  third_party/Inter-OFL.txt licence for the bundled Inter font
```

## What you need

- An Android TV or Google TV (a TV with Android built in, or a streaming box) on Android 9 or later.
- A laptop on the same Wi-Fi.
- [ADB](https://developer.android.com/tools/adb) (`brew install --cask android-platform-tools` on a Mac).
- For the launcher: [Android Studio](https://developer.android.com/studio) (free), which provides the JDK and Android SDK. You never have to open it.
- Optional but recommended: a coding agent such as [Claude Code](https://docs.claude.com/en/docs/claude-code/overview) to do all of this for you.

## The easy way: let a coding agent do it

Open your coding agent in this folder and paste:

```text
I want to debloat my Android TV and install the custom launcher in this repo. This is a TV I own.
Developer mode and USB debugging are on. TV IP: <your TV's IP>. Same Wi-Fi as this laptop.
Apps I use: <e.g. Netflix, Prime Video, YouTube>. Country: <e.g. IN>.

Read README.md and CLAUDE.md first. Connect over ADB, list every installed app grouped by category,
and mark what's bloat, what's safe to keep and what must never be removed. Ask me before removing
anything. Then set my favourites, build the launcher, install it and make it the home screen.
```

## The manual way

### 1. Turn on developer mode (on the TV)

1. **Settings → Device Preferences → About**, click **Build** seven times.
2. **Developer options** → turn on **USB debugging**, and **Wireless debugging** if your TV has it.
3. Note the TV's IP from **Settings → Network**, or from the Wireless debugging screen.

Then connect from your laptop. Which command depends on the TV:

```bash
# Most Android TVs (Android 9–11): USB debugging also opens network debugging on port 5555
adb connect <tv-ip>:5555

# TVs with "Wireless debugging" (Google TV, Android 11+): pair once, then connect to the port it shows
# On the TV: Wireless debugging → "Pair device with pairing code" shows an IP:port and a 6-digit code
adb pair <tv-ip>:<pairing-port>        # enter the 6-digit code
adb connect <tv-ip>:<port>             # the port on the main Wireless debugging screen
```

The first time, the TV asks **"Allow USB debugging?"** (or wireless debugging). Tick **"Always allow from this computer"** and press Allow. If you connected with a port other than 5555, pass `<tv-ip>:<port>` wherever this README says `<tv-ip>`.

### 2. Debloat

See what's installed, then put the packages you want gone in `debloat/packages.txt` (one per line). If you have a Xiaomi TV, start from `debloat/examples/xiaomi-tv.txt`.

```bash
adb -s <tv-ip>:5555 shell pm list packages        # everything
adb -s <tv-ip>:5555 shell pm list packages -3     # apps you (or the manufacturer) installed

cd debloat
./debloat.sh <tv-ip>                              # uses packages.txt
./debloat.sh <tv-ip> examples/xiaomi-tv.txt       # or any list
```

Apps are removed with `pm uninstall -k --user 0`, which hides them from your user profile without deleting them from the system. **Never remove** Live TV, chip/vendor services (e.g. `com.mediatek.*`), the remote's Bluetooth service, TV settings, Play Services or the keyboard: doing so can break the remote, the inputs or booting.

### 3. Configure and build the launcher

```bash
cd launcher
cp local.properties.example local.properties
```

Edit `local.properties`:

| Setting | What it does |
|---|---|
| `sdk.dir` | Path to the Android SDK (or set `ANDROID_HOME` instead) |

`local.properties` is gitignored. The wallpaper engine (wallhaven.cc) needs no key or further configuration. Then build:

```bash
./gradlew assembleRelease
```

Always use the **release** build: it's optimised with R8, and debug Compose builds are noticeably slow on TV hardware.

### 4. Install

One command does all of it — build, install, re-grant the permissions MIUI revokes on reinstall, and speed-compile for a faster cold start:

```bash
./deploy.sh <tv-ip>
```

Or by hand:

```bash
S=<tv-ip>:5555                    # or <tv-ip>:<port> if you used Wireless debugging
adb -s $S install -r app/build/outputs/apk/release/app-release.apk
adb -s $S shell pm grant me.prince.launcher android.permission.ACCESS_COARSE_LOCATION   # Wi-Fi name + weather
adb -s $S shell appops set me.prince.launcher GET_USAGE_STATS allow                       # Recent row
adb -s $S shell cmd package compile -m speed -f me.prince.launcher                       # faster cold start
adb -s $S shell am start -n me.prince.launcher/.MainActivity                             # try it first
```

### 5. Make it the home screen

Only once the launcher works:

```bash
adb -s $S shell cmd package set-home-activity me.prince.launcher/.MainActivity
adb -s $S shell input keyevent KEYCODE_HOME
```

If Home still opens the stock launcher, disable it (disabled, not uninstalled, so it can come back). Read the "Ok Google" note in [Gotchas](#gotchas) first.

```bash
adb -s $S shell pm disable-user --user 0 com.google.android.tvlauncher
```

## Customise

- **Favourites:** long-press a tile → Move / Add to favourites / Remove. The order is saved on the TV. The defaults for a fresh install are at the top of `Apps.kt`.
- **OLED care:** set `OLED_CARE = false` at the top of `HomeScreen.kt` on an LCD TV.
- **Wallpaper themes:** edit `WALLPAPER_THEMES` at the top of `Trending.kt` (movie, nature, scenic, mountain by default; any wallhaven.cc search term works).
- **Font:** replace `res/font/inter.ttf` with any variable font that has `wght` and `opsz` axes.

## Undo everything

```bash
cd debloat
./restore.sh <tv-ip> <package>   # bring one app back
./restore.sh <tv-ip>             # bring back everything in packages.txt and re-enable the stock launcher
adb -s <tv-ip>:5555 uninstall me.prince.launcher
```

If the TV ever shows a black screen on Home, `adb -s <tv-ip>:5555 shell pm enable com.google.android.tvlauncher` brings the stock launcher back.

## Gotchas

- **The stock launcher wins Home.** It's a system app with a higher priority than yours, so `set-home-activity` alone often doesn't stick. Disable it, but only after your launcher is installed and tested, or you get a black screen.
- **Disabling the stock launcher can break "Ok Google"** on TVs with a far-field (hands-free) mic. Android 11+ only lets a background app use the microphone if an app in the foreground lends it that permission, and the stock launcher was doing that for Google Assistant. This repo doesn't include a fix. The voice button on the remote keeps working either way. If hands-free "Ok Google" matters to you, check it after disabling the stock launcher (logcat shows `Request denied by app op: 27` when it's broken), or ask your coding agent to add a microphone-type foreground service that binds the Assistant with `BIND_INCLUDE_CAPABILITIES`.
- **The Wi-Fi button says "Wi-Fi" instead of your network name**, and **there's no weather.** Both need location permission: run the `pm grant ... ACCESS_COARSE_LOCATION` line above. Weather also needs the TV to know its location; some streaming boxes don't, and then it's simply hidden.
- **VPN ad blockers.** Apps like AdGuard run as a VPN, which makes the VPN the "active network". The launcher checks every network, so the Wi-Fi label still works.
- **Firmware updates** can bring removed apps back. Re-run `./debloat.sh <tv-ip>`.

## Credits

- Wallpapers from [wallhaven.cc](https://wallhaven.cc).
- Weather from [Open-Meteo](https://open-meteo.com/).
- [Inter](https://rsms.me/inter/) by Rasmus Andersson, under the SIL Open Font License (`launcher/third_party/Inter-OFL.txt`).

## Licence

[MIT](LICENSE) for the code. The bundled Inter font is under the SIL Open Font License.
