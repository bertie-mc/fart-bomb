#!/usr/bin/env python3
"""Derive Fart Bomb's detonation sounds from the Artifacts fart samples.

The target is the "voice of God" treatment from film — the booming, layered,
cathedral-sized sound of something speaking from the sky. That is three things
stacked, and all three matter:

  * octave-down layering, so it sounds like several voices at once rather than one
  * discrete slap echoes, the part you actually hear repeating
  * a long, dark reverb tail that keeps ringing after the repeats have died

The output files are committed under src/main/resources/assets/fartbomb/sounds/.
This script exists so that derivation is reproducible and auditable rather than a
one-off edit nobody can repeat — see NOTICE for the licensing that governs it.

    python tools/derive_sounds.py --jar path/to/artifacts-neoforge-13.2.1.jar

Requires: numpy, soundfile (libsndfile >= 1.1 for Ogg Vorbis write).

Everything is deterministic: the reverb tail is seeded noise, so re-running this
reproduces the committed files byte for byte.
"""

import argparse
import math
import pathlib
import zipfile

import numpy as np
import soundfile as sf

SOURCES = ("fart1", "fart2")

# --- layering: the same sample at several speeds, mixed.
# --- The lead layer stays near original pitch on purpose. What identifies a fart is its
# --- buzzy mid-band and its attack, and dropping the lead a full octave smears both into
# --- an anonymous drone — the effect reads as "huge" but no longer as "fart". The octave-
# --- down copies sit underneath as weight, quiet enough not to mask the lead.
LAYERS = ((0.88, 1.00), (0.62, 0.40), (0.45, 0.22))   # (playback rate, gain)

# --- tone. Boost lower and by less than before: a big shelf at 200 Hz eats the buzz band.
SHELF_HZ, SHELF_DB = 140.0, 4.0
DRIVE = 1.35                   # heavy drive smears the attack, which is half the recognition

# --- echo: the audible repeats. Spaced wider and decaying faster so the first repeat does
# --- not land on top of the source still ringing.
ECHO_MS = 300.0
ECHO_FEEDBACK = 0.50
ECHO_REPEATS = 3

# --- reverb: a big dark hall, but kept behind the source rather than on top of it.
RT60 = 2.0                     # seconds to -60 dB
PREDELAY_MS = 90.0             # the single biggest factor in the source staying legible:
                               # the hall does not start until the fart itself has landed
TAIL_DAMP_HZ = 2600.0          # tail is low-passed; a bright tail sounds like a tin can
WET = 0.45                     # was 0.78, which left almost no dry signal to recognise
EARLY_TAPS_MS = ((23.0, 0.50), (41.0, 0.42), (67.0, 0.34), (98.0, 0.26), (137.0, 0.18))
SEED = 0xFA27

PEAK_DBFS = -0.5
FADE_MS = 3.0
TAIL_FADE_MS = 350.0           # smooth the very end so the tail does not cut off abruptly


def resample(x: np.ndarray, rate: float) -> np.ndarray:
    """Linear-interpolated resample. rate < 1 lowers pitch and lengthens."""
    n_out = int(math.ceil(len(x) / rate))
    return np.interp(np.arange(n_out) * rate, np.arange(len(x)), x)


def layer(x: np.ndarray) -> np.ndarray:
    """Mix the sample against pitched-down copies of itself."""
    voices = [(resample(x, rate), gain) for rate, gain in LAYERS]
    out = np.zeros(max(len(v) for v, _ in voices))
    for v, gain in voices:
        out[:len(v)] += v * gain
    return out


def low_shelf(x: np.ndarray, sr: int, f0: float, gain_db: float) -> np.ndarray:
    """RBJ audio-EQ-cookbook low shelf at S = 1, direct form I."""
    a_ = 10.0 ** (gain_db / 40.0)
    w0 = 2.0 * math.pi * f0 / sr
    cos_w0, sin_w0 = math.cos(w0), math.sin(w0)
    alpha = sin_w0 / 2.0 * math.sqrt(2.0)
    tsa = 2.0 * math.sqrt(a_) * alpha

    b0 = a_ * ((a_ + 1) - (a_ - 1) * cos_w0 + tsa)
    b1 = 2 * a_ * ((a_ - 1) - (a_ + 1) * cos_w0)
    b2 = a_ * ((a_ + 1) - (a_ - 1) * cos_w0 - tsa)
    a0 = (a_ + 1) + (a_ - 1) * cos_w0 + tsa
    a1 = -2 * ((a_ - 1) + (a_ + 1) * cos_w0)
    a2 = (a_ + 1) + (a_ - 1) * cos_w0 - tsa
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0

    y = np.empty_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, xn in enumerate(x):
        yn = b0 * xn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yn
        x2, x1 = x1, xn
        y2, y1 = y1, yn
    return y


def one_pole_lp(x: np.ndarray, sr: int, fc: float) -> np.ndarray:
    a = math.exp(-2.0 * math.pi * fc / sr)
    y = np.empty_like(x)
    prev = 0.0
    for i, v in enumerate(x):
        prev = (1.0 - a) * v + a * prev
        y[i] = prev
    return y


