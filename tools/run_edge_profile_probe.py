"""Run the edge profile probe (tools/probes/EdgeProfileProbe.java): renders the
real card/bubble primitives at the session density and prints halo / edge / line
widths in device pixels.

Needs the target's compiled main classes:
  cd platforms/1.21.1-neoforge && ./gradlew classes
"""
from pathlib import Path
import os, subprocess, sys

ROOT = Path(__file__).resolve().parent.parent


def main():
    output = ROOT / '.work' / '2026-11-02-edge-profile'
    output.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get('JAVA_HOME')
    java = Path(java_home) / 'bin' / 'java.exe' if java_home else Path(os.popen('where java').read().split()[0])
    javac = Path(java_home) / 'bin' / 'javac.exe' if java_home else Path(os.popen('where javac').read().split()[0])
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.startswith('#'))
    skija = props.get('skija_version', '0.143.18')

    main_classes = ROOT / 'platforms/1.21.1-neoforge/build/atomlib-consumer/classes/java/main'
    if not main_classes.is_dir():
        raise SystemExit('build platforms/1.21.1-neoforge first: ./gradlew classes')
    classpath = [str(main_classes)]
    atomlib = [m for m in (ROOT / 'build/audit-20261101/maven/com/atom/lib/atomlib-1.21.1-neoforge').rglob('*.jar')
               if 'sources' not in m.name and 'javadoc' not in m.name]
    if atomlib:
        classpath.append(str(atomlib[0]))
    for group, artifact, version in [('io.github.humbleui', 'skija-shared', skija),
                                     ('io.github.humbleui', 'skija-windows-x64', skija),
                                     ('io.github.humbleui', 'types', '0.2.0'),
                                     ('com.google.code.gson', 'gson', '2.10.1')]:
        matches = [m for m in sorted((cache / group / artifact).glob(f'*/**/{artifact}-{version}.jar'))
                   if 'sources' not in m.name and 'javadoc' not in m.name]
        if not matches:
            raise SystemExit(f'Missing {artifact}-{version}.jar in {cache}.')
        classpath.append(str(matches[0]))

    cp = os.pathsep.join(classpath)
    classes = output / 'probe-classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tools/probes/EdgeProfileProbe.java')], check=True)
    result = subprocess.run([str(java), '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + cp,
                             'com.atom.chat.probe.EdgeProfileProbe', str(output)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    (output / 'probe.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='')
    sys.exit(result.returncode)


if __name__ == '__main__':
    main()
