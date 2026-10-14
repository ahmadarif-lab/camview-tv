#!/usr/bin/env python3
"""Restore signing material into a private runner directory, never into the checkout."""
import base64
import os
from pathlib import Path


def property_value(value):
    return value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t').replace(' ', '\\ ')


def main():
    names = ('CAMVIEW_KEYSTORE_BASE64', 'CAMVIEW_STORE_PASSWORD', 'CAMVIEW_KEY_ALIAS', 'CAMVIEW_KEY_PASSWORD')
    if any(not os.environ.get(name) for name in names):
        raise SystemExit('Production signing secrets are missing.')
    # Exclusive creation prevents accidentally following or replacing an existing path.
    private = Path(os.environ['RUNNER_TEMP']) / 'camview-signing'
    private.mkdir(mode=0o700)
    keystore = base64.b64decode(os.environ['CAMVIEW_KEYSTORE_BASE64'], validate=True)
    if not keystore:
        raise SystemExit('The signing keystore is empty.')
    keystore_path = private / 'release.jks'
    with keystore_path.open('xb') as output:
        os.chmod(keystore_path, 0o600)
        output.write(keystore)
    values = {
        'storeFile': str(keystore_path),
        'storePassword': os.environ['CAMVIEW_STORE_PASSWORD'],
        'keyAlias': os.environ['CAMVIEW_KEY_ALIAS'],
        'keyPassword': os.environ['CAMVIEW_KEY_PASSWORD'],
    }
    properties = private / 'key.properties'
    with properties.open('x', encoding='utf-8') as output:
        os.chmod(properties, 0o600)
        for name, value in values.items():
            output.write(name + '=' + property_value(value) + '\n')
    print('Production signing files prepared outside the checkout.')


if __name__ == '__main__':
    main()
