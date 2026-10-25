# TV Home

An ad-free, minimal home screen for Android TV and Google TV, with an Apple TV-style look. No ads, no recommendations: just your photo, a greeting, and the apps you use.

Built for a Xiaomi Google TV with a modest graphics chip, so it's tuned to stay smooth on slower TVs.

**Download:** [TVHome-6.24.apk](download/TVHome-6.24.apk) (open it, then click the download button on the right).

## What it does

- **Greeting:** "Good evening, *your name*", changing with the time of day, with the weather beside the clock. It asks your name on first launch (change it in **Controls → Appearance**).
- **Photo wallpaper:** free featured scenery from Wikimedia Commons that changes with the time of day, or your own photos.
- **Mac-style dock:** your favourite apps along the bottom, magnifying as you move across them. The selected app's colour washes softly into the photo.
- **All apps:** sorted by how often you use them. Hold OK on any app to add it to the dock, move it, hide it, or open App info.
- **HDMI inputs:** a slim **Inputs** row that switches the TV straight to HDMI 1, HDMI 2 and so on. Rename one (say, "PS5") and add it to the dock.
- **Search with voice:** search apps and YouTube; **Speak** shows Siri-style colour waves while you talk.
- **Control Centre:** an Apple-style cluster in the top-right corner over a blurred home screen: a big StandBy tile, Wi-Fi and Bluetooth with their status, Inputs, Settings, and round buttons for voice search, Screen Mirror, the screensaver and **More** (Install apps, Remap buttons, Hidden apps, Appearance, Picture, Sound, Storage). Tiles take their tint from your wallpaper.
- **Install apps:** Aurora Store and F-Droid from f-droid.org, an APK link, or an APK from a USB drive, always through Android's own installer.
- **Quiet and smooth:** no click sounds when moving around, night dimming, a screensaver.
- **Continue Watching:** a row of what you were watching in apps that share it with Android TV (YouTube, Netflix, Prime Video and others), with progress bars. TV Home asks once for permission to read it.
- **Recent:** the last few apps you opened, above All apps.
- **Folders:** hold OK on an app → **Add to folder…** to group apps (say, Streaming or Utilities). Folders open in a frosted glass panel.
- **Send from phone:** **Controls → Appearance → Wallpaper: Send from phone…** shows a link; open it on your phone (same Wi-Fi), pick a photo, and it becomes the wallpaper. The link has a one-time code and closes after one photo or 10 minutes.
- **Remap buttons:** **Controls → More → Remap buttons** makes a remote button (Netflix, Prime Video, colour keys…) open any app or HDMI input. Needs accessibility access, switched on once in Settings → Accessibility or with adb (TV Home shows the command). Arrows, OK, Back, Home, volume and power are never changed.
- **StandBy:** six bedside styles like iPhone StandBy (Analogue + Calendar, Modern + Weather, Date + Weather, Analogue, Float, Digital). Open it from **Controls → StandBy**; Left/Right switch styles, any other button goes back.
- **Glass panels:** Control Centre and folders use a frosted look made from your wallpaper (worked out once, so it stays smooth).
- **Settings:** a full Settings screen (the gear at the end of the dock, or **Controls → Settings**) in a Classic or Glass style.
- **Icon styles:** Original, Light (Apple TV), Dark or Glass tiles for the dock icons.
- **Icon packs:** **Controls → Appearance → Icon pack** restyles the dock icons with any installed icon pack (ADW / Nova format).

## Install

You need a Mac or PC with `adb` ([Android platform tools](https://developer.android.com/tools/releases/platform-tools)) on the same Wi-Fi as the TV.

1. On the TV: **Settings → System → About**, press OK on **Android TV OS build** 7 times. Then **Settings → System → Developer options → USB debugging** on. Note the TV's IP address under **Settings → Network & Internet**.
2. On your computer:
   ```
   adb connect <TV-IP>:5555
   adb install -r TVHome-6.24.apk
   ```
   Accept the prompt on the TV. "TV Home" now appears in your apps.

## Make it the home screen

Google TV doesn't offer a choice of home app, so set it with adb:

```
adb shell cmd package set-home-activity com.home.tvhome/.MainActivity
```

If the stock home still comes back, find it and switch it off:

```
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

**To undo:** `adb shell pm enable com.google.android.apps.tv.launcherx` (use the package name you disabled). Nothing is deleted.

## Your own wallpapers

**Controls → Appearance → My photos** uses pictures from `Pictures/TV Home` on the TV. Optional subfolders `Morning`, `Afternoon`, `Evening` and `Night` are used at those times.

```
adb shell mkdir -p "/sdcard/Pictures/TV Home/Evening"
adb push sunset.jpg "/sdcard/Pictures/TV Home/Evening/"
```

## Build it yourself

Open this folder in Android Studio and choose **Build → Build APK(s)**. Plain Java, no extra libraries.

To sign updates with your own key (so new builds install over old ones), create `keystore.properties` in this folder. It is ignored by git, so it never gets uploaded:

```
storeFile=keystore/your-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Privacy

TV Home has no accounts, ads or tracking. It only goes online for:

- wallpapers from Wikimedia Commons,
- the weather, from Open-Meteo, using an approximate location from your IP address via ipapi.co,
- apps you choose to install from f-droid.org or a link.

Voice search uses the TV's own speech service. Your name and settings stay on the TV.

## Credits

- Wallpapers: [Wikimedia Commons](https://commons.wikimedia.org) photographers (credited on screen).
- Weather: [Open-Meteo](https://open-meteo.com).
- Some icons are based on [Material Icons](https://fonts.google.com/icons) (Apache License 2.0).

App names and logos shown on the screen belong to their owners. TV Home is not affiliated with Google, Xiaomi, Sony or any streaming service.

## Licence

MIT. See [LICENSE](LICENSE).
