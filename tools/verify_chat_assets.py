"""Validate explicitly selected jars: this round's effects and feedback resources."""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXPECTED_VERSION = next(
    line.split("=", 1)[1].strip()
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
    if line.strip().startswith("mod_version=")
)
CLASSES=("fun/ChatEffectMatcher","fun/ChatEffectSession","fun/ChatEffectRenderer","fun/ChatEffectEvents",
         "audio/FeedbackSoundPolicy","audio/FeedbackSoundController","diagnostics/AtomChatLog")
CUES=("button","send","panel_open")
def verify(path):
    path=Path(path)
    with zipfile.ZipFile(path) as jar:
        for cls in CLASSES: assert 'com/atom/chat/'+cls+'.class' in jar.namelist(),cls
        prefix='assets/atomchat/'
        sounds=json.loads(jar.read(prefix+'sounds.json'))
        audio={}
        for cue in CUES:
            key='feedback_'+cue
            assert sounds[key]['sounds']==['atomchat:'+key],key
            data=jar.read(prefix+'sounds/'+key+'.ogg')
            assert data.startswith(b'OggS') and len(data)>1000,key
            audio[key]=data
        for locale in ['en_us','zh_cn']:
            lang=json.loads(jar.read(prefix+'lang/'+locale+'.json'))
            for key in ['sounds','experimental','experimental.fun','sounds.feedback_volume']:
                assert lang['atomchat.settings.'+key],(locale,key)
        metadata=next((jar.read(n).decode('utf-8') for n in ['fabric.mod.json','META-INF/neoforge.mods.toml','META-INF/mods.toml'] if n in jar.namelist()),'')
        if metadata.lstrip().startswith("{"):
            actual_version = json.loads(metadata).get("version")
        else:
            match = re.search(r'(?m)^\s*version\s*=\s*"([^"]+)"', metadata)
            actual_version = match.group(1) if match else None
        assert actual_version == EXPECTED_VERSION, (
            "version drift", actual_version, EXPECTED_VERSION
        )
    print('PASS chat assets/version:',path)
    return audio
if __name__=='__main__':
    assert len(sys.argv)>1,'Pass this round exact jar paths'
    results=[verify(path) for path in sys.argv[1:]]
    assert all(data==results[0] for data in results),'cross-platform sound mismatch'
