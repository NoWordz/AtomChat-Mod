"""Exercise artifact selection and native isolation using real nested ZIPs."""
import io
import json
import struct
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

VERIFIER = Path(__file__).with_name('verify_jars.py')
ROOT = Path(__file__).resolve().parent.parent
LOCK_VERSION = json.loads((ROOT / 'versions/atomlib.json').read_text(encoding='utf-8'))['version']


def archive(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as jar:
        for name, content in entries.items():
            jar.writestr(name, content)
    return out.getvalue()


def pe(machine):
    data = bytearray(128)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 0x3c, 64)
    data[64:68] = b'PE\0\0'
    struct.pack_into('<H', data, 68, machine)
    return bytes(data)


def elf(machine=62):
    data = bytearray(64)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', data, 18, machine)
    return bytes(data)


def artifact(path, natives, prefix='META-INF/jars/', include_types=True, register_types=True,
             machine=None, shared_version='0.143.18', native_version='0.143.18',
             embedded_version=None, register_native=True, linux_bytes=None):
    flatlaf = archive({
        'com/atom/chat/shaded/flatlaf/FlatLightLaf.class': b'relocated',
        'META-INF/MANIFEST.MF': 'Automatic-Module-Name: com.atom.chat.shaded.flatlaf\n',
    })
    entries = {prefix + 'flatlaf-private.jar': flatlaf,
               prefix + f'skija-shared-{shared_version}.jar': archive({
                   'Shared.class': b'shared',
                   'META-INF/maven/io.github.humbleui/skija-shared/pom.properties':
                       f'version={shared_version}\n',
               })}
    registered = [prefix + 'flatlaf-private.jar', prefix + f'skija-shared-{shared_version}.jar']
    if include_types:
        entries[prefix + 'types-0.2.0.jar'] = archive({
            'io/github/humbleui/types/Rect.class': b'types',
        })
        if register_types:
            registered.append(prefix + 'types-0.2.0.jar')
    for native in natives:
        extension = 'dll' if native.startswith('windows-') else 'so'
        native_path = prefix + 'skija-' + native + '-' + native_version + '.jar'
        content = pe(machine if machine is not None else
                     (0xAA64 if native == 'windows-arm64' else 0x8664)) if extension == 'dll' else (linux_bytes or elf())
        resource = 'io/github/humbleui/skija/' + native.replace('-', '/') + '/'
        entries[native_path] = archive({
            resource + ('skija.dll' if extension == 'dll' else 'libskija.so'): content,
            resource + 'skija.version': embedded_version or native_version,
            f'META-INF/maven/io.github.humbleui/skija-{native}/pom.properties':
                f'version={embedded_version or native_version}\n',
        })
        if register_native:
            registered.append(native_path)
    if prefix == 'META-INF/jarjar/':
        entries[prefix + 'metadata.json'] = json.dumps({
            'jars': [{'path': jar_path} for jar_path in registered],
        }).encode('utf-8')
    else:
        entries['fabric.mod.json'] = json.dumps({'jars': [{'file': p} for p in registered]}).encode()
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
                for platform in ['windows-x64', 'windows-arm64', 'linux-x64']:
                    with self.subTest(loader=loader, platform=platform):
                        jar = Path(tmp) / f'atomchat-{loader}-0.3.2-{platform}.jar'
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
            artifact(project / 'build/libs/atomchat-windows-x64.jar', ['windows-x64'])
            artifact(project / 'build/windows-arm64/libs/atomchat-windows-arm64.jar', ['windows-arm64'])
            artifact(project / 'build/linux-x64/libs/atomchat-linux-x64.jar', ['linux-x64'])
            artifact(project / 'build/linux-x64/libs/atomchat-linux-x64-slim.jar', [])
            artifact(project / 'build/linux-x64/libs/atomchat-linux-x64-sources.jar', [])
            for platform in ['windows-x64', 'windows-arm64', 'linux-x64']:
                result = self.run_verifier(platform, project)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            artifact(project / 'build/linux-x64/libs/old-linux-x64.jar', ['linux-x64'])
            self.assertEqual(1, self.run_verifier('linux-x64', project).returncode)

    def test_platform_suffix_is_required_and_wrong_platform_name_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            for platform, name in [('linux-x64', 'atomchat.jar'),
                                   ('windows-x64', 'atomchat.jar'),
                                   ('windows-x64', 'atomchat-linux-x64.jar'),
                                   ('windows-arm64', 'atomchat-windows-x64.jar')]:
                jar = Path(tmp) / name
                artifact(jar, [platform])
                self.assertEqual(1, self.run_verifier(platform, jar).returncode)

    def test_invalid_platform_is_rejected(self):
        result = self.run_verifier('macos-arm64', Path('/nonexistent.jar'))
        self.assertEqual(2, result.returncode)

    def test_missing_types_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            for platform in ['windows-x64', 'linux-x64']:
                jar = Path(tmp) / f'atomchat-{platform}.jar'
                artifact(jar, [platform], include_types=False)
                self.assertEqual(1, self.run_verifier(platform, jar).returncode)

    def test_unregistered_jarjar_types_does_not_satisfy_dependency_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'atomchat-linux-x64.jar'
            artifact(jar, ['linux-x64'], prefix='META-INF/jarjar/', register_types=False)
            result = self.run_verifier('linux-x64', jar)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn('内嵌 HumbleUI types', result.stdout)

    def test_x64_dll_hidden_inside_arm64_native_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'atomchat-windows-arm64.jar'
            artifact(jar, ['windows-arm64'], machine=0x8664)
            result = self.run_verifier('windows-arm64', jar)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn('PE 架构', result.stdout)

    def test_shared_and_native_version_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'atomchat-windows-x64.jar'
            artifact(jar, ['windows-x64'], native_version='0.143.17')
            result = self.run_verifier('windows-x64', jar)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn('Skija 版本一致', result.stdout)

    def test_matching_old_versions_and_renamed_old_native_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            for kwargs in [dict(shared_version='0.116.8', native_version='0.116.8'),
                           dict(embedded_version='0.116.8')]:
                with self.subTest(kwargs=kwargs):
                    jar = Path(tmp) / 'atomchat-windows-x64.jar'
                    artifact(jar, ['windows-x64'], **kwargs)
                    result = self.run_verifier('windows-x64', jar)
                    self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                    self.assertIn('Skija 版本', result.stdout)

    def test_wrong_linux_format_and_machine_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            for content in (pe(0x8664), elf(183)):
                jar = Path(tmp) / 'atomchat-linux-x64.jar'
                artifact(jar, ['linux-x64'], linux_bytes=content)
                result = self.run_verifier('linux-x64', jar)
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                self.assertIn('ELF 架构', result.stdout)

    def test_unregistered_fabric_native_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'atomchat-windows-arm64.jar'
            artifact(jar, ['windows-arm64'], register_native=False)
            self.assertEqual(1, self.run_verifier('windows-arm64', jar).returncode)


