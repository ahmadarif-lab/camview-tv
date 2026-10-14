# Release guide

Production APKs are built and signed on the maintainer's laptop. Only the APK, its SHA-256
checksum and the public certificate fingerprint are published. The private keystore and
passwords are never committed or uploaded to GitHub, including GitHub Secrets.

## Private signing files

Keep the keystore and `key.properties` in a private directory outside the checkout. Set
permissions to `0700` for that directory and `0600` for its private files. Back up both the
keystore and properties file securely; losing either can prevent future app updates.

A git-ignored `key.properties` in the repository root may be a symlink to the external file.
Alternatively, pass `-PsigningProperties=/path/to/private/key.properties` when building.
Properties are described in the main README. Never pass passwords as command-line arguments.

## Build and verify

1. Update `versionCode` and `versionName` in `app/build.gradle` for an update.
2. Build and lint locally:

   ```sh
   ./gradlew assembleDebug assembleRelease lintDebug lintRelease
   ```

3. Verify the signed APK with the Android SDK build tools:

   ```sh
   apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
   ```

   The signer certificate SHA-256 must equal `docs/signing-certificate.sha256`. A release
   APK must not be signed by `CN=Android Debug`. Confirm that debug preview activities are
   absent from the release APK.

4. Prepare the public files:

   ```sh
   mkdir -p dist
   cp app/build/outputs/apk/release/app-release.apk dist/CamView-TV.apk
   cd dist
   shasum -a 256 CamView-TV.apk > SHA256SUMS
   cd ..
   ```

## Publish

Commit and push the public source changes. Once CI passes, create a release from that exact
commit with GitHub CLI, substituting the app version and a release notes file:

```sh
gh release create vX.Y.Z dist/CamView-TV.apk dist/SHA256SUMS \
  --target COMMIT_SHA --title 'CamView TV vX.Y.Z' --notes-file /path/to/release-notes.md
```

Do not attach `key.properties`, a keystore, signing passwords or an archive of the private
signing directory. Keep the same production key for all updates to `id.my.bontot.camviewtv`.
