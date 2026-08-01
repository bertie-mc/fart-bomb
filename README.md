> **Development has moved:** See [the `fart-bomb` module in the Bertie monorepo](https://github.com/bertie-mc/bertie/tree/main/mods/fart-bomb). This repository is retained read-only for historical tags, releases, and issues.

# Fart Bomb

A Whoopee Cushion fart that goes off while the wearer is alight **explodes**.

- **Minecraft:** 1.21.1
- **Loader:** NeoForge
- **Mod ID:** `fartbomb`
- **Companion mod:** [Artifacts](https://modrinth.com/mod/artifacts) (optional, but nothing happens without it)

## What it does

Artifacts' Whoopee Cushion farts on three occasions: the moment you *start* crouching, a
double jump, and equipping the cushion. If the farting player is **on fire** or standing on a
**fiery surface**, the fart detonates — an explosion centred 0.3 blocks behind and 0.3 blocks
below the midpoint of the player's hitbox, and a deeper, louder fart in place of the usual one.

### What counts as a fiery surface

| Where | Blocks |
|---|---|
| In or on lava | `isInLava()`, plus `minecraft:lava` at the feet or underfoot |
| Directly underfoot | `#fartbomb:ignites_below` — magma block, lava, lit campfires, fire |
| At the feet | `#fartbomb:ignites_at_feet` — torches (incl. soul and wall variants), lit campfires, fire, lava |

Two positions, because block shapes differ: a magma block is a full cube so you stand *above*
it, while a campfire's shortened collision box and a torch's empty one both leave you standing
*inside* that block position.

Both are datapack block tags, so the pack can add modded fire blocks without a rebuild. A
tagged block that carries the vanilla `lit` property only counts while lit — an extinguished
campfire is just a wooden step.

## Config

`config/fartbomb-common.toml`:

| Key | Default | Meaning |
|---|---|---|
| `explosion.enabled` | `true` | Master switch |
| `explosion.power` | `3.0` | Radius — creeper-strength. TNT is 4.0. Damage reaches `2 x power` blocks |
| `explosion.breakBlocks` | `true` | Destroy terrain. `false` still damages entities |
| `explosion.startFires` | `false` | Leave fire behind — compounds, since fire is a trigger |
| `explosion.triggerWhenBurning` | `true` | Being on fire is enough on its own |
| `sound.replaceSound` | `true` | Swap the normal fart for the detonation one |
| `sound.volume` | `4.0` | **Range, not loudness** — see below |
| `placement.offsetBehind` | `0.3` | Blocks behind the player, along their facing |
| `placement.offsetBelow` | `0.3` | Blocks below the hitbox midpoint |

`sound.volume` above `1.0` does not make the sound louder: `SoundEngine` clamps a sound's
final volume to `1.0` and uses values above that only to widen the radius over which it can
be heard. Loudness comes from the audio file itself.

The fart *probability* is not here — that is Artifacts' own
`config/artifacts/items.toml`, under `[whoopee_cushion] fartChance`.

## The detonation sound

`fartbomb:god_voice_fart` — `god-voice-fart-1.ogg` / `god-voice-fart-2.ogg`, derived from
Artifacts' own fart samples by [`tools/derive_sounds.py`](tools/derive_sounds.py): pitched
down to 0.7×, a +9 dB low shelf under 180 Hz, tanh saturation so the boosted low end still
reads on laptop speakers, then normalised to −0.5 dBFS. That last step matters most — the
sources peak at 0.35 and 0.63, leaving most of their headroom unused.

Measured against the originals: **+10.4 dB** and **+7.4 dB** RMS, with energy below 250 Hz
going from 3.4% → 50.5% and 11.7% → 32.3%.

These two files are derivative works of MIT-licensed assets and are carved out of this
repository's Unlicense dedication — see [NOTICE](NOTICE).

## Implementation note

No compile-time dependency on Artifacts; the mod matches its sound by resource location.

The hook is a mixin on `ServerLevel#playSeededSound` (both overloads — entity and
positional), because Artifacts' three farts do **not** share a code path. Crouching and
double-jumping go through its flatulence roll and fire an `artifacts:fart` game event;
equipping the cushion plays the same sound from the accessory framework's equip hook and
fires no game event at all. The sound is the only thing common to all three. Mixing into
`ServerLevel` rather than `Level` keeps the client's copy untouched, so it cannot fire twice
in single-player.

## Install

Download the latest JAR from the [Releases page](../../releases) and put it in your `mods/`
folder. Requires NeoForge for Minecraft 1.21.1.

## Building

`gradle build` — the built JAR is written to `build/libs/`.

## Tests

`gradle test` covers sound recognition, detonation decisions, and explosion placement.
`gradle runGameTestServer` verifies the server mixins, fiery block tags, and a real
burning-player detonation. GameTest code is kept out of the release JAR.

## License

Released into the public domain under **The Unlicense** — see [UNLICENSE](UNLICENSE).
Third-party assets and dependencies are carved out in [NOTICE](NOTICE).
