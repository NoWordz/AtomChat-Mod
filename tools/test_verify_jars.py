"""Exercise artifact selection and native isolation using real nested ZIPs."""
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

VERIFIER = Path(__file__).with_name('verify_jars.py')


def archive(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as jar:
        for name, content in entries.items():
            jar.writestr(name, content)
    return out.getvalue()


def artifact(path, natives, prefix='META-INF/jars/', include_types=True, register_types=True):
    flatlaf = archive({
        'com/atom/chat/shaded/flatlaf/FlatLightLaf.class': b'relocated',
        'META-INF/MANIFEST.MF': 'Automatic-Module-Name: com.atom.chat.shaded.flatlaf\n',
    })
    entries = {prefix + 'flatlaf-private.jar': flatlaf,
               prefix + 'skija-shared.jar': archive({'Shared.class': b'shared'})}
    registered = [prefix + 'flatlaf-private.jar', prefix + 'skija-shared.jar']
    if include_types:
        entries[prefix + 'types-0.1.1.jar'] = archive({
            'io/github/humbleui/types/Rect.class': b'types',
        })
        if register_types:
            registered.append(prefix + 'types-0.1.1.jar')
    for native in natives:
        extension = 'dll' if native == 'windows-x64' else 'so'
        native_path = prefix + 'skija-' + native + '.jar'
        entries[native_path] = archive({'native/libskija.' + extension: b'native'})
        registered.append(native_path)
    if prefix == 'META-INF/jarjar/':
        entries[prefix + 'metadata.json'] = json.dumps({
            'jars': [{'path': jar_path} for jar_path in registered],
        }).encode('utf-8')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(archive(entries))


class ArtifactVariantsTest(unittest.TestCase):
    def run_verifier(self, platform, path):
        return subprocess.run([sys.executable, str(VERIFIER), '--platform', platform, str(path)],
                              text=True, capture_output=True)

    def test_each_loader_accepts_exactly_its_requested_native(self):
        with tempfile.TemporaryDirectory() as tmp:
            for loader, prefix in [('Fabric', 'META-INF/jars/'), ('NeoForge', 'META-INF/jarjar/'),
                                   ('Forge', 'META-INF/jarjar/')]:
                for platform in ['windows-x64', 'linux-x64']:
                    with self.subTest(loader=loader, platform=platform):
                        suffix = '-linux-x64' if platform == 'linux-x64' else ''
                        jar = Path(tmp) / f'atomchat-{loader}-0.3.0{suffix}.jar'
                        artifact(jar, [platform], prefix)
                        result = self.run_verifier(platform, jar)
                        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_missing_wrong_and_double_native_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'atomchat-linux-x64.jar'
            for natives in [[], ['windows-x64'], ['windows-x64', 'linux-x64']]:
                with self.subTest(natives=natives):
                    artifact(jar, natives)
                    result = self.run_verifier('linux-x64', jar)
                    self.assertEqual(1, result.returncode, result.stdout + result.stderr)

    def test_variant_directories_are_selected_without_mixing(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp)
            artifact(project / 'build/libs/atomchat.jar', ['windows-x64'])
            artifact(project / 'build/linux-x64/libs/atomchat-linux-x64.jar', ['linux-x64'])
            artifact(project / 'build/linux-x64/libs/atomchat-linux-x64-slim.jar', [])
            artifact(project / 'build/linux-x64/libs/atomchat-linux-x64-sources.jar', [])
            for platform in ['windows-x64', 'linux-x64']:
                result = self.run_verifier(platform, project)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            artifact(project / 'build/linux-x64/libs/old-linux-x64.jar', ['linux-x64'])
            self.assertEqual(1, self.run_verifier('linux-x64', project).returncode)

    def test_linux_name_is_required_and_wrong_platform_name_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            for platform, name in [('linux-x64', 'atomchat.jar'),
                                   ('windows-x64', 'atomchat-linux-x64.jar')]:
                jar = Path(tmp) / name
                artifact(jar, [platform])
                self.assertEqual(1, self.run_verifier(platform, jar).returncode)

    def test_invalid_platform_is_rejected(self):
        result = self.run_verifier('macos-arm64', Path('/nonexistent.jar'))
        self.assertEqual(2, result.returncode)

    def test_missing_types_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            for platform in ['windows-x64', 'linux-x64']:
                suffix = '-linux-x64' if platform == 'linux-x64' else ''
                jar = Path(tmp) / f'atomchat{suffix}.jar'
                artifact(jar, [platform], include_types=False)
                self.assertEqual(1, self.run_verifier(platform, jar).returncode)

    def test_unregistered_jarjar_types_does_not_satisfy_dependency_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'atomchat-linux-x64.jar'
            artifact(jar, ['linux-x64'], prefix='META-INF/jarjar/', register_types=False)
            result = self.run_verifier('linux-x64', jar)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn('内嵌 HumbleUI types', result.stdout)


if __name__ == '__main__':
    unittest.main()
