#!/usr/bin/env python3
"""Capture fictional debug fixtures, never personal camera footage, as WebP."""
import argparse
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='ADB serial of the Android TV')
args = parser.parse_args()
prefix = ['adb', '-s', args.serial]
package = 'id.my.bontot.camviewtv'
remote = '/sdcard/camview-docs-' + uuid.uuid4().hex + '.xml'
dest = Path(__file__).resolve().parents[1] / 'docs' / 'screenshots'
dest.mkdir(parents=True, exist_ok=True)


def adb(*cmd):
    return subprocess.check_output(prefix + list(cmd), stderr=subprocess.STDOUT)


def dump():
    for _ in range(6):
        result = adb('shell', 'uiautomator', 'dump', remote)
        if b'dumped' in result:
            try:
                return ET.fromstring(adb('exec-out', 'cat', remote))
            except ET.ParseError:
                pass
        time.sleep(.5)
    raise RuntimeError('Could not get a fresh UI tree')


def tap(label):
    tree = dump()
    control = next(n for n in tree.iter('node')
                   if n.get('class') == 'android.widget.Button' and n.get('text') == label)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', control.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def capture(name, marker):
    for _ in range(6):
        tree = dump()
        if any(marker in n.get('text', '') for n in tree.iter('node')):
            break
    else:
        raise RuntimeError('Preview marker missing; refusing to save a screenshot')
    # A preview-only marker must be visible, so this cannot capture a normal dashboard.
    texts = [n.get('text', '') for n in tree.iter('node')]
    if not any('192.0.2.' in t or t == 'DEMO' for t in texts):
        raise RuntimeError('Fictional fixture not verified; refusing to capture')
    png = adb('exec-out', 'screencap', '-p')
    if not png.startswith(b'\x89PNG'):
        raise RuntimeError('Invalid screen capture')
    subprocess.run(['magick', 'png:-', '-resize', '1440x810>', '-strip', '-quality', '84',
                    str(dest / (name + '.webp'))], input=png, check=True)
    print('Saved', name + '.webp', flush=True)


try:
    adb('shell', 'am', 'force-stop', package)
    output = adb('shell', 'am', 'start', '-W', '-n', package + '/.PreviewActivity')
    if b'Error' in output:
        raise RuntimeError('Install a debug build with PreviewActivity first')
    capture('overview', 'Quick monitoring')
    tap('Cameras')
    capture('cameras', 'Camera library')
    tap('Multi-view')
    capture('multiview', 'Multi-view')
    adb('shell', 'input', 'keyevent', '4')
    tap('Network')
    capture('network', 'Device inventory')
finally:
    adb('shell', 'rm', '-f', remote)
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-n', package + '/.MainActivity')
