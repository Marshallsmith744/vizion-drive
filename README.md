# Vizion Drive

An independent Android car launcher for Tesla-style head units, written from scratch in Kotlin + Jetpack Compose.
See `docs/FEATURE_ANALYSIS.md` for the feature analysis, what's built, and the roadmap.

## Build (Android Studio)

1. Install **Android Studio** (Ladybug 2024.2 or newer). It bundles a JDK 17 and the Android SDK.
2. **File → Open** → select this `VizionDrive` folder. Let Gradle sync. It downloads Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21 and Compose.
   - If Studio says the Gradle wrapper is missing, accept its offer to use the wrapper from `gradle/wrapper/gradle-wrapper.properties`. Or run `gradle wrapper` once if you have Gradle installed.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
   - The release build (`assembleRelease`) is signed with the debug key so it installs straight away. Swap in your own keystore before you hand it to anyone.

## Install on the head unit

- USB debugging on: `adb install -r app-debug.apk`
- Or copy the APK to a USB stick and open it with the unit's file manager (allow "unknown sources").
- Press **Home** and choose **Vizion Drive → Always**. Or go to Settings → Apps & startup → *Make Vizion Drive the default launcher*.

## First-run checklist

| What | Where |
|---|---|
| Location permission (speedometer, compass, trip, weather) | asked on first launch |
| Nearby devices / Bluetooth permission (OBD, ambient light) | asked on first launch |
| Notification access (music widget) | tap the music widget, or Settings → Apps & startup |
| OBD-II: pair the ELM327 in Android Bluetooth settings (PIN 1234 / 0000), then pick it | Settings → OBD-II |
| Ambient LEDs: power the ELK-BLEDOM controller, **close the phone app that controls it**, then Scan | Settings → Ambient light |
| Navigation app | App drawer → long-press an app → *Use as navigation app* |
| Dock apps | App drawer → long-press → *Add to dock* (long-press a dock icon to remove) |
| Start an app after boot (e.g. Spotify) | App drawer → long-press → *Start after boot* |

## Run the tests

```
./gradlew test          # or: right-click app/src/test → Run 'Tests in drive'
```

`LogicTest.kt` covers OBD PID decoding, DTC parsing (CAN + legacy formats), ELK-BLEDOM command frames, trip/haversine maths, the dashboard layout model, speed-volume steps and weather codes. These 29 tests were compiled with Kotlin 2.0.21 and passed. The Android UI layer has **not** been compiled yet, because the sandbox it was written in had no access to Google's Maven repository. Expect to fix minor compile issues on first sync, if any.

## Manual test plan (on the head unit)

1. **Launcher:** press Home; Vizion Drive appears with no system bars. Back and Home close any open panel and never leave the launcher.
2. **Speed:** drive (or use a GPS mock app). The big number on the left matches the car's speed; switch km/h ↔ mph in Settings → Driving.
3. **Speed alert:** set 50 km/h, exceed it. You should get a beep, a toast, a red number and the round sign on the panel.
4. **Pages:** swipe the right side. Edit dash → add/remove/reorder widgets, add a page, apply a preset.
5. **Apps:** open the drawer, search, launch an app, add 3 apps to the dock.
6. **Media:** play Spotify/YouTube Music. The widget shows title/artist/art; play/pause/skip work; the dock gets a play/pause button.
7. **OBD:** connect the adapter with ignition on. RPM bar, coolant, battery, quad widget and Performance view fill in. Scan codes; clear only with the engine **off**.
8. **Ambient:** scan, connect, change colour; then change the accent colour and confirm the LEDs follow.
9. **Screensaver:** set 1 min, leave it idle. The clock appears; tap to return.
10. **Autostart:** set an app, reboot the unit. A countdown banner appears; Cancel works.

## Project layout

```
app/src/main/java/com/vizionconcepts/drive/
  HomeActivity.kt        HOME activity, permissions, alerts, autostart
  DriveApp.kt            Application + AppContainer (manual DI)
  settings/Settings.kt   DataStore-backed settings
  dashboard/             widget types, page layout model, presets
  obd/                   ELM327 protocol parser + Bluetooth SPP manager
  ambient/               ELK-BLEDOM frames + BLE manager
  vehicle/               GPS tracker + trip computer
  media/                 notification-listener media session hub
  weather/               Open-Meteo client
  system/                app list/launching, volume, alerts, voice
  ui/                    Compose UI: home, widgets, gauges, drawer, editor, settings
```

## Notes
- Weather uses Open-Meteo (free for non-commercial use, no key). Commercial use needs their paid plan.
- No accounts, analytics or tracking.
- Clean-room build: nothing from Car Nebula's code, assets or text is included.
