"""Run the hidden-window Skija backdrop probe after building NeoForge Windows x64.

Requires JDK 21 in JAVA_HOME/PATH and the project's already-resolved Gradle cache.
This opens no visible window and does not launch Minecraft or edit its instances.
"""
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def main():
    output = ROOT / '.work' / 'banners'
    output.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get('JAVA_HOME')
    java = Path(java_home) / 'bin' / 'java.exe' if java_home else Path(shutil.which('java') or '')
    javac = Path(java_home) / 'bin' / 'javac.exe' if java_home else Path(shutil.which('javac') or '')
    if not java.is_file() or not javac.is_file():
        raise SystemExit('Set JAVA_HOME to a Windows JDK 21 installation.')
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.startswith('#'))
    classpath = [str(ROOT / 'platforms/1.21.1-neoforge/build/classes/java/main')]
    artifacts = [('io.github.humbleui', 'skija-shared', props['skija_version']),
                 ('io.github.humbleui', 'skija-windows-x64', props['skija_version']),
                 ('io.github.humbleui', 'types', '0.2.0'),
                 ('com.google.code.gson', 'gson', '2.10.1')]
    artifacts += [('org.lwjgl', artifact, '3.3.3') for artifact in ('lwjgl', 'lwjgl-glfw', 'lwjgl-opengl')]
    for group, artifact, version in artifacts:
        names = [f'{artifact}-{version}.jar']
        if group == 'org.lwjgl':
            names.append(f'{artifact}-{version}-natives-windows.jar')
        for name in names:
            matches = list((cache / group / artifact / version).rglob(name))
            if len(matches) != 1:
                raise SystemExit(f'Expected one resolved {name} in {cache}; found {len(matches)}.')
            classpath.append(str(matches[0]))
    cp = os.pathsep.join(classpath)
    classes = output / 'probe-classes'
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tools/probes/NotificationBackdropProbe.java')], check=True)
    result = subprocess.run([str(java), '-cp', str(classes) + os.pathsep + cp,
                             'com.atom.chat.probe.NotificationBackdropProbe', str(output / 'gpu-backdrop.png')],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (output / 'gpu-probe.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='')
    result.check_returncode()


if __name__ == '__main__':
    main()