def saturate(x: np.ndarray, drive: float) -> np.ndarray:
    return np.tanh(x * drive) / np.tanh(drive)


def echo(x: np.ndarray, sr: int) -> np.ndarray:
    """Discrete repeats at a fixed spacing, each quieter than the last."""
    d = int(sr * ECHO_MS / 1000.0)
    out = np.zeros(len(x) + d * ECHO_REPEATS)
    out[:len(x)] = x
    gain = 1.0
    for i in range(1, ECHO_REPEATS + 1):
        gain *= ECHO_FEEDBACK
        out[d * i:d * i + len(x)] += x * gain
    return out


def hall_impulse(sr: int) -> np.ndarray:
    """Synthetic hall: sparse early reflections, then exponentially decaying dark noise."""
    n = int(sr * RT60)
    rng = np.random.default_rng(SEED)
    pre = int(sr * PREDELAY_MS / 1000.0)

    ir = np.zeros(n)
    for ms, gain in EARLY_TAPS_MS:
        idx = pre + int(sr * ms / 1000.0)
        if idx < n:
            ir[idx] += gain

    t = np.arange(n - pre) / sr
    tail = rng.standard_normal(n - pre) * np.exp(-6.907 * t / RT60)  # 6.907 = ln(1000) -> -60 dB
    ir[pre:] += one_pole_lp(tail, sr, TAIL_DAMP_HZ) * 0.6

    peak = np.abs(ir).max()
    return ir / peak if peak else ir


def fft_convolve(x: np.ndarray, h: np.ndarray) -> np.ndarray:
    n = len(x) + len(h) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(h, size), size)[:n]


def reverb(x: np.ndarray, sr: int) -> np.ndarray:
    wet = fft_convolve(x, hall_impulse(sr))
    out = np.zeros(len(wet))
    out[:len(x)] += x * (1.0 - WET)
    peak = np.abs(wet).max()
    if peak:
        wet = wet / peak * np.abs(x).max()
    return out + wet * WET


def normalise(x: np.ndarray, dbfs: float) -> np.ndarray:
    peak = float(np.abs(x).max())
    return x * (10.0 ** (dbfs / 20.0) / peak) if peak else x


def fade(x: np.ndarray, sr: int) -> np.ndarray:
    head = max(1, int(sr * FADE_MS / 1000.0))
    tail = max(1, int(sr * TAIL_FADE_MS / 1000.0))
    x[:head] *= np.linspace(0.0, 1.0, head)
    x[-tail:] *= np.linspace(1.0, 0.0, tail) ** 2
    return x


def trim_silence(x: np.ndarray, floor_db: float = -60.0) -> np.ndarray:
    """Drop an inaudible tail so the file is not padded with near-silence."""
    thresh = 10.0 ** (floor_db / 20.0) * np.abs(x).max()
    loud = np.flatnonzero(np.abs(x) > thresh)
    return x[:loud[-1] + 1] if len(loud) else x


def low_ratio(x: np.ndarray, sr: int, cutoff: float = 250.0) -> float:
    spec = np.abs(np.fft.rfft(x)) ** 2
    freqs = np.fft.rfftfreq(len(x), 1.0 / sr)
    total = spec.sum()
    return float(spec[freqs < cutoff].sum() / total) if total else 0.0


def main() -> None:
    here = pathlib.Path(__file__).resolve().parent.parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", required=True, help="path to an artifacts-neoforge-*.jar")
    ap.add_argument("--out", default=str(here / "src/main/resources/assets/fartbomb/sounds"))
    args = ap.parse_args()

    out_dir = pathlib.Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(args.jar) as jar:
        for name in SOURCES:
            with jar.open(f"assets/artifacts/sounds/{name}.ogg") as fh:
                src, sr = sf.read(fh, dtype="float64", always_2d=False)
            if src.ndim > 1:  # mono, or Minecraft stops treating it as positional
                src = src.mean(axis=1)

            before = (float(np.sqrt((src ** 2).mean())), low_ratio(src, sr), len(src) / sr)

            y = layer(src)
            y = low_shelf(y, sr, SHELF_HZ, SHELF_DB)
            y = saturate(y, DRIVE)
            y = echo(y, sr)
            y = reverb(y, sr)
            y = trim_silence(y)
            y = normalise(y, PEAK_DBFS)
            y = fade(y, sr)

            after = (float(np.sqrt((y ** 2).mean())), low_ratio(y, sr), len(y) / sr)

            dest = out_dir / f"big_{name}.ogg"
            sf.write(dest, y, sr, format="OGG", subtype="VORBIS")

            print(f"{name} -> {dest.name}")
            print(f"   duration  {before[2]:.3f}s -> {after[2]:.3f}s")
            print(f"   rms       {before[0]:.4f} -> {after[0]:.4f}  "
                  f"({20 * math.log10(after[0] / before[0]):+.1f} dB)")
            print(f"   <250 Hz   {before[1] * 100:.1f}% -> {after[1] * 100:.1f}% of energy")
            print(f"   echoes    {ECHO_REPEATS} at {ECHO_MS:.0f} ms, reverb RT60 {RT60:.1f}s, "
                  f"{WET * 100:.0f}% wet")


if __name__ == "__main__":
    main()
