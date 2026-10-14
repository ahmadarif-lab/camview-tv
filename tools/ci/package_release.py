#!/usr/bin/env python3
"""Verify the official signer, then prepare only the public release files."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess


def main():
    apk = Path('app/build/outputs/apk/release/app-release.apk')
    apksigner = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner'
    result = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=True).stdout
    found = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', result)
    expected = Path('docs/signing-certificate.sha256').read_text().strip()
    if not found or found.group(1) != expected or 'CN=Android Debug' in result:
        raise SystemExit('The APK signer does not match the official production certificate.')
    if 'Verified using v2 scheme (APK Signature Scheme v2): true' not in result:
        raise SystemExit('APK Signature Scheme v2 verification failed.')
    dist = Path('dist')
    dist.mkdir(exist_ok=True)
    dest = dist / 'CamView-TV.apk'
    shutil.copy2(apk, dest)
    digest = hashlib.sha256(dest.read_bytes()).hexdigest()
    (dist / 'SHA256SUMS').write_text(digest + '  CamView-TV.apk\n')
    tag = os.environ['RELEASE_TAG']
    commit = os.environ['GITHUB_SHA']
    size = dest.stat().st_size
    notes = f'''Production-signed Android TV release, built by GitHub Actions from commit `{commit}`.

- Package: `id.my.bontot.camviewtv`
- APK size: {size:,} bytes ({size / 1048576:.2f} MiB)
- Minimum Android 7.0 / API 24
- Signing certificate verified against the same production key used by previous releases.

Download **CamView-TV.apk** to install or update an existing production installation.
An earlier debug-signed installation uses a different certificate; uninstalling it removes its saved cameras.

### Verification

APK SHA-256:

```text
{digest}
```

Production certificate SHA-256:

```text
{expected}
```

Check the download using the attached `SHA256SUMS`, or inspect its signer with
`apksigner verify --verbose --print-certs CamView-TV.apk`.
'''
    (dist / 'release-notes.md').write_text(notes)
    print('Verified production certificate; public APK and checksum prepared for', tag)


if __name__ == '__main__':
    main()
