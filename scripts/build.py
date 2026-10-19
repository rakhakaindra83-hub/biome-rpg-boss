#!/usr/bin/env python3
"""Build Java 21 plugin; optional ECJ supports hosts without javac 21."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--classpath', help='Dependency JARs separated by the OS classpath separator')
p.add_argument('--ecj', type=Path, help='Optional Eclipse Java compiler JAR')
p.add_argument('--audio', type=Path, help='Optional directory containing biomepack-*.ogg files')
args = p.parse_args()
build = ROOT / 'build'
if build.exists():
    shutil.rmtree(build)
build.mkdir()
cp = str(ROOT / 'lib/legacy-runtime.jar') + os.pathsep
cp += args.classpath or os.pathsep.join(str(x) for x in sorted((ROOT / 'deps').glob('*.jar')))
compiler = ['java', '-jar', str(args.ecj), '-21', '-proc:none'] if args.ecj else ['javac', '--release', '21', '-proc:none']
sources = sorted(str(x) for x in (ROOT / 'src/main/java').rglob('*.java'))
subprocess.run(compiler + ['-encoding', 'UTF-8', '-classpath', cp, '-d', str(build)] + sources, check=True)
entries = {}
with zipfile.ZipFile(ROOT / 'lib/legacy-runtime.jar') as z:
    entries.update({n: z.read(n) for n in z.namelist() if n.endswith('.class')})
for folder in (ROOT / 'src/main/resources', build):
    for f in sorted(folder.rglob('*')):
        if f.is_file():
            entries[f.relative_to(folder).as_posix()] = f.read_bytes()
# Generate resource pack and data pack from editable assets.
import io
for name in ('resourcepack', 'datapack'):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as z:
        for f in sorted((ROOT / 'assets' / name).rglob('*')):
            if f.is_file():
                z.writestr(f.relative_to(ROOT / 'assets' / name).as_posix(), f.read_bytes())
    entries['rpgboss-' + name + '.zip'] = buffer.getvalue()
audio = sorted(args.audio.glob('*.ogg')) if args.audio else []
for f in audio:
    entries['bundled-music/' + f.name] = f.read_bytes()
entries['bundled-audio.txt'] = ('\n'.join(f.name for f in audio) + ('\n' if audio else '')).encode()
entries['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\npaperweight-mappings-namespace: mojang\r\n\r\n'
dest = ROOT / 'dist/YouTubeBacksound-RPGBoss-2.8.0.jar'
dest.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as z:
    for n, data in sorted(entries.items()):
        info = zipfile.ZipInfo(n, (2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, data)
print(dest)
print('Bundled OGG files:', len(audio))
