# Car Nebula — Feature Analysis & Vizion Drive Implementation Plan

Target analysed: **Car Nebula** (`com.khaledisim.carlauncher`) v6.6.4 (versionCode 277), pulled from a Pixel 8 as `base.apk` + `split_config.arm64_v8a.apk`, `split_config.en.apk`, `split_config.xxhdpi.apk`.

Analysis date: 10 Nov 2026. Tools: REA 6.8.0 on Windows with JADX headless 0.7.1 (static decompiler metadata), REA APK signature inspection, and androguard 4.1.4 (resource-table string extraction). No code from the APK was executed, copied, or reproduced.

---

## 1. How this analysis was done (and its limits)

| Evidence source | What it gave us | Confidence |
|---|---|---|
| Manifest (JADX via REA) | Package, SDK levels, 51 permissions, all activities/services/receivers/providers | Observed |
| Class inventory (JADX, `com.khaledisim.carlauncher.*`) | ~800 unobfuscated data/service/ViewModel class names → feature areas | Observed |
| Enums (`ScreenType`, `FeatureType`, `ButtonActionType`) | Dashboard screen types, paid feature flags, button actions | Observed |
| `resources.arsc` strings (5,271 entries, ~600 app-owned groups) | Every visible screen title, setting, widget name and option | Observed |
| Native libs in arm64 split + `assets/` | Which engines power maps, routing, on-device AI, ADAS | Observed |
| APK signing block | v2+v3 signer cert SHA-256 `5d8fdcd1…5bc1`, Play source stamp present | Declared (not cryptographically verified) |

**Not done, deliberately:** reading the obfuscated Compose UI code, reading business logic line by line, extracting any assets (map themes, images, ML models, sounds) or reusing any text. The rebuild is a *clean-room* implementation from the feature list only.

**Unverifiable statically:** runtime behaviour, exact layouts and animations, server-side features (licensing, cloud Looks, AI credits, social feed, leaderboards) and anything gated behind the developer's backend. The Compose UI is R8-obfuscated (top-level classes renamed), so screen *structure* comes from strings and ViewModels, not from layout code.

---

## 2. Technical architecture observed

| Layer | Car Nebula uses |
|---|---|
| UI | Jetpack Compose, single `Home` activity (HOME + LAUNCHER + `USB_DEVICE_ATTACHED` intent filters), Material 3 |
| DI / data | Hilt, Room (≈30 DAOs), DataStore preferences, kotlinx.serialization |
| Backend | Supabase-style REST/Edge functions (license, subscriptions, sync DTOs), Firebase Analytics/Crashlytics, Google Play Billing, Paymob (Egypt payments) |
| Maps | MapLibre GL native (`libmaplibre.so`, 13 MB) with 34 custom map style JSONs |
| Offline routing | Valhalla (`libvalhalla-wrapper.so`) + downloadable country packs (268 country strings) |
| Online navigation | Google Navigation SDK (`libgmm-jni.so`, `NavigationService`) |
| On-device AI | LiteRT-LM (`liblitertlm_jni.so`, 20.7 MB) — model choices: Gemma 2, Gemma 3 1B, Gemma 4 E2B/E4B, Qwen3 |
| ADAS vision | MediaPipe Tasks Vision + TFLite: `twinlitenetplus_{nano,small,medium}.tflite` (lane/drivable-area), `efficientdet_lite0.tflite` (objects) |
| USB camera | `libUVCCamera.so`, `libuvc.so`, `libusb100.so` |
| Licence protection | `com.pairip.licensecheck` (Google Play Integrity / anti-tamper) |

---

## 3. Complete feature inventory

Status key for **Vizion Drive**: ✅ implemented in this build · 🟡 planned (phase 2/3) · ⛔ not reproducible / not recommended (reason given).

