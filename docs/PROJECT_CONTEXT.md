# LumiLink — Project Context

> Durable context doc. Captures the idea, feasibility findings, protocol details, the
> Android networking approach, architecture direction, and next steps, so work can resume
> even if the chat session is lost. Last updated: 2026-07-09.

## 1. The idea

An Android app to **remote-control and transfer photos from a Panasonic Lumix GX80**
camera over the camera's own WiFi hotspot, from a **Xiaomi 14T Pro** phone.

**Why build it:** the official Panasonic app (Image App / LUMIX Sync) connects unreliably.
Root cause (confirmed during research): modern Android (10+) detects the camera's hotspot
has no internet, routes app traffic back over **cellular**, and aggressively **tears the
WiFi connection down**. So requests never reach the camera, or the link drops. A custom app
that pins its sockets to the camera network fixes exactly this.

## 2. Core requirements (MVP)

1. **Remote shutter control** — trigger capture, autofocus, ideally exposure settings.
2. **Photo transfer** — browse and download stored JPG/RAW files to the phone.

Stretch: live-view viewfinder, video record start/stop, exposure controls (ISO/aperture/shutter).

## 3. Feasibility verdict: CONFIRMED FEASIBLE

Both requirements are achievable, and the connectivity problem has a known, documented fix.

### Camera protocol (reverse-engineered, unencrypted HTTP)
- Camera hotspot address: **`192.168.54.1`**
- Control endpoint: plain HTTP GET to **`http://192.168.54.1/cam.cgi`**
- **Capture:** `?mode=camcmd&value=capture`
- **Autofocus:** `?mode=camcmd&value=oneshot_af`
- **Playback / record mode switch:** `?mode=camcmd&value=playmode` / `recmode`
- **Live view stream:** `?mode=camcmd&value=startstream&value=49152` — camera pushes an
  MJPEG/UDP stream to the specified client port
- **Settings:** ISO / aperture / shutter / focus / metering via `setsetting` commands
- **Info/state:** `?mode=getinfo&type=capability`, `?mode=getstate`
- **Pairing handshake:** short UPnP device-description exchange on **port 60606**, then set
  a device name — required before the camera accepts commands.

### Photo transfer
- Switch camera to playback mode, then browse media over **DLNA/UPnP (ContentDirectory
  service on port 60606)**; the actual image files are served over ordinary **HTTP GET**.
- Existing projects that already do this: lumix-link-desktop, qtpanaremote.

### Caveats
- Protocol is unofficial (Panasonic could change it; GX80 is old/stable, so low risk).
- RAW download is slow over the camera's 2.4GHz WiFi.
- UPnP-based transfer is the fiddliest part to implement.

## 4. The Android networking approach (this is the actual product differentiator)

The whole point is handling the network better than the official app. Pattern:

1. Build a `NetworkRequest` from a **`WifiNetworkSpecifier`** (Android 10+), and
   **remove `NET_CAPABILITY_INTERNET`** so Android stops expecting internet from it.
2. Call **`ConnectivityManager.requestNetwork()`** with it.
3. In the callback, call **`bindProcessToNetwork(network)`** — forces *all* app sockets over
   the camera WiFi regardless of internet status.
4. **Hold the `NetworkRequest` open** to keep the connection alive — Android won't reap a
   network an app is actively requesting. This defeats the "connection quickly discarded"
   problem the user hit.

## 5. Architecture direction (to be finalized in plan mode)

- **Language/UI:** Kotlin + Jetpack Compose + Material 3
- **Networking layer:** ConnectivityManager binding (above) + an HTTP client (OkHttp/Retrofit)
  for `cam.cgi`; a UPnP/DLNA piece for media browse; a UDP receiver for live view.
- **Architecture pattern:** MVVM or MVI (decide in plan mode)
- **Local storage:** where downloaded photos land (MediaStore / app storage)
- **Target:** Xiaomi 14T Pro first; decide min SDK (likely API 29/Android 10 for the WiFi
  specifier APIs).

## 6. Names considered
Chosen: **LumiLink** (Lumix + link). Runners-up: Shutterbind, GXConnect, TetherG, Holdfast.

## 7. Wireframe (v1)

Low-fidelity clickable wireframe of the three core screens exists at
**`docs/wireframe-v1.html`** (open in a browser) and is published as a Claude Artifact:
https://claude.ai/code/artifact/58f8cf72-f111-464b-8a0a-8237304b3d0e

Design decisions captured there: three-tab bottom nav (**Connect · Control · Photos**);
function-over-form treatment; monospace for technical data (IPs, SSIDs, exposure, filenames)
vs. system sans for UI; amber "camera-dial" accent; light+dark themes.
Screen contents: (1) Connect — pairing checklist, network/keep-alive readout, bind-to-Wi-Fi
callout; (2) Control — live-view placeholder, ISO/aperture/shutter/EV readout, AF + shutter +
REC controls; (3) Photos — selectable thumbnail grid with JPG/RAW badges and a
"N selected · XX MB → Download" action bar. Status: awaiting user feedback / iteration.

## 8. Build status

**MVP1 (Connect + Photo Transfer) is code-complete and builds** (as of 2026-07-09). Awaiting the
first on-device test before starting MVP2.

- Toolchain: Temurin 17 (JDK), Android cmdline-tools SDK (platform-tools/android-34/build-tools 34),
  Gradle 8.9 via wrapper, AGP 8.5.2, Kotlin 2.0.20. No IDE; CLI builds via `./gradlew`.
  Env vars added to `~/.zshrc` (JAVA_HOME, ANDROID_HOME, PATH).
- App: Kotlin + Compose + Material 3, minSdk 29 / target 34, single `:app` module, manual DI.
- Key files: `network/CameraNetworkManager` (WiFi bind), `network/CameraClient` (cam.cgi),
  `network/SsdpDiscovery` + `DeviceDescriptionParser` + `DidlParser`, `data/PhotoRepository`
  (recursive DLNA browse), `data/PhotoDownloader` + `MediaStoreSaver`, `service/CameraConnectionService`
  (foreground), `ui/connect/*`, `ui/gallery/*`.
- Tests: JVM unit tests for `CamReplyParser` and `DidlParser` (all passing).
- Build: `JAVA_HOME=<temurin17> ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (~27 MB).
- Reader's guide for the code: `docs/kotlin-android-for-java-devs.md`.

### Known MVP1 scoping decisions (to revisit)
- Network selection is **manual SSID + password entry** (saved via DataStore), not a WiFi scan —
  the scan/pick UI is a later enhancement.
- Download picks the JPEG original when present; RAW-only shots download as `.rw2`. (RAW+JPEG pairs
  currently save the JPEG.)
- `ktlint` from the plan is not yet wired into Gradle.

## 9. Next steps
1. **On-device verify MVP1** (physical Xiaomi 14T Pro + GX80): connect holds, browse, download JPG + RAW.
2. Then MVP2 (remote shutter), then MVP3 (live view).

## Sources
- Lumix GX80 protocol: https://github.com/cleverfox/lumixproto
- libgphoto2 Lumix HTTP/Wifi protocol thread: https://github.com/gphoto/libgphoto2/issues/409
- Android — Connecting your App to a Wi-Fi Device: https://android-developers.googleblog.com/2016/07/connecting-your-app-to-wi-fi-device.html