def consumer_artifact(path, loader='Fabric', dependency=True, side='CLIENT', extra=None):
    prefix = 'META-INF/jars/' if loader == 'Fabric' else 'META-INF/jarjar/'
    flatlaf = archive({
        'com/atom/chat/shaded/flatlaf/FlatLightLaf.class': b'relocated',
        'com/atom/chat/shaded/flatlaf/natives/flatlaf-windows-x86_64.dll': pe(0x8664),
        'META-INF/MANIFEST.MF': 'Automatic-Module-Name: com.atom.chat.shaded.flatlaf\n',
    })
    entries = {prefix+'flatlaf-private.jar': flatlaf,
               'atomchat-atomlib.properties': ('version=' + LOCK_VERSION + '\n').encode(),
               'com/atom/chat/compat/AtomLibDependencyCheck.class': b'client bootstrap',
               'com/atom/chat/AtomChatClient.class': b'AtomLibDependencyCheck'}
    if loader == 'Fabric':
        meta = {'id':'atomchat', 'environment':'*', 'jars':[{'file':prefix+'flatlaf-private.jar'}]}
        if dependency:
            meta['suggests'] = {'atomlib': '=' + LOCK_VERSION}
            meta['custom'] = {'atomlib:client-required': LOCK_VERSION}
        entries['fabric.mod.json'] = json.dumps(meta).encode()
    else:
        entries[prefix+'metadata.json'] = json.dumps({'jars':[{'path':prefix+'flatlaf-private.jar'}]}).encode()
        kind = 'mandatory=true' if loader=='Forge' else 'type="required"'
        text = '[[mods]]\nmodId="atomchat"\n'
        if dependency:
            text += f'[[dependencies.atomchat]]\nmodId="atomlib"\n{kind}\nversionRange="[{LOCK_VERSION}]"\nside="{side}"\n'
        entries['META-INF/mods.toml' if loader=='Forge' else 'META-INF/neoforge.mods.toml'] = text.encode()
    if extra:
        entries.update(extra)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(archive(entries))