### 3.1 Launcher core
| Feature (evidence) | Notes | Status |
|---|---|---|
| Replaces the HOME launcher (`Home` activity, HOME category) | Default launcher on head unit | ✅ |
| App drawer with search, pin/unpin (`app_picker_*`, `pinned_*`) | | ✅ |
| Shortcut bar / dock — position bottom/left/right/top, configurable slots: app, clock, connection, driver profiles, next/prev page, play-pause, screen, theme colours, voice assistant, weather (`shortcut_bar_*`) | | ✅ apps, clock, drawer, settings, play-pause, voice, nav, performance, editor; 🟡 page-nav and other slot types |
| Bottom bar styles: dual bar, enhanced, flat, simplified (+compact/left), top-only, left/right mini-widgets (`bottom_bar_*`) | | ✅ one Tesla-style bar, top or bottom; 🟡 alternate styles, left/right |
| Multiple swipeable home screens, add/remove/lock pages, set home page (`home_swipe_screens_*`, `dashboard_*page*`) | | ✅ |
| Gestures: 2-/3-finger swipe to switch pages (`home_gesture_*`) | | 🟡 |
| Auto-start apps on boot with countdown (`autostart_apps_*`) | | ✅ |
| Split view / app window inside dashboard (`split_view_*`, `app_window_*`) | Android multi-window on head units is OEM-dependent | 🟡 (launch-adjacent where supported) |
| Quick Return floating overlay (speed/RPM/coolant/battery + media controls over other apps) (`quick_return_*`) | Needs `SYSTEM_ALERT_WINDOW` | 🟡 |
| Door-open overlay with car silhouette (`door_overlay_*`) | Needs CAN data | 🟡 |
| Screensaver: idle timeout, clock/date/message, photo/video wallpapers, dim levels, schedule, "only on home" (`screensaver_*`) | | ✅ clock screensaver with idle timer & dim; 🟡 photos/video/schedule |
| Setup checklist / onboarding (`setup_step_*`) | | 🟡 |
| Steering-wheel / hardware button mapping: launch app, cycle apps, launch assistant (`ButtonActionType`, `MediaButtonBroadcastReceiver`) | | 🟡 |
| In-app web browser with presets (`browser_*`) | | 🟡 |

### 3.2 Dashboard & widgets
Widget catalogue found in strings (categories: ADAS, ambient light, apps, assistant, clock, companion, compass, display, eco, EV, images, info, maintenance, map, media, navigation, OBD, PiP, RPM, speed, stack, statistics, text, utility, vehicle, volume, weather):

| Widget family | Variants seen | Status |
|---|---|---|
| Speedometer | arc, bar, digital, fiery, HUD, material, minimal, nebula, original, sci-fi, simple, standard | ✅ arc, digital, bar (original designs) |
| RPM gauge | same 12 styles + shift light | ✅ arc + bar + shift light |
| OBD single / quad / vertical / circular (8 ring styles) | | ✅ single + quad |
| Clock 2×1 / 2×2, weather, weather+clock | | ✅ |
| Compass (round, driving, altitude) | | ✅ heading + altitude |
| Media / music player, phone music, video player | | ✅ media-session widget; 🟡 video |
| Navigation guidance, map (small/large/horizontal) | | ✅ nav launcher widget; 🟡 embedded map |
| Maintenance card/overview, fuel log, fuel quality | | 🟡 |
| DTC scan / clear | | ✅ |
| Eco score, eco live bands | | 🟡 |
| Statistics (minimal, route trace, simple, sport, wave) | | ✅ trip computer |
| EV battery / power (configurable CAN byte maps) | | 🟡 |
| Ambient light (full, compact, slider) | | ✅ |
| Volume, brightness slider/presets | | ✅ volume; 🟡 brightness (needs WRITE_SETTINGS) |
| Phone hub / calls / notifications | | 🟡 |
| TPMS (BLE or USB sensors) | | 🟡 |
| CAN bus pages (body, climate, drive, lights, sensors) | Head-unit-specific CAN decoders | 🟡/⛔ (see 3.8) |
| AM/FM radio (experimental) | Vendor radio API per head unit | ⛔ generic |
| Screen mirror, PiP, Android host widget, image, text, web browser | | ✅ app shortcuts, text; 🟡 host AppWidgets |
| Widget **stacks** that auto-rotate on rules (speed above/below, RPM, coolant, fuel low, night/day, time windows, OBD/phone/online state, navigating, music playing) with presets: city, commute, highway, parked, low fuel, engine hot… | Strong differentiator | 🟡 (rule engine planned) |
| Grid editor: columns/rows, density, resize, per-page background | | ✅ add/remove/reorder; 🟡 resize |
| Widget theming: accent, border, background opacity, text colour overrides | | ✅ global accent & opacity |
| Dashboard presets: classic drive, eco navigate, maintenance focus, media info, minimal HUD, navigation first, OBD quad, road HUD, sport mode; auto-switch by OBD/phone | | ✅ 4 built-in presets |
| AI widget generator (text → widget, credits) & community gallery | Server-side AI + paid credits | ⛔ (requires their backend; could be replaced by your own JSON widget format) |

