<div align="center">

# ✦ EepyTab

**A modern, softly animated tab list for the eepy Minecraft server.**

Violet-to-blue gradients, clean dividers, live server stats and rank-sorted names – built for [mc.eepy.at](https://eepy.at).

[![Build](https://github.com/1870s/EepyTab/actions/workflows/build.yml/badge.svg)](https://github.com/1870s/EepyTab/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/1870s/EepyTab?label=release)](https://github.com/1870s/EepyTab/releases/latest)
[![Paper](https://img.shields.io/badge/Paper-26.1.2-2b7bb9)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-e76f00)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/license-MIT-3da639)](LICENSE)

</div>

---

## ✨ Features

- 🎨 **Designed, not just text** – a soft violet / blue / white palette, a slowly flowing title gradient, faded divider lines and evenly spaced stats.
- 📊 **Live information** – online / max players, ping, TPS, MSPT, server load, staff online, world, mode, local time and a time-of-day greeting. Values the server can't provide are shown as `–`, never made up.
- 🏷️ **Ranks & sorting** – formatted prefixes, name colours and rank-based ordering via the native player-list order. No scoreboard teams, so nametag and team plugins keep working.
- 🔐 **LuckPerms** (optional) – ranks come from groups (primary group, then heaviest matching group), weights and prefixes are respected, and group changes appear **instantly**.
- 🧩 **PlaceholderAPI** (optional) – use any `%placeholder%` in headers, footers, animations and names, and use `%eepytab_…%` placeholders in other plugins.
- 🌍 **Layouts per world, rank, permission or player count** – plus per-world names, modes, or opting a world out entirely.
- 🌙 **Calm animations** – flowing gradients, gentle colour pulses and rotating messages. No rainbow, no blinking, fully switchable off.
- 🛠️ **Maintenance banner** – `/eepytab maintenance on` adds a notice to everyone's tab list.
- 👻 **Vanish aware** – vanished players are not counted for people who can't see them.
- ⚡ **Lightweight** – updates are spread across ticks, shared lines are rendered once, unchanged packets are never resent.

## 🖼️ The design

```
                     ✦   e e p y   ✦                      ← flowing violet → blue → white
                 cozy minecraft · mc.eepy.at
      ─────────────────────────────────────────────        ← fades out to both sides

          Good evening, Muezza. Welcome to eepy!           ← rotates every 6 s

   ONLINE 12/100    •    PING 23ms    •    TPS 20.0        ← ping/TPS coloured green → amber → rose

   OWNER │ Muezza       ADMIN │ Lena       MOD │ Kai        ← vanilla player grid,
   VIP │ Jonas          Alex               Sam                 sorted by rank

      ─────────────────────────────────────────────
               WEBSITE  ›  eepy.at
               DISCORD  ›  dsc.gg/muezza

                  Play at mc.eepy.at                       ← rotates every 8 s
             Overworld  ·  Survival  ·  21:30
```

Staff (permission `eepytab.layout.staff`, ops by default) get one extra line:
`LOAD |||||||||||| 12%  •  MSPT 3.2  •  STAFF 2`.

## 📦 Installation

1. Download `EepyTab-x.y.z.jar` from [**Releases**](https://github.com/1870s/EepyTab/releases/latest).
2. Put it into your server's `plugins/` folder (optionally with LuckPerms and PlaceholderAPI).
3. Restart the server. Adjust `plugins/EepyTab/config.yml` and run `/eepytab reload`.

**Requirements:** [Paper](https://papermc.io/downloads/paper) 26.1.2 · Java 25

| Paper   | Status |
|---------|:------:|
| 26.1.2  | ✅ tested on a live server (LuckPerms 5.5.90, PlaceholderAPI 2.12.3) |
| 26.2, 26.3 | ⚙️ compiles against the API (checked by CI weekly), not play-tested |

## 🎮 Commands & permissions

Alias: `/etab`

| Command | Permission | Description |
|---|---|---|
| `/eepytab` | – | Plugin information |
| `/eepytab help` | – | Lists the commands you can use |
| `/eepytab reload` | `eepytab.reload` | Reloads `config.yml`; a broken file is rejected and the old config stays active |
| `/eepytab preview [layout]` | `eepytab.preview` | Refreshes your tab list, or shows another layout to you for 30 s. From the console it prints the design |
| `/eepytab debug [player]` | `eepytab.debug` | Versions, hooks, render timings, packet counts, a player's rank and layout |
| `/eepytab maintenance [on\|off]` | `eepytab.maintenance` | Shows or toggles the maintenance banner (saved in `data.yml`) |

`eepytab.admin` grants everything above. All of them default to op.

| Permission | Default | Effect |
|---|---|---|
| `eepytab.layout.staff` | op | Used by the default layout rule to show the staff layout |
| `eepytab.rank.<id>` | **false** | Gives a rank when LuckPerms is not installed (never granted to ops automatically) |

## ⚙️ Configuration

Everything lives in [`config.yml`](src/main/resources/config.yml) and uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) (try it in the [web editor](https://webui.advntr.dev/)). The file is commented section by section:

| Section | What it controls |
|---|---|
| `branding` | Server name, IP, website, Discord, default mode |
| `palette` | Named colours, usable as tags: `<primary>text</primary>` |
| `separator` | The `<separator>` divider |
| `layouts` / `default-layout` / `layout-rules` | Header and footer designs and when each one is shown |
| `animations` | On/off, gradient and pulse speed, rotating texts |
| `player-list` / `ranks` | Entry format, name source, sorting, rank prefixes, colours, priorities, LuckPerms groups |
| `placeholders` | Time zone, time/date format, greetings, ping and TPS thresholds, load bar |
| `update-intervals` / `performance` | How often things refresh, spreading, skipping unchanged packets |
| `vanish`, `worlds`, `maintenance` | Vanish handling, per-world settings, maintenance banner |
| `integrations`, `compatibility`, `messages` | LuckPerms / PlaceholderAPI options, conflict warnings, all command messages |

### Tags

| Tag | Value |
|---|---|
| `<flow:c1:c2:…>text</flow>` | Gradient whose colours slowly loop (palette names or hex) |
| `<pulse:c1:c2>text</pulse>` | Text that softly breathes between two colours |
| `<anim:name>` | Current frame of a rotating text from `animations.texts` |
| `<separator>` | The configured divider |
| `<online>` `<max>` `<load_percent>` `<load_bar>` `<staff_online>` | Player counts (vanish aware) |
| `<tps>` `<tps_color>` `<mspt>` | Server performance |
| `<ping>` `<ping_color>` | The viewer's ping |
| `<player>` `<displayname>` `<world>` `<world_alias>` `<mode>` `<rank>` `<rank_prefix>` | Viewer information |
| `<time>` `<date>` `<greeting>` | Local time in the configured time zone |
| `<server_name>` `<ip>` `<website>` `<discord>` | Branding |
| `%any_placeholder%` | PlaceholderAPI, if installed (otherwise replaced by `missing-value`) |

### PlaceholderAPI placeholders

`%eepytab_rank%`, `%eepytab_rank_id%`, `%eepytab_rank_prefix%`, `%eepytab_rank_suffix%`, `%eepytab_rank_priority%`, `%eepytab_layout%`, `%eepytab_world_alias%`, `%eepytab_mode%`, `%eepytab_maintenance%`

### Ranks

With **LuckPerms**, a player gets the rank whose `luckperms-groups` contains their primary group, otherwise their heaviest inherited group that has a rank. Groups without a rank entry still work: with `prefix-source: AUTO` their LuckPerms prefix and weight are used. Without LuckPerms, the highest-priority rank whose `eepytab.rank.<id>` permission the player has wins; everyone else gets `default-rank`.

## 🧠 Technical notes

- **Paper API only, no NMS.** Header/footer use Adventure, sorting uses `Player#setPlayerListOrder` (1.21.2+), names use `Player#playerListName`. No fake players or filler entries are created.
- **No scoreboard teams.** Sorting through the native list order avoids conflicts with nametag, team and scoreboard plugins.
- **Main thread only for the Bukkit API.** LuckPerms events (async) just mark players dirty; the single EepyTab task applies changes on the next tick. Only `data.yml` writes happen asynchronously.
- **Cheap rendering.** Templates are expanded once per tick; lines without viewer-specific tags are parsed once and shared; players are spread across the interval by UUID; packets are skipped when the content did not change. On the test server, 20 players cost about 0.4 ms per tick in total.
- **Safe text handling.** Player names and PlaceholderAPI / LuckPerms output are inserted as finished components, never re-parsed as MiniMessage, so they can't inject formatting.
- **Seamless gradients.** MiniMessage's gradient phase clamps instead of wrapping, so `<flow>` computes its own looping colour stops (see `AnimationEngine#cyclicStops`).
- **Bandwidth.** While `<flow>` animates, the header is resent every `header-footer` interval (~2.7 KB before compression with the default design). On very busy servers raise the interval or set `animations.enabled: false`; then packets are only sent when values change.
- **Clean shutdown.** The task is cancelled, LuckPerms listeners and the PlaceholderAPI expansion are unregistered and header, footer, names and order are reset.

## 🔨 Building

```bash
./gradlew build
```

The jar is written to `build/libs/EepyTab-<version>.jar`. Gradle downloads a Java 25 toolchain automatically if needed. To build against another Paper version, change `paperApiVersion` and `apiVersion` in `gradle.properties` (or pass `-PpaperApiVersion=…`).

## 📄 License

[MIT](LICENSE)
