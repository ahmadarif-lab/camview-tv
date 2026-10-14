<p align="center">
  <img src="docs/icon.webp" width="112" height="112" alt="CamView TV CCTV icon">
</p>

<h1 align="center">CamView TV</h1>

<p align="center">
  Your cameras. One clear view.<br>
  A native Android TV viewer for local RTSP cameras, with network discovery,<br>
  a bright interface and multiple cameras on one screen.
</p>

<p align="center">
  <a href="https://github.com/ahmadarif-lab/camview-tv/releases/latest"><img src="https://img.shields.io/badge/Install-APK-089DA9?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Install APK"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-162B38?style=flat-square" alt="Android 7.0 or later">
  <img src="https://img.shields.io/badge/Android_TV-D--pad_ready-089DA9?style=flat-square" alt="Android TV remote support">
  <img src="https://img.shields.io/badge/APK-842_KiB-089DA9?style=flat-square" alt="Production APK about 842 KiB">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-162B38?style=flat-square" alt="MIT license"></a>
</p>

<p align="center">
  <img src="docs/screenshots/overview.webp" width="960" alt="CamView TV overview with camera totals and quick monitoring layouts">
</p>

## Why CamView TV

A TV makes a useful camera monitor. CamView TV connects directly to cameras on your home
network, so you can open one feed or keep several in view with your remote. No cloud account
or subscription is required by the app.

It started with TP-Link Tapo cameras, but uses standard RTSP streaming and supports a custom
port and stream path for other cameras. Camera compatibility depends on the model, its RTSP
implementation and the video/audio codecs your TV can decode. So far, live playback has been
verified with two TP-Link cameras on an Android 9 TV; other brands have not yet been verified.

## Features

- **Overview** — camera totals, favorites, last scan and quick monitoring shortcuts.
- **Cameras** — saved cameras, last-view previews, favorites and connection settings.
- **Network** — TV network details, scan progress and discovered RTSP/ONVIF services.
- **Multi-view** — two cameras side by side or stacked, plus grids of four, six or nine tiles.
- **Remote controls** — D-pad navigation, visible focus rings and OK actions throughout.
- **Playback** — RTSP over TCP, fit/fill, reconnect, recognized HD/SD stream presets and
  audio from the highlighted camera in Multi-view. Audio starts off.
- **Local credentials** — camera details encrypted with AES-GCM and Android Keystore;
  application backups are disabled.
- **Light interface** — white and teal across the dashboard and Multi-view.

## Screenshots

<p align="center">
  <img src="docs/screenshots/cameras.webp" width="960" alt="Camera library with favorites, last-view previews and per-camera settings">
</p>
<p align="center">
  <img src="docs/screenshots/multiview.webp" width="960" alt="Four-camera Multi-view with a white interface and layout, audio and fit controls">
</p>
<p align="center">
  <img src="docs/screenshots/network.webp" width="960" alt="Network inventory showing example RTSP and ONVIF services">
</p>

Screenshots use the actual app UI with **fictional camera names, documentation-only addresses
and original scene illustrations**. They contain no private camera footage or credentials.
All documentation images are WebP.

## Install

Requires **Android 7.0 / API 24 or later**. Designed for Android TV / Google TV in landscape
with a D-pad remote. The TV and cameras need to be on the same reachable local network.

