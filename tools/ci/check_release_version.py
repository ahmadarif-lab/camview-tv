#!/usr/bin/env python3
"""Gate release builds on main ancestry, version/tag agreement and immutable releases."""
import os
from pathlib import Path
import re
import subprocess


def main():
    subprocess.run(['git', 'merge-base', '--is-ancestor', 'HEAD', 'origin/main'], check=True)
    source = Path('app/build.gradle').read_text()
    version = re.search(r"versionName\s+'([^']+)'", source).group(1)
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise SystemExit('A stable major.minor.patch app version is required.')
    tag = 'v' + version
    ref = os.environ['GITHUB_REF']
    publish = os.environ.get('PUBLISH_RELEASE') == 'true'
    if ref.startswith('refs/tags/'):
        if ref != 'refs/tags/' + tag:
            raise SystemExit('The pushed tag must match versionName in app/build.gradle.')
    elif ref != 'refs/heads/main' or publish:
        raise SystemExit('Manual main builds may create signed artifacts; publishing requires the matching version tag.')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write('tag=' + tag + '\n')
    print('Release version and main ancestry verified:', tag)


if __name__ == '__main__':
    main()
