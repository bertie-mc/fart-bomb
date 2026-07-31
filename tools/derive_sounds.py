#!/usr/bin/env python3
"""Derive Fart Bomb's detonation sounds from the Artifacts fart samples.

The output files are committed under src/main/resources/assets/fartbomb/sounds/.
This script exists so that derivation is reproducible and auditable rather than a
one-off edit nobody can repeat — see NOTICE for the licensing that governs it.

    python tools/derive_sounds.py --jar path/to/artifacts-neoforge-13.2.1.jar

Requires: numpy, soundfile (libsndfile >= 1.1 for Ogg Vorbis write).

The chain, in order:

  1. pitch down      resample so the sample plays slower and lower, deepening it and
                     giving the detonation more weight than a normal fart
  2. low shelf       RBJ biquad, boosting everything under the corner frequency
  3. saturation      tanh soft clip; adds harmonics of the boosted low end so the bass
                     survives laptop speakers that cannot reproduce the fundamental
  4. normalise       peak to -0.5 dBFS, reclaiming the headroom the sources leave unused
                     (fart1 peaks at just 0.35, roughly 9 dB down)
  5. fade            2 ms in/out, so resampling cannot leave a click at either end
"""

import argparse
import math
import pathlib
import zipfile

import numpy as np
import soundfile as sf

SOURCES = ("fart1", "fart2")

PITCH = 0.70          # playback rate; < 1 lowers pitch and lengthens
SHELF_HZ = 180.0      # low-shelf corner
SHELF_DB = 9.0        # low-shelf gain
DRIVE = 2.2           # tanh drive; 1.0 is nearly clean
PEAK_DBFS = -0.5      # normalisation target
FADE_MS = 2.0


def pitch_down(x: np.ndarray, rate: float) -> np.ndarray:
    """Linear-interpolated resample. Reading the source more slowly than it is written
    back lowers the pitch and stretches the duration by 1/rate."""
    n_out = int(math.ceil(len(x) / rate))
    pos = np.arange(n_out) * rate
    return np.interp(pos, np.arange(len(x)), x)


def low_shelf(x: np.ndarray, sr: int, f0: float, gain_db: float) -> np.ndarray:
    """RBJ audio-EQ-cookbook low shelf at S = 1, direct form I."""
    a_ = 10.0 ** (gain_db / 40.0)
    w0 = 2.0 * math.pi * f0 / sr
    cos_w0, sin_w0 = math.cos(w0), math.sin(w0)
    alpha = sin_w0 / 2.0 * math.sqrt(2.0)
    two_sqrt_a_alpha = 2.0 * math.sqrt(a_) * alpha

    b0 = a_ * ((a_ + 1) - (a_ - 1) * cos_w0 + two_sqrt_a_alpha)
    b1 = 2 * a_ * ((a_ - 1) - (a_ + 1) * cos_w0)
    b2 = a_ * ((a_ + 1) - (a_ - 1) * cos_w0 - two_sqrt_a_alpha)
    a0 = (a_ + 1) + (a_ - 1) * cos_w0 + two_sqrt_a_alpha
    a1 = -2 * ((a_ - 1) + (a_ + 1) * cos_w0)
    a2 = (a_ + 1) + (a_ - 1) * cos_w0 - two_sqrt_a_alpha

    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0

    y = np.empty_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, xn in enumerate(x):
        yn = b0 * xn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yn
        x2, x1 = x1, xn
        y2, y1 = y1, yn
    return y


def saturate(x: np.ndarray, drive: float) -> np.ndarray:
    return np.tanh(x * drive) / np.tanh(drive)


def normalise(x: np.ndarray, dbfs: float) -> np.ndarray:
    peak = float(np.abs(x).max())
    if peak == 0.0:
        return x
    return x * (10.0 ** (dbfs / 20.0) / peak)


def fade(x: np.ndarray, sr: int, ms: float) -> np.ndarray:
    n = max(1, int(sr * ms / 1000.0))
    n = min(n, len(x) // 2)
    ramp = np.linspace(0.0, 1.0, n)
    x[:n] *= ramp
    x[-n:] *= ramp[::-1]
    return x


def low_ratio(x: np.ndarray, sr: int, cutoff: float = 250.0) -> float:
    """Share of total spectral energy below `cutoff` — used to report the bass lift."""
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

            if src.ndim > 1:  # keep it mono, or Minecraft stops treating it as positional
                src = src.mean(axis=1)

            before = (float(np.abs(src).max()), float(np.sqrt((src ** 2).mean())), low_ratio(src, sr))

            y = pitch_down(src, PITCH)
            y = low_shelf(y, sr, SHELF_HZ, SHELF_DB)
            y = saturate(y, DRIVE)
            y = normalise(y, PEAK_DBFS)
            y = fade(y, sr, FADE_MS)

            after = (float(np.abs(y).max()), float(np.sqrt((y ** 2).mean())), low_ratio(y, sr))

            dest = out_dir / f"big_{name}.ogg"
            sf.write(dest, y, sr, format="OGG", subtype="VORBIS")

            print(f"{name} -> {dest.name}")
            print(f"   duration  {len(src)/sr:.3f}s -> {len(y)/sr:.3f}s")
            print(f"   peak      {before[0]:.3f} -> {after[0]:.3f}")
            print(f"   rms       {before[1]:.4f} -> {after[1]:.4f}  "
                  f"({20*math.log10(after[1]/before[1]):+.1f} dB)")
            print(f"   <250 Hz   {before[2]*100:.1f}% -> {after[2]*100:.1f}% of energy")


if __name__ == "__main__":
    main()
