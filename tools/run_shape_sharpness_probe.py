"""Run the shape edge sharpness probe (unit F): renders the UI's four shape
families through the real primitives (SkiaDraw/UiCards/DotTexture/UiTokens) on
an offscreen GPU surface at densities 1.0/1.3333/1.5 x two device-pixel phases,
and compares each against a 4x-supersampled box-downsampled ground truth.

Requires JDK 21 in JAVA_HOME/PATH and the project's already-resolved Gradle
cache. Opens no visible window and does not launch Minecraft.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent


def main():
    output = ROOT / '.work' / '2026-11-01-sharpness-shapes'
    output.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get('JAVA_HOME')
    java = Path(java_home) / 'bin' / 'java.exe' if java_home else Path(shutil.which('java') or '')
    javac = Path(java_home) / 'bin' / 'javac.exe' if java_home else Path(shutil.which('javac') or '')
    if not java.is_file() or not javac.is_file():
        raise SystemExit('Set JAVA_HOME to a Windows JDK 21 installation.')
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.startswith('#'))
    skija = props.get('skija_version', '0.143.18')

    classpath = [str(ROOT / 'platforms/1.21.1-neoforge/build/classes/java/main')]
    atomlib = sorted((ROOT / 'build/audit-20261101/maven/com/atom/lib/atomlib-1.21.1-neoforge').rglob('atomlib-1.21.1-neoforge-0.1.0-beta.jar'))
    if not atomlib:
        raise SystemExit('atomlib input jar not found under build/audit-20261101/maven; run the build once.')
    classpath.append(str(atomlib[0]))
    artifacts = [('io.github.humbleui', 'skija-shared', skija),
                 ('io.github.humbleui', 'skija-windows-x64', skija),
                 ('io.github.humbleui', 'types', '0.2.0'),
                 ('com.google.code.gson', 'gson', '2.10.1')]
    artifacts += [('org.lwjgl', artifact, '3.3.3') for artifact in ('lwjgl', 'lwjgl-glfw', 'lwjgl-opengl')]
    for group, artifact, version in artifacts:
        names = [f'{artifact}-{version}.jar']
        if group == 'org.lwjgl':
            names.append(f'{artifact}-{version}-natives-windows.jar')
        matches = []
        for version_dir in (cache / group / artifact).glob('*'):
            for name in names:
                matches += list(version_dir.rglob(name))
        if not matches:
            raise SystemExit(f'Expected at least one resolved {names[0]} in {cache}.')
        classpath += [str(m) for m in matches]
    cp = os.pathsep.join(classpath)
    classes = output / 'probe-classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tools/probes/ShapeSharpnessProbe.java')], check=True)
    result = subprocess.run([str(java), '-Djava.awt.headless=true',
                             '-cp', str(classes) + os.pathsep + cp,
                             'com.atom.chat.probe.ShapeSharpnessProbe', str(output)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (output / 'probe.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='')
    sys.exit(result.returncode)


if __name__ == '__main__':
    main()
