"""Run the hidden-window glass-clamp probe against the repo's real panel-blur
rounded shader (no Minecraft, no Skia: raw LWJGL compiles the fsh/vsh).

Requires JDK 21 in JAVA_HOME/PATH and the project's already-resolved Gradle
cache. Opens no visible window and does not launch Minecraft or edit its
instances.

Optional args are forwarded to the probe: [repoRoot] [fshPath] [vshPath].
The mutation test passes a clamp-enabled fsh copy and expects a failure.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent


def main():
    output = ROOT / '.work' / 'glass'
    output.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get('JAVA_HOME')
    java = Path(java_home) / 'bin' / 'java.exe' if java_home else Path(shutil.which('java') or '')
    javac = Path(java_home) / 'bin' / 'javac.exe' if java_home else Path(shutil.which('javac') or '')
    if not java.is_file() or not javac.is_file():
        raise SystemExit('Set JAVA_HOME to a Windows JDK 21 installation.')
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
    classpath = []
    for artifact in ('lwjgl', 'lwjgl-glfw', 'lwjgl-opengl'):
        names = [f'{artifact}-3.3.3.jar', f'{artifact}-3.3.3-natives-windows.jar']
        matches = []
        for version_dir in (cache / 'org.lwjgl' / artifact).glob('*'):
            for name in names:
                matches += list(version_dir.rglob(name))
        if not matches:
            raise SystemExit(f'No resolved {names[0]} in {cache}; build the project once first.')
        classpath += [str(m) for m in matches]
    cp = os.pathsep.join(classpath)
    classes = output / 'probe-classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tools/probes/PanelBlurGlassProbe.java')], check=True)
    result = subprocess.run([str(java), '-cp', str(classes) + os.pathsep + cp,
                             'com.atom.chat.probe.PanelBlurGlassProbe', *sys.argv[1:]],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (output / 'glass-probe.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='')
    sys.exit(result.returncode)


if __name__ == '__main__':
    main()
