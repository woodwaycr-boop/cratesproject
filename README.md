# CrateCore

Paper 1.21.11 plugin. Requires Vault and an economy plugin (e.g. EssentialsX).

## How it works
- A crate is any block (chest, ender chest or barrel work best) that an admin marks with `/crate set <crate>`.
- Players need a key for that crate. **Right-click** the crate holding the key to open it. **Left-click** it to preview the rewards and their chances.
- Opening plays a roulette: the rewards scroll across a menu with a rainbow border, clicking faster at first and slowing to a stop on the winner. Sparks climb around the crate while it spins.
- A reward can give money (Vault), items, and/or run console commands. Chances are weights, shown as percentages in the preview.
- **Great wins** (rewards at or below 5% chance by default) add a title, a rising bell arpeggio, a flame burst and a fireworks barrage. The fireworks do no damage.
- Crates keep working inside WorldGuard-protected regions. Crate blocks can't be broken or blown up.

## Safety
- **One crate at a time per player.** While a reel is running, that player's keys do nothing.
- The winner is rolled before the spin starts. The reward is paid when the reel stops, or **immediately** if the player closes the menu (skipping the animation), disconnects, dies, or the server stops or reloads.
- The key is taken only after the menu opens, and never taken if the economy is missing for a crate that pays money.
- A hard server crash during a spin (a few seconds) can still lose that one key. Re-give it with `/crate givekey`.

## Commands and permissions
| Command | Permission | Default |
|---|---|---|
| `/crate preview <crate>` | `cratecore.use` | everyone |
| `/crate set <crate>` (look at a block) | `cratecore.admin` | op |
| `/crate remove` (look at a crate block) | `cratecore.admin` | op |
| `/crate givekey <player> <crate> [amount]` | `cratecore.admin` | op, and console |
| `/crate list`, `/crate reload` | `cratecore.admin` | op |

Opening and previewing need `cratecore.use`, which everyone has by default.

## Getting keys to players
- **By hand or from other plugins:** `/crate givekey <player> <crate> [amount]`. The player must be online. Console can run it, so vote plugins and quest plugins can call it.
- **Store:** the same command works from a Tebex package. Check Mojang's current commercial guidelines before selling random-reward keys for real money.

## Config
`config.yml` has three example crates (common, rare, legendary) and an `animation:` section for speed, sounds and fireworks. Amounts are placeholders. Run `/crate reload` after editing. The preview fits 28 rewards per crate.
- Every animation setting and message is optional. Anything missing from your config falls back to a built-in default, so updating the jar never breaks an old config.
- Sounds are plain ids (`minecraft:block.note_block.hat`), so resource-pack sounds work too.
- Force or forbid the fireworks on a reward with `great-win: true` or `false`.
- Set `animation.enabled: false` for instant opening with no reel.
- Crate block positions are stored in `plugins/CrateCore/locations.yml`.

## Updating from 1.0.0
Upload the new `src`, `pom.xml` and `README.md` over the old ones in your GitHub repository (same names are replaced, nothing needs deleting). The build runs by itself. Download `CrateCore-1.1.0.jar` from **Releases**, delete `CrateCore-1.0.0.jar` from the server's `plugins` folder, upload the new jar, and restart. Your crates and crate blocks are kept.

## Build the jar
**GitHub (nothing to install):** create a repository, upload this folder's contents (`pom.xml`, `README.md`, `src`, `.github`), open the Actions tab, and wait for the green check. Download `CrateCore-1.1.0.jar` from **Releases** on the repo's front page.
**Your PC:** install JDK 21 and Maven, run `mvn clean package`, take `target/CrateCore-1.1.0.jar`.

## Install on ScalaCube
1. Server must run Paper 1.21.11.
2. Upload `CrateCore-1.1.0.jar`, Vault, and EssentialsX to `/plugins`, then restart.
3. Look at a chest and run `/crate set common`, then `/crate givekey <you> common`.
