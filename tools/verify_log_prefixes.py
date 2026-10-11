from pathlib import Path
import re
ROOT=Path(__file__).resolve().parent.parent
CALL=re.compile(r'\b(?:AtomChat\.LOGGER|LOGGER|logger|LOG|log)\.(?:info|warn|error|debug|trace|warning|severe)\s*\(')
WRAP='com.atom.chat.diagnostics.AtomChatLog.message('
def first_end(text,start):
    depth=0; quoted=False; escape=False
    for i in range(start,len(text)):
        c=text[i]
        if quoted:
            if escape: escape=False
            elif c=='\\': escape=True
            elif c=='"': quoted=False
        elif c=='"': quoted=True
        elif c=='(': depth+=1
        elif c==')':
            if depth==0: return i
            depth-=1
        elif c==',' and depth==0: return i
    raise ValueError('unterminated call')
def chain(path,expr):
    old=re.match(r'"\[(notify|test|capture|echo|route|sender|avatar|media)\]',expr.strip())
    if old: return old.group(1)
    parts=str(path).replace('\\','/').lower()
    if 'notification' in parts: return 'notify'
    if 'feedback' in parts: return 'feedback'
    if '/fun/' in parts: return 'fun'
    if 'testcompanion' in parts: return 'test'
    for key in ['avatar','media','history','config','pack']:
        if key in parts: return key
    if any(key in parts for key in ['skia','render','frameprofile','canvas']): return 'render'
    if any(key in parts for key in ['font','cache','wallpaper','image','bannerstore']): return 'resource'
    if '/net/' in parts: return 'network'
    if 'chathud' in parts or 'chattemplates' in parts: return 'capture'
    if '/diagnostics/' in parts or path.name in ['AtomChat.java','AtomChatClient.java']: return 'startup'
    if '/screen/' in parts or '/ui/' in parts or '/settings/' in parts: return 'ui'
    return 'compat'
def sources():
    return sorted(p for folder in ['shared','layers','platforms'] for p in (ROOT/folder).rglob('*.java') if '/src/main/' in str(p).replace('\\','/'))
def violations(text):
    errors=[]
    for call in CALL.finditer(text):
        start=call.end(); end=first_end(text,start); expr=text[start:end].strip()
        if not expr.startswith(WRAP): errors.append(('missing',call.start()))
        elif not re.match(re.escape(WRAP)+r'"[a-z][a-z0-9.]*",',expr): errors.append(('chain',call.start()))
        if '[AtomChat][AtomChat]' in expr or re.search(r'\[AtomChat\]\[[^]]+\].*?\[AtomChat\]\[',expr): errors.append(('duplicate',call.start()))
    if re.search(r'\bSystem\.(?:out|err)\.|\.printStackTrace\(',text): errors.append(('unowned-outlet',0))
    return errors

def verify():
    count=0
    failures=[]
    for p in sources():
        text=p.read_text(encoding='utf-8')
        count+=len(CALL.findall(text))
        failures.extend((str(p),kind,text[:pos].count('\n')+1) for kind,pos in violations(text))
    if count<246: failures.append((str(ROOT),'coverage-floor',count))
    for item in failures: print(*item,sep=':')
    print('AtomChat production log outlets:',count,'violations:',len(failures))
    return int(bool(failures))
if __name__=='__main__':
    import sys
    sys.exit(verify())
