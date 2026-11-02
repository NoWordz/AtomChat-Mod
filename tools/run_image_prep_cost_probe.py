"""Run the colour-space / AA blending probe (see tools/probes/ImagePrepCostProbe.java)."""
from pathlib import Path
import os, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parent.parent


def main():
    output = ROOT / '.work' / '2026-11-02-image-prep-cost'
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
    main_classes = ROOT / 'platforms/1.21.1-neoforge/build/atomlib-consumer/classes/java/main'
    if not main_classes.is_dir():
        raise SystemExit('build platforms/1.21.1-neoforge first: ./gradlew classes')
    classpath = [str(main_classes)]
    for group, artifact, version in [('io.github.humbleui', 'skija-shared', skija),
                                     ('io.github.humbleui', 'skija-windows-x64', skija),
                                     ('io.github.humbleui', 'types', '0.2.0')]:
        matches = [m for m in sorted((cache / group / artifact).glob(f'*/**/{artifact}-{version}.jar'))
                   if 'sources' not in m.name and 'javadoc' not in m.name]
        if not matches:
            raise SystemExit(f'Expected {artifact}-{version}.jar in {cache}.')
        classpath.append(str(matches[0]))
    cp = os.pathsep.join(classpath)
    classes = output / 'probe-classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tools/probes/ImagePrepCostProbe.java')], check=True)
    result = subprocess.run([str(java), '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + cp,
                             'com.atom.chat.probe.ImagePrepCostProbe', str(output)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (output / 'probe.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='')
    sys.exit(result.returncode)


if __name__ == '__main__':
    main()