### 3.3 Sport-mode / full-screen dashboards
`ScreenType`: HOME, SPORT_MODE, BASIC/SIMPLE/MODERN/TECHNICAL/ZEN/CYBER/AURORA/HOT/EGYPT sport modes, OBD single/quad/vertical, DIAGNOSTIC, MECHANIC. ✅ one original full-screen Performance view; 🟡 more themes (your own designs — this is where Vizion's graphics skills pay off).

### 3.4 Vehicle data (OBD-II / ELM327)
| Feature | Status |
|---|---|
| Connection over Bluetooth Classic SPP, BLE, Wi-Fi (`BluetoothObdConnection`, `BleObdConnection`, `WifiObdConnection`) | ✅ Bluetooth SPP; 🟡 BLE & Wi-Fi |
| Adapter busy detection, init delay / command delay tuning | ✅ delays configurable in code; 🟡 UI |
| ~40 standard PIDs (speed, RPM, coolant, intake, load, throttle, MAF, MAP, fuel level, fuel trims, cat temps, ambient, battery voltage, fuel rate…) | ✅ 14 core PIDs |
| Custom PIDs with formula evaluator, import/export, make presets (Mitsubishi etc.) | 🟡 |
| DTC read (stored/pending/permanent), clear with ack handling, system grouping (P/C/B/U) | ✅ stored read + clear; 🟡 pending/permanent |
| DTC descriptions (`assets/dtc-codes.json`) | 🟡 — must source your own SAE-generic list (do not copy theirs) |
| Engine protection: coolant/oil temp thresholds, overheating alerts, battery voltage health, low-fuel warnings | ✅ coolant & voltage alerts; 🟡 rest |
| Fuel quality estimator, knock test | 🟡 (heuristic, needs real-car tuning) |
| 0–100 / acceleration records, max speed | ✅ max speed; 🟡 0–100 timer |
| Eco-driving score, trip statistics, monthly CO₂/money saved | ✅ trip computer; 🟡 scoring |
| EV profiles (SoC/voltage/current from CAN bytes) | 🟡 |

### 3.5 Maintenance & logs
Oil change (type, interval, temperature), tyres (by position), brakes (fluid/pads by axle), filters, transmission, battery service, mileage calibration, fuel log (price, quantity, currency, odometer, charge vs fuel). → 🟡 Phase 2 (Room DB). Cloud sync of these → ⛔ (their backend).

### 3.6 Navigation & location
| Feature | Status |
|---|---|
| Three engines: MapLibre in-app (offline), Google Nav SDK, external app (Maps/Waze) | ✅ external app picker; 🟡 MapLibre in-app |
| Offline country map/routing packs (Valhalla) | 🟡 big job; use OSM-derived data you host |
| Saved places (home/work/favourites), offline POI search (FTS) | 🟡 |
| Speed cameras, speed bumps, traffic lights, police from OSM, speed limit display | 🟡 (OSM Overpass, cache) |
| Speed-limit alert & speed-adaptive volume | ✅ over-speed alert (user-set limit, not map data); ✅ speed-adaptive volume |
| Location fusion (GNSS vs Play Services vs auto; tunnel dead-reckoning) | ✅ Android GNSS; 🟡 fusion |
| Google Maps nav notification mirroring (`GoogleNavNavInfoService`) | 🟡 via notification listener |
| Send maps from phone to car (QR + upload) | ⛔ (their companion protocol) |
| Incident reporting / voting (Google Nav SDK UGC strings) | ⛔ (Google Nav SDK licence) |
| 34 map themes | ⛔ assets — design your own |

### 3.7 Media
Local music library (scan folders, favourites, groups), internet radio (country filter, saved stations), sources: local, Bluetooth, phone, Anghami, Apple Music, any media app; autoplay on start; video player with aspect modes, captions; DSP/equaliser (`nwd_dsp_*` – NWD head-unit specific) and root bridge for max volume.
→ ✅ media-session control of any playing app; 🟡 autoplay last source, local library, internet radio (Radio-Browser API), video; ⛔ NWD DSP and root bridge (vendor/root specific).

### 3.8 Hardware integrations
| Integration | Status |
|---|---|
| Ambient lighting — ELK-BLEDOM BLE LED controllers: power, colour, brightness, effects, presets, "match app colour" | ✅ power/colour/brightness/built-in effects/match accent; 🟡 named presets |
| USB webcam ADAS — lane departure, forward collision, calibration wizard, dashcam recording (`AdasRecordingService`) | 🟡 Phase 3 (CameraX/UVC + your own model choice) |
| TPMS — BLE sensors and USB receivers | 🟡 |
| CAN bus via head-unit "helper" (Dongfeng and others): doors, lights, climate, seat heat, windows, sunroof | ⛔ generic — each head-unit vendor exposes CAN differently; needs your specific unit's API |
| Headlight sensing → auto dark theme | 🟡 (CAN or light sensor) |
| AM/FM tuner | ⛔ generic (vendor API) |
| Root bridge (volume max etc.) | ⛔ |

### 3.9 Phone / companion
Companion app (Android/iOS) over Wi-Fi or Bluetooth: QR pairing with PIN, send locations, notifications, phone music, calls, remote sync, anti-theft, file transfer (QR + local network), shared GPS location. → ⛔ for this build (requires a second app and protocol). Vizion Drive could add its own companion later 🟡.

### 3.10 AI & voice
"Luna" assistant: wake/voice commands offline (apps, volume, nav, music, settings, sport mode, diagnostics), cloud or on-device LLM (Gemma/Qwen via LiteRT-LM), cloud/on-device STT/TTS, proactive warnings, driver-profile recognition by voice; AI Mechanic chat that scans DTCs and diagnoses.
→ ✅ hand-off to the system/Google voice assistant from the dock; 🟡 offline command grammar via Android `SpeechRecognizer`; 🟡 AI mechanic with **your own** LLM key or on-device model; ⛔ "Luna" persona/voice assets.

### 3.11 Personalisation
Themes (auto/dark/light; switch by system, sunrise/sunset, custom times or headlights), accent colours, static/dynamic/3D/video/custom-image backgrounds per page, Looks (save/apply whole theme+layout, cloud sync), driver profiles (photo, theme, layout, bar; selected by prompt or voice), car model image & colour selection (AI-generated car images).
→ ✅ dark/light/auto (by time of day), 8 accent colours, 5 background styles, layout presets; 🟡 driver profiles, image/video backgrounds, local Looks export/import (JSON); ⛔ cloud Looks, AI car images.

### 3.12 Accounts, licensing, social (backend)
Tiers Free / Essential / Plus / Ultimate / Offline-lifetime, Google Play Billing + Paymob, licence validation, AI credits, news, version checks, social posts/comments, driver leaderboards & comparisons, GPS tracking upload, diagnostic log upload, analytics. → ⛔ not reproduced — these are the developer's business systems. Vizion Drive has no accounts, no tracking, no analytics.

---

## 4. What Vizion Drive v0.1 (this project) implements

* **HOME launcher** with Tesla-style split layout: left vehicle panel (huge speed, RPM bar, coolant/voltage, gear-agnostic), right widget pages, full-width dock.
* **Swipeable widget pages**, page editor (add/remove/reorder widgets, add/remove pages), 4 presets.
* **Widgets:** speed (arc/digital/bar), RPM (arc/bar + shift light), OBD single & quad, clock, weather (Open-Meteo, no API key), compass/altitude, media (any app via MediaSession), navigation launcher, app shortcuts, trip computer, DTC scan/clear, ambient light, volume.
* **Performance (sport) full-screen view.**
* **App drawer** with search, add/remove from dock; **dock** with play/pause, clock, drawer, settings, voice, nav.
* **OBD-II ELM327 over Bluetooth Classic:** pick bonded adapter, auto-init, live polling, 14 PIDs, read & clear DTCs, coolant/voltage alerts.
* **ELK-BLEDOM ambient lighting:** scan, connect, on/off, colour, brightness, built-in effects, "match accent colour".
* **Theme:** dark/light/auto (night 19:00–07:00), 8 accent colours, 5 background styles, card opacity.
* **Speed:** GPS speed, km/h or mph, over-speed alert, speed-adaptive volume.
* **Screensaver** (idle clock, dim), **auto-start app** after boot with cancellable countdown, **trip computer** (distance, time, avg/max).
* **Settings** for all of the above, persisted with DataStore.

## 5. Phased plan

**Phase 1 (this build, ~3k lines Kotlin):** as section 4.

**Phase 2 — "daily driver" (2–4 weeks):**
Room DB; maintenance + fuel logs; widget resize & rule-based stacks; OBD over BLE and Wi-Fi; custom PIDs with formula evaluator; pending/permanent DTCs + your own SAE generic description list; local music library + internet radio (radio-browser.info); MapLibre in-app map with your own style; Google Maps nav notification mirroring; driver profiles; Looks export/import as JSON; quick-return overlay; steering-wheel key mapping; offline voice commands.

**Phase 3 — "pro":**
Offline routing (Valhalla/GraphHopper with OSM extracts you host); speed-camera/bump layer from OSM; USB-camera dashcam and ADAS (CameraX/UVC, open lane-detection model with a licence that permits commercial use); TPMS; head-unit-specific CAN for **your** Tesla-style unit (needs its vendor SDK/CAN helper docs); EV profiles; your own companion app; optional on-device LLM mechanic.

## 6. Clean-room & legal notes
* No source, resources, map styles, ML models, DTC database, images or text from Car Nebula are used. Widget designs are new.
* Names avoided: "Nebula", "Luna", "Car Nebula", the Egypt-themed sport modes.
* Car Nebula ships Google's Play licence/anti-tamper check (`pairip`). Nothing here inspects or works around it, and it doesn't need to.
* ELM327 AT commands and SAE J1979 PID formulas are public standards. The ELK-BLEDOM BLE command format is publicly documented by the hobbyist community.
* If you ship commercially: check Open-Meteo's terms (free for non-commercial; paid plan for commercial), OSM attribution, and any ML model licences.

## 7. Could not verify / reproduce
* Exact visual design, animations, layouts (obfuscated UI) — rebuilt from scratch instead.
* All server features (licensing, AI credits, cloud Looks, social, leaderboards, GPS upload, diagnostics upload) — need their backend.
* Head-unit specific: CAN helper (Dongfeng etc.), NWD DSP, AM/FM tuner, root bridge — depend on vendor firmware.
* Google Navigation SDK features (needs Google commercial licence + API key).
* Companion app protocol (QR pairing, send-maps, file transfer).
* On-device LLM quality/behaviour (models and prompts not inspected).
* **The full app could not be compiled or run here.** Both available machines block Google Maven, Maven Central and Gradle downloads. The pure-logic layer (OBD decoding, DTC parsing, ELK frames, trip maths, layout model) *was* compiled with Kotlin 2.0.21 and passed 29/29 unit tests. The Android/Compose layer was reviewed by hand only, so build it in Android Studio (see README).