Download `CamView-TV.apk` from the [latest release](https://github.com/ahmadarif-lab/camview-tv/releases/latest),
copy it to your TV and open it with a file manager. Android may ask you to allow installation
from that file manager. Open **CamView TV** from your TV's apps list.

You can also [build the app locally](#build).

### Install with ADB

Enable developer options and USB/network debugging on your TV, then use its address:

```sh
adb connect <TV_IP>:5555
adb install -r CamView-TV.apk
adb shell am start -n id.my.bontot.camviewtv/.MainActivity
```

The package is **`id.my.bontot.camviewtv`**. Future updates need the same package and signing
key to keep saved cameras. Builds under the earlier development package are separate apps;
Android does not automatically move their encrypted credentials to this package.

## First run

1. Enable **RTSP / local streaming** in your camera's app or web settings. Create a local
   camera username and password if required by the camera. These are often different from
   the account used to sign in to its cloud app.
2. Open **Network → Scan network**. Discovery looks for local RTSP and ONVIF services;
   a discovery result does not guarantee the camera can stream without further setup.
3. In **Cameras**, choose **Set up camera**, or choose **Add camera** for a manual connection.
   Enter the camera's IPv4 address, local username/password, RTSP port and stream path.
4. Choose **Watch**. If it fails, check the camera manufacturer's RTSP URL and credentials.
5. Open **Multi-view** and choose a layout and cameras. Start with low-resolution streams
   when using several cameras together.

For **Tapo**, the usual setup is *Camera settings → Advanced Settings → Camera Account*.
Supported models commonly use port `554`, `/stream1` for HD and `/stream2` for SD. Check your
model's documentation; some battery-powered cameras do not expose local RTSP.

For **other brands**, use the port and path supplied by the manufacturer. A full URL such as
`rtsp://USER:PASS@192.168.1.20:554/live/main` is entered as separate fields: address
`192.168.1.20`, port `554`, path `/live/main`, and the local credentials. Stream paths can
include query parameters. The current setup form requires a username and password.

### Remote controls

| Action | Control |
| --- | --- |
| Move between controls or tiles | D-pad arrows |
| Activate a button / open a camera | OK |
| Show single-camera playback controls | D-pad arrows |
| Change the camera assigned to a Multi-view tile | Hold OK on the tile |
| Swap two assigned cameras | Hold OK, then choose an already displayed camera |
| Return to the previous screen | Back |

In Multi-view, enabled audio follows the focused camera. HD/SD presets map recognized Tapo
paths (`/stream1`, `/stream2`, `/stream6`, `/stream7`); custom paths remain unchanged. For a
custom stream, choose its main/sub-stream path in the camera's connection settings.

## Compatibility and limits

- Discovery covers the TV's current IPv4 Wi-Fi/Ethernet subnet, with a limit of 1,024 addresses
  on large subnets. Guest networks, VLANs and client isolation can prevent discovery.
- ONVIF is used for discovery. This version does not implement ONVIF PTZ, automatic media
  profile/stream URL retrieval, recordings, microSD/cloud playback or two-way talk.
- Saved **Account saved** labels confirm stored credentials, not current connectivity.
  Network results are cached observations; card previews are frames from the last viewing.
- Nine tiles are available, but the number of simultaneous playable streams depends on your
  TV's decoder, memory, camera resolution and network. Two simultaneous SD feeds have been
  tested; larger live setups have not yet been verified.
- Manual connections currently use IPv4. Cameras that only work through a proprietary cloud
  service are not supported.

## Privacy

The app connects to your local cameras and performs local network discovery. It contains no
analytics, ads, cloud sign-in or app-operated relay service. Credentials are not included in
navigation Intents or shown in displayed stream addresses. Saved camera metadata and
credentials are encrypted on the TV; last-view images are stored in the app's private cache.

RTSP is not encrypted by this app. Use it on a trusted local network. Uninstalling the app
removes its saved settings and device-bound encryption key.

## Build

Requires **JDK 17**, Android SDK **platform 35**, and Android SDK build tools. Android Studio
can open the project directly. For a command-line build, set `ANDROID_HOME`, or create a
local, untracked `local.properties` with `sdk.dir=/path/to/your/android-sdk`.

```sh
./gradlew assembleDebug lintDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Release builds enable R8 code optimization and resource shrinking. The v0.4.1 production
APK is about **842 KiB / 0.82 MiB**, 77.5% smaller than v0.4.0, with the same features.

The Gradle wrapper is included. Debug APKs are signed automatically with your local Android
debug key and are suitable for trying the app. Official release APKs are built locally and
signed with a dedicated production key; GitHub receives the APK, not the private key.

### Production signing

The private keystore and passwords live outside this repository. To sign your own release,
create an untracked `key.properties` in the project root, or pass an external file with
`-PsigningProperties=/path/to/private/key.properties`:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

```sh
./gradlew assembleRelease lintRelease
# With signing configured: app/build/outputs/apk/release/app-release.apk
# Without signing configured: app/build/outputs/apk/release/app-release-unsigned.apk
```

An explicitly requested but missing properties file, incomplete properties or a missing
keystore fail the build. An unsigned release is never automatically signed with a debug key.
Keep your key and passwords backed up together; updates must use the same signing key.

The official certificate's SHA-256 fingerprint is in
[docs/signing-certificate.sha256](docs/signing-certificate.sha256). Verify a downloaded APK
with Android SDK build tools:

```sh
apksigner verify --verbose --print-certs CamView-TV.apk
```

The `Signer #1 certificate SHA-256 digest` must match that file. The APK file checksum in
release notes verifies the download; the certificate fingerprint identifies its signer.
Production APKs cannot update a debug installation with a different signing key; switching
requires uninstalling the debug app, which removes its locally saved cameras.

GitHub Actions builds and lints debug and unsigned release variants without access to
production signing material. CI debug artifacts are development builds. See
[Release guide](docs/RELEASING.md) for the local signing and publishing process.

### Project layout

```text
app/src/main/java/id/my/bontot/camviewtv/
  MainActivity.java       Dashboard, camera setup and network inventory
  Discovery.java          LAN discovery and service probing
  PlayerActivity.java     Single-camera playback
  MultiViewActivity.java  Multi-camera layout and player lifecycle
  CameraStore.java        Encrypted camera storage
  SnapshotStore.java      Last-view frame cache
app/src/debug/            Read-only fixtures for public screenshots
app/src/main/res/         Original icons, TV banner and theme
```

The app uses native Java and [AndroidX Media3](https://developer.android.com/media/media3)
for playback, with no ABI-specific native libraries bundled by this project.

### Refresh documentation screenshots

Install a debug build on an Android TV, then run:

```sh
python3 tools/capture_screenshots.py --serial <ADB_DEVICE_SERIAL>
```

Requires Python 3, ADB and ImageMagick (`magick`). The debug-only preview activities use
fictional in-memory fixtures, do not scan the network and do not read or overwrite the camera
vault. They are excluded from release builds. See [docs/README.md](docs/README.md).

## Contributing

Bug reports and camera compatibility reports are welcome. Include the TV model, Android
version, camera model, codec, stream resolution and steps to reproduce. Remove camera
credentials, private addresses and footage from logs or screenshots before posting.

For changes, keep D-pad navigation usable, preserve custom RTSP paths and run
`./gradlew assembleDebug lintDebug` before opening a pull request.

## License

[MIT](LICENSE) © 2026 Ahmad Arif. Third-party dependencies retain their own licenses;
see [Third-party notices](THIRD_PARTY_NOTICES.md).

CamView TV is an independent project, unaffiliated with TP-Link or other camera manufacturers.
Tapo and other product names belong to their respective owners.
