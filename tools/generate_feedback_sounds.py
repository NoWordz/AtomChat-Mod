"""Derive every feedback cue from one CC0 bubble recording, without network access.

Requires numpy and soundfile only for asset authoring. Run from any directory.
The source/license live in tools/audio-source; only the derived OGGs ship in mods.
"""
from pathlib import Path
import hashlib
import math

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
RATE = 48000
SOURCE = ROOT / 'tools/audio-source/bubble-pop.wav'
PREVIEW = ROOT / '.tmp/chat-effects/bubble-revision/audio'
PLATFORMS = ('1.21.1-fabric', '1.21.1-neoforge', '1.20.1-forge')


def lowpass(samples, cutoff):
    """A gentle one-pole filter removes the source pop's sharp upper edge."""
    alpha = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    result = np.empty_like(samples)
    previous = 0.0
    for i, value in enumerate(samples):
        previous += alpha * (float(value) - previous)
        result[i] = previous
    return result


def fades(samples):
    samples = samples.copy()
    attack = min(round(RATE * .004), len(samples) // 2)
    release = min(round(RATE * .025), len(samples) // 2)
    samples[:attack] *= np.linspace(0.0, 1.0, attack) ** 2
    samples[-release:] *= np.linspace(1.0, 0.0, release) ** 2
    return samples


def mother():
    source, rate = sf.read(SOURCE, dtype='float64', always_2d=True)
    assert len(source) > 0 and np.isfinite(source).all(), 'invalid source recording'
    mono = source.mean(axis=1)
    positions = np.arange(max(1, round(len(mono) * RATE / rate))) * rate / RATE
    mono = np.interp(positions, np.arange(len(mono)), mono)
    mono -= mono.mean()
    assert np.max(np.abs(mono)) > .001, 'silent source'
    peak = int(np.argmax(np.abs(mono)))
    # Isolate the strongest bubble and its soft tail instead of shipping silence.
    start = max(0, peak - round(RATE * .025))
    end = min(len(mono), peak + round(RATE * .18))
    base = fades(lowpass(mono[start:end], 1800.0))
    assert len(base) >= RATE * .04 and np.max(np.abs(base)) > .001
    return base / np.max(np.abs(base)) * .26


def derive(base, semitones, cutoff, gain):
    # Resampling changes pitch and duration together; every cue remains the
    # same recognizable bubble, with no unrelated oscillators or added noise.
    ratio = 2.0 ** (semitones / 12.0)
    positions = np.arange(math.ceil(len(base) / ratio)) * ratio
    samples = np.interp(positions, np.arange(len(base)), base, right=0.0)
    return fades(lowpass(samples, cutoff)) * gain


def main():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    base = mother()
    sf.write(PREVIEW / 'bubble-mother.wav', base, RATE, subtype='PCM_16')
    for cue, semitones, cutoff, gain in (
        ('button', -2.0, 1600.0, .85),
        ('send', 1.0, 1900.0, .90),
        ('panel_open', -5.0, 1300.0, .80),
    ):
        samples = derive(base, semitones, cutoff, gain)
        assert np.isfinite(samples).all() and 0 < np.max(np.abs(samples)) < .3
        wav = PREVIEW / ('feedback_' + cue + '.wav')
        ogg = PREVIEW / ('feedback_' + cue + '.ogg')
        sf.write(wav, samples, RATE, subtype='PCM_16')
        sf.write(ogg, samples, RATE, format='OGG', subtype='VORBIS')
        decoded, rate = sf.read(ogg)
        assert rate == RATE and decoded.ndim == 1 and len(decoded) == len(samples)
        assert np.isfinite(decoded).all() and 0 < np.max(np.abs(decoded)) < .35
        data = ogg.read_bytes()
        assert data.startswith(b'OggS') and len(data) > 1000
        for platform in PLATFORMS:
            target = ROOT / 'platforms' / platform / 'src/main/resources/assets/atomchat/sounds' / ogg.name
            target.write_bytes(data)
        print(f'{cue} semitones={semitones} duration={len(samples)/RATE:.3f}s '
              f'peak={np.max(np.abs(decoded)):.4f} sha256={hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
