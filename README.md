# Fart Bomb

A Whoopee Cushion fart that goes off while the wearer is alight **explodes**.

- **Minecraft:** 1.21.1
- **Loader:** NeoForge
- **Mod ID:** `fartbomb`
- **Companion mod:** [Artifacts](https://modrinth.com/mod/artifacts) (optional, but nothing happens without it)

## What it does

Artifacts' Whoopee Cushion rolls its `artifacts:flatulence` chance on two occasions — the
moment you *start* crouching, and a double jump. When the roll succeeds it plays a fart and
fires an `artifacts:fart` game event.

This mod listens for that game event. If the farting player is **on fire** or standing on a
**fiery surface**, the fart detonates: an explosion centred 0.3 blocks behind and 0.3 blocks
below the midpoint of the player's hitbox.

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
| `placement.offsetBehind` | `0.3` | Blocks behind the player, along their facing |
| `placement.offsetBelow` | `0.3` | Blocks below the hitbox midpoint |

The fart *probability* is not here — that is Artifacts' own
`config/artifacts/items.toml`, under `[whoopee_cushion] fartChance`.

## Implementation note

No mixins and no compile-time dependency on Artifacts. NeoForge posts
`VanillaGameEvent` unconditionally from `ServerLevel#gameEvent` for **every** game event,
modded ones included, so matching on the `artifacts:fart` resource key catches exactly the
moment a fart lands. If Artifacts is absent the event simply never fires.

## Install

Download the latest JAR from the [Releases page](../../releases) and put it in your `mods/`
folder. Requires NeoForge for Minecraft 1.21.1.

## Building

`./gradlew build` — the built JAR is written to `build/libs/`.

## License

Released into the public domain under **The Unlicense** — see [UNLICENSE](UNLICENSE).
Third-party assets and dependencies are carved out in [NOTICE](NOTICE).
