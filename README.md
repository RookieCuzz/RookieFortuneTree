# RookieFortuneTree

RookieFortuneTree is an example gameplay plugin built on top of BukkitSpring.
It provides a "fortune tree" loop with menu interaction, economy integration,
persistence, and starter-based infrastructure.

## Features

- Menu-based gameplay:
  - Water the tree
  - Collect one bubble
  - Collect all bubbles
  - Daily reset cycle
- Command entry points:
  - `/wt`
  - `/wt gui`
  - `/wt water [level]`
  - `/wt collect`
  - `/wt reload`
  - `/wt debug`
  - `/water`
- Economy integration:
  - `playerpoints` mode
  - `command` mode
  - `vault` currently falls back to `command` (warning log)
- Data layer:
  - Redis first, MyBatis fallback
  - Redis keys for player state, attempt state, and collect bitmaps
- JVM caching:
  - Caffeine `expire-after-access-ms = 300000` (5 minutes)
  - Per-player cache eviction on `PlayerQuitEvent` and `PlayerKickEvent`
- Time handling:
  - Uses `TimeService.now()` and `TimeService.settings().zoneId` when available
  - Falls back to `Clock.systemDefaultZone()`

## Project Layout

```text
examples/RookieFortuneTree
|-- src/main/java/com/cuzz/rookiefortunetree
|   |-- bootstrap/     # startup wiring and schema initialization
|   |-- command/       # command wrapper and tab completion
|   |-- controller/    # event handlers
|   |-- menu/          # OdalitaMenus GUI
|   |-- repository/    # MyBatis repositories
|   |-- service/       # gameplay and clock services
|   `-- storage/       # persistent store (Redis + MyBatis + JVM cache)
`-- src/main/resources
    |-- config.yml
    |-- menu_icons.yml
    |-- plugin.yml
    `-- mappers/*.xml
```

## Runtime Requirements

- Java 17
- Paper 1.20.4+
- Required plugins:
  - `BukkitSpring`
  - `OdalitaMenus`
- Recommended plugin:
  - `PlayerPoints` (when `economy.type=playerpoints`)
- Recommended starters in `plugins/BukkitSpring/starters/`:
  - `bukkitspring-starter-redis`
  - `bukkitspring-starter-mybatis`
  - `bukkitspring-starter-caffeine`
  - `bukkitspring-starter-time`

## Build

```bash
mvn -f examples/RookieFortuneTree/pom.xml clean package
```

Output:

```text
examples/RookieFortuneTree/target/RookieFortuneTree-1.0.0.jar
```

## Deploy

1. Copy plugin jar:

```text
examples/RookieFortuneTree/target/RookieFortuneTree-1.0.0.jar
-> run/paper/plugins/
```

2. Ensure starter jars exist:

```text
run/paper/plugins/BukkitSpring/starters/
```

3. Start or restart Paper.

## Configuration

### Plugin config

File:

```text
plugins/RookieFortuneTree/config.yml
```

Important sections:

- `daily.*`: daily limit and reset time (`HH:mm`)
- `newbie.*`: first pick cost and free picks after first
- `reroll.*`: reroll switch and max rerolls per attempt
- `levels[]`: per-level deposit, reward max, bubble bounds, crit params
- `economy.*`: economy mode and command templates
- `menu.*`: title, sounds, and slots
- `messages.*`: gameplay messages

### BukkitSpring config

File:

```text
plugins/BukkitSpring/config.yml
```

At minimum, verify:

- `redis.enabled=true`
- `mybatis.enabled=true`
- `caffeine.enabled=true`
- `time.enabled=true`

## Redis and Database

Redis keys:

- `ft:player:{uuid}`
- `ft:attempt:{uuid}:{cycleId}`
- `ft:collect:{uuid}:{cycleId}`

TTL:

- Attempt and collect keys use 30 days TTL.

MyBatis tables (auto-create):

- `fortune_tree_player`
- `fortune_tree_attempt`

## Permissions

- `dailywatertree.gui`
- `dailywatertree.use`
- `dailywatertree.admin`

Defaults are defined in `plugin.yml`.

## Tests

Run module tests:

```bash
mvn -f examples/RookieFortuneTree/pom.xml test
```

Covered areas:

- `ResetClockService` (cycle calculation and time-starter path)
- `RewardGenerator` (reward constraints)
- `PersistentFortuneTreeStore` (Redis-first, MyBatis fallback, cache reload after evict)

## Known Limitations

- `economy.type=vault` is not implemented in this example and falls back to `command`.
- If localized text looks corrupted, make sure `config.yml` and `menu_icons.yml` are saved as UTF-8.
