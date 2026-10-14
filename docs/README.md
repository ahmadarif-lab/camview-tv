# Documentation assets

All published images use WebP. The icon and TV banner are original VectorDrawable artwork
in `app/src/main/res/drawable/`; `icon.webp` is a rendered copy for the README.

The screenshots show the real Android TV interface, rendered by debug-only preview
activities. Camera names and credentials are fictional, addresses use the RFC 5737
`192.0.2.0/24` documentation range, and camera images are original illustrations rather than
private footage. Multi-view labels these frames **DEMO**.

To refresh screenshots, install a debug APK and run from the repository root:

```sh
python3 tools/capture_screenshots.py --serial <ADB_DEVICE_SERIAL>
```

The script opens the preview activity, captures Overview, Cameras, Multi-view and Network,
converts each capture to WebP and returns to the normal dashboard. It needs Python 3, ADB
and ImageMagick. It does not enter credentials, edit saved cameras or run network scans.

Preview activities live only in `app/src/debug` and are absent from release APKs.
