"""Original soft electronic cues, deterministic PCM -> Vorbis, no borrowed audio."""
from pathlib import Path
import math, struct, wave
import numpy as np
import soundfile as sf
ROOT=Path(__file__).resolve().parent.parent
TEMP=ROOT/'.tmp/chat-effects/audio'
TEMP.mkdir(parents=True,exist_ok=True)
RATE=48000
for cue,duration,start,end in [('button',.07,1400,1050),('send',.16,620,1180),('panel_open',.20,360,720)]:
    samples=[]
    total=round(RATE*duration)
    for i in range(total):
        t=i/RATE; p=i/total
        envelope=min(1,t/.006)*(1-p)**2
        phase=2*math.pi*(start*t+(end-start)*t*t/(2*duration))
        signal=math.sin(phase)
        if cue=='panel_open': signal=.72*signal+.28*math.sin(phase*1.5)
        samples.append(round(signal*envelope*.28*32767))
    wav=TEMP/f'feedback_{cue}.wav'; ogg=TEMP/f'feedback_{cue}.ogg'
    with wave.open(str(wav),'wb') as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(RATE)
        w.writeframes(struct.pack('<'+'h'*len(samples),*samples))
    sf.write(str(ogg),np.asarray(samples,dtype=np.float32)/32768,RATE,format='OGG',subtype='VORBIS')
    info=sf.info(str(ogg)); assert info.channels==1 and info.frames==total and info.subtype=='VORBIS'
    data=ogg.read_bytes();assert data.startswith(b'OggS') and len(data)>1000
    for platform in ['1.21.1-fabric','1.21.1-neoforge','1.20.1-forge']:
        target=ROOT/'platforms'/platform/'src/main/resources/assets/atomchat/sounds'/ogg.name
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    print(cue,'duration',duration,'peak',max(abs(x) for x in samples)/32767,'bytes',len(data))
