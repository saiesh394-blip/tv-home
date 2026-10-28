# TV Home

An ad-free, minimal home screen for Android TV and Google TV, with an Apple TV-style look. No ads, no recommendations: just your photo, a greeting, and the apps you use.

Built for a Xiaomi Google TV with a modest graphics chip, so it's tuned to stay smooth on slower TVs.

**Download:** grab the latest `TVHome-x.y.apk` from the [download](download) folder.

## What it does

- **First-run setup:** a short welcome that asks your name and picture, then makes TV Home the Home app and gives it its permissions by itself, through the TV's own debugging connection. No computer needed (run it again from **Settings → Setup & Home App**).
- **Greeting:** "Good evening, *your name*", changing with the time of day, with your profile picture beside the clock.
- **Top Shelf:** big artwork behind the dock, like Apple TV. Choose popular **Movies & Shows** (with title logos, year, genre and rating; **Where to Watch** opens the TV's search) or **News** (Indian headlines from NDTV, The Times of India, The Hindu and The Indian Express). Cards change by themselves; Left/Right on the button flips through them. After 30 seconds without the remote, the rows and dock fade away so only the artwork shows. **Settings → Home Screen**.
- **Weather:** a pill in the top-left corner with an icon and the temperature. Press OK for details: feels like, high and low, UV, air quality, humidity, wind, sunrise, sunset and the next six hours. Pick any city, or use the TV's location.
- **App opening animation:** apps zoom out of their icon (or reveal, fade, rise or pop) at the speed you choose. **Settings → Appearance → App Opening**.
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

1. On the TV: **Settings → System → About**, press OK on **Build** 7 times. Then **Settings → System → Developer options → USB debugging** on. Note the TV's IP address under **Settings → Network & Internet**.
2. On your computer:
   ```
   adb connect <TV-IP>:5555
   adb install -r TVHome-x.y.apk
   ```
   Accept the prompt on the TV. "TV Home" now appears in your apps.

## Make it the home screen

The first-run setup does this for you (**Set Up Automatically**). To do it by hand from a computer instead:

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
- the weather and air quality, from Open-Meteo, using an approximate location from your IP address via ipapi.co (or a city you choose),
- the Top Shelf, if you turn it on: the free Cinemeta catalogue for movies and shows, or public news RSS feeds,
- apps you choose to install from f-droid.org or a link.

Voice search uses the TV's own speech service. Your name and settings stay on the TV. The automatic setup only talks to the TV itself (127.0.0.1) and runs a short, fixed list of commands.

## Credits

- Wallpapers: [Wikimedia Commons](https://commons.wikimedia.org) photographers (credited on screen).
- Weather and air quality: [Open-Meteo](https://open-meteo.com).
- Movie and show artwork: [Cinemeta](https://github.com/Stremio/stremio-addons) (metahub images).
- News: the publishers' own RSS feeds.
- Some icons are based on [Material Icons](https://fonts.google.com/icons) (Apache License 2.0).

App names and logos shown on the screen belong to their owners. TV Home is not affiliated with Google, Xiaomi, Sony or any streaming service.

## Licence

MIT. See [LICENSE](LICENSE).
