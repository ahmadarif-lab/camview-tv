# Release guide

Official APKs are built and signed by the **Signed release** GitHub Actions workflow. It uses
the existing production key, so its APKs can update previously published production builds.
The private keystore and passwords are stored as environment secrets, never in Git or Release
attachments. A local backup and the local signing workflow are retained.

## GitHub signing setup

Create the `production` environment in repository Settings → Environments. Allow deployment
from the `main` branch and `v*` tags. Configure these **environment secrets**:

| Secret | Value |
| --- | --- |
| `CAMVIEW_KEYSTORE_BASE64` | Base64-encoded bytes of the production JKS keystore |
| `CAMVIEW_STORE_PASSWORD` | Keystore password |
| `CAMVIEW_KEY_ALIAS` | Production key alias |
| `CAMVIEW_KEY_PASSWORD` | Private key password |

Base64 is a transport format, not encryption; the complete value belongs in GitHub Secrets,
not a workflow file or public log. Use `gh secret set NAME --env production` with the value on
stdin, or GitHub's environment secrets UI. Keep the same production key for future updates.

The release workflow runs only in the upstream repository on `main` or a `v*` tag. It checks
main ancestry and version/tag agreement before using signing secrets. Pull requests use the
separate validation workflow without production secrets. Actions are pinned to commit SHAs.
Signing files are created with restricted permissions in the runner's temporary directory
outside the checkout and removed before public artifacts are uploaded. The signed APK's
certificate is checked against `docs/signing-certificate.sha256` before publication.

## Publish with a version tag

1. Update `versionCode` and `versionName` in `app/build.gradle`.
2. Commit and push to `main`, then wait for the **Android build** workflow to pass.
3. Push a matching, new version tag. For example, when `versionName` is `X.Y.Z`:

   ```sh
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

**Signed release** builds and lints the production APK, verifies its signer, uploads the signed
APK/checksum and R8 mapping as Actions artifacts, then creates the GitHub Release. Only the
APK and checksum are attached to the public Release. An existing Release is not overwritten.

For a test build without publishing:

```sh
gh workflow run release.yml --ref main -f publish=false
```

Download the `CamView-TV-production` artifact from that run. For manual publishing, select a
matching version tag and set `publish=true`; publishing from `main` is rejected.

## Local signing backup

Keep the keystore and `key.properties` in a private directory outside the checkout. Set
permissions to `0700` for that directory and `0600` for its private files. Back up both the
keystore and properties file securely; losing either can prevent future app updates.

A git-ignored `key.properties` in the repository root may be a symlink to the external file.
Alternatively, pass `-PsigningProperties=/path/to/private/key.properties` when building.
Properties are described in the main README. Never pass passwords as command-line arguments.

## Build and verify locally

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

   Release builds use R8 optimization and resource shrinking. Archive
   `app/build/outputs/mapping/release/mapping.txt` per version outside Git before another release
   build overwrites it. This mapping lets you retrace obfuscated crash reports.

4. Prepare the public files:

   ```sh
   mkdir -p dist
   cp app/build/outputs/apk/release/app-release.apk dist/CamView-TV.apk
   cd dist
   shasum -a 256 CamView-TV.apk > SHA256SUMS
   cd ..
   ```

## Publish a local build manually

Commit and push the public source changes. Once CI passes, create a release from that exact
commit with GitHub CLI, substituting the app version and a release notes file:

```sh
gh release create vX.Y.Z dist/CamView-TV.apk dist/SHA256SUMS \
  --target COMMIT_SHA --title 'CamView TV vX.Y.Z' --notes-file /path/to/release-notes.md
```

Do not attach `key.properties`, a keystore, signing passwords or an archive of the private
signing directory. Keep the same production key for all updates to `id.my.bontot.camviewtv`.