class AtomLibConsumerTest(unittest.TestCase):
    def run_verifier(self,path):
        return subprocess.run([sys.executable,str(VERIFIER),'--profile','atomlib-consumer',str(path)],text=True,capture_output=True)

    def test_three_consumers_have_no_ui_runtime_and_keep_private_flatlaf(self):
        with tempfile.TemporaryDirectory() as temp:
            for loader in ['Forge','NeoForge','Fabric']:
                jar=Path(temp)/f'atomchat-{loader}-0.4.0.jar';consumer_artifact(jar,loader)
                result=self.run_verifier(jar)
                self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    def test_hidden_runtime_payloads_and_embedded_atomlib_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            cases = [
                {'META-INF/jars/skija-shared-0.143.18.jar':archive({'io/github/humbleui/skija/Canvas.class':b'Java'})},
                {'META-INF/jars/innocent.jar':archive({'org/lwjgl/util/yoga/Yoga.class':b'Java'})},
                {'io/github/humbleui/types/Rect.class':b'Java'},
                {'com/atom/lib/view/UiView.class':b'Java'},
                {'assets/misc/skija.dll':pe(0x8664)},
                {'assets/misc/lwjgl_yoga.dll':pe(0x8664)},
                {'META-INF/jars/atomlib-0.1.0-local.jar':archive({'resource.txt':b'not allowed'})},
            ]
            for extra in cases:
                with self.subTest(payload=list(extra)):
                    jar=Path(temp)/'atomchat-Fabric-0.4.0.jar';consumer_artifact(jar,extra=extra)
                    self.assertEqual(1,self.run_verifier(jar).returncode)

    def test_dependency_and_client_only_scope_are_required(self):
        with tempfile.TemporaryDirectory() as temp:
            for loader in ['Forge','NeoForge','Fabric']:
                jar=Path(temp)/f'atomchat-{loader}-0.4.0.jar';consumer_artifact(jar,loader,dependency=False)
                self.assertEqual(1,self.run_verifier(jar).returncode)
            for loader in ['Forge','NeoForge']:
                jar=Path(temp)/f'atomchat-{loader}-0.4.0.jar';consumer_artifact(jar,loader,side='BOTH')
                self.assertEqual(1,self.run_verifier(jar).returncode)

    def test_old_architecture_specific_names_do_not_pass_as_consumers(self):
        with tempfile.TemporaryDirectory() as temp:
            jar=Path(temp)/'atomchat-Fabric-0.4.0-windows-x64.jar';consumer_artifact(jar)
            self.assertEqual(1,self.run_verifier(jar).returncode)


if __name__ == '__main__':
    unittest.main()
