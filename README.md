# TowerDuel

[![Release](../../actions/workflows/release.yml/badge.svg)](../../actions/workflows/release.yml)

A tower-defense duel for Android. You and an AI opponent each defend a lane
with a drafted hand of towers, survive the same escalating waves, and send
units down the other side's lane. Whoever runs out of lives first loses.

Native Kotlin + Jetpack Compose. No game engine, no network access, no ads.
Every sprite is drawn in code and every sound is synthesized at startup. The
only bundled assets are two open-licensed fonts.

**[Download the latest APK](../../releases/latest)**

## Contents

- [Features](#features)
- [Install](#install)
- [How a match plays](#how-a-match-plays)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Extending the game](#extending-the-game)
- [Tests](#tests)
- [Releases and versioning](#releases-and-versioning)
- [Release signing](#release-signing)
- [Contributing](#contributing)
- [Credits](#credits)

## Features

- **Draft your defense.** Each match offers you 5 of the 20 towers and you
  keep 3. The AI drafts from its own offer, so neither side knows what the
  other holds until the towers go down.
- **20 towers**, each with a two-step upgrade track. Besides plain guns
  there is splash (Bomb Tower, Mortar), chain lightning (Tesla Coil), slows
  (Frost Spire), stuns (Stun Turret), poison and fire (Poison Totem, Flame
  Tower), a beam that ramps up on one target (Prism), a blade that cuts
  through a whole line (Glaive Thrower), crits (Crossbow), a curse that makes
  everything else hit harder (Hex Totem), knockback (Gust Fan), executions
  (Reaper), extra bounties (Bounty Hunter), anti-air (Flak Net), a damage
  aura (Beacon) and pure economy (Gold Mine).
- **14 units, 8 per match.** Every match rolls its own roster: Runner and
  Grunt, one finisher (Boss or Juggernaut) and five of Swarm, Drummer, Flyer,
  Tank, Phantom, Healer, Bulwark, Splitter, Troll and Brood Mother. Both
  sides send from that roster, and the waves are built from it. Armour,
  regeneration, phasing out of reach, haste auras and immunity to slows all
  ask for different towers.
- **Waves that are never the same twice.** Each round's wave is generated
  for the match: sometimes a mixed bag, sometimes a themed one (Rush, Swarm,
  Air Raid, Heavy Armour, Boss Round). The same wave hits both lanes, each
  tougher than the last. After the final round comes sudden death, where
  waves grow faster and tougher until one side breaks.
- **Maps without end.** Six named maps in six looks (meadow, dunes, snow,
  lava, swamp, autumn), each also played mirrored, and half of all matches
  on a freshly generated track that is checked for fairness before you see
  it.
- **13 rules**, one rolled per match and sometimes two at once: Rush Hour,
  Gold Rush, Glass Cannons, Fortified, Blitz, Iron Lives, Bounty Boom, Thick
  Skin, War Economy, Quick March, Rapid Fire, Marathon, Mirror Match.
- **Random events.** A few times a match something happens to both lanes
  at once: Gold Rain, an Ambush wave, Payday, a Stampede, a Power Surge,
  Overdrive, Fog or a Cold Snap.
- **12 named rivals, 8 play styles.** On top of the difficulty you pick
  (Easy, Medium, Hard) you draw a rival: a Rusher, Turtle, Balanced, Tycoon,
  Swarmer, Bruiser, Gambler or Trickster, with something to say about how
  the match is going. The AI plays the same game you do: it places towers
  by how much track they cover, saves up for what it wants, answers what you
  send, and on Hard sizes its pushes to what your defense can absorb.
- **A live main menu.** The match on the menu is real: two AIs playing the
  same engine you are about to.
- **Short matches.** About 4 minutes; 3 under Blitz, 5 under Marathon.
- **A stats tab.** Win rate, record per difficulty, lifetime totals, personal
  bests, your most picked towers and which rivals you have beaten, all kept
  on the device.
- **Stats backup.** Export your stats to a file and import them again, for
  example on a new phone.

Towers offered, units, map, rules, rival, waves and events are all rolled
independently, so no two matches play the same way.

## Install

1. Open the [latest release](../../releases/latest) on your Android phone and
   download `TowerDuel-vX.Y.apk`.
2. Open the file. Android will ask you to allow installs from the app you
   downloaded it with (browser or file manager). Allow it, then tap
   **Install**.

Requires Android 8.0 (API 26) or newer. Every release is signed with the same
key, so a newer APK installs over the old one and keeps it updated in place.

## How a match plays

Each side starts with 130 gold and 100 lives and earns gold every second.

- **Draft.** Tap 3 of the 5 towers you are offered, then **Battle!**. At
  least one of the three has to be a real damage dealer.
- **Build.** Tap one of your towers in the bottom panel, then touch your
  lane (the lower one). Keep your finger down to see the tower's range and
  drag it into place; lift to build. Towers cannot stand on the track or on
  each other, and a lane holds up to 10.
- **Tap a placed tower** to see its range and stats, upgrade it (twice),
  change which unit it shoots first, or sell it for 70% of what you spent.
- **Send.** The two rows of unit buttons are this match's roster; the
  draft screen shows it before you pick towers. Sending a unit puts it on
  the rival's lane (the upper one), as tough as the current round's wave.
  Most sends also raise your income for good, so cheap sends early pay for
  big pushes later.
- **Survive.** A wave walks both lanes every round. Every unit that reaches
  a keep costs that side lives, and every unit you pop pays a bounty.
- **Events** are announced on your lane when they strike, with a countdown
  while they last. They always hit both sides.
- **Pause**, or the system back button, stops the match. Leaving the app
  pauses it automatically. The button on the right of the top bar doubles
  the speed.
- The first side to reach 0 lives loses. After the last round (round 12,
  unless a rule changes the match length) it is sudden death: a wave every
  10 seconds, each one much tougher and faster than the one before.

### Backing up your stats

Open the **Stats** tab on the main menu and scroll to **Backup**.

- **Export** opens Android's save dialog in the Download folder with the
  file name already filled in (`towerduel-stats-<date>.json`). Tap **Save**,
  or browse to another folder first.
- **Import** lets you pick an exported file, shows what is in it, and asks
  before it replaces the stats on the device.

The file is plain JSON. The game needs no storage permission for either
direction: it only ever touches the one file you pick.

## Building from source

### Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | **17** | Required. Gradle 8.4 does not run on JDK 21 or newer. |
| Android SDK | Platform 34, Build-Tools 34 | Installed by Android Studio's SDK Manager. |
| Android Studio | Hedgehog (2023.1) or newer | Optional. The command line works on its own. |
| Git | any recent version | |

Gradle itself does not need installing. The wrapper (`gradlew`) downloads
Gradle 8.4 on first use.

### Setup with Android Studio

1. Clone the repository:

   ```bash
   git clone https://github.com/<owner>/TowerDuel.git
   ```

2. In Android Studio choose **File > Open** and select the `TowerDuel` folder
   (the one that contains `settings.gradle.kts`).
3. If the first Gradle sync fails with an "unsupported class file" or
   "incompatible Java" error, Android Studio is using a JDK newer than 17.
   Go to **Settings > Build, Execution, Deployment > Build Tools > Gradle**,
   set **Gradle JDK** to a JDK 17 (the dropdown can download one), and sync
   again.
4. Plug in a phone with USB debugging enabled, or create a virtual device in
   **Device Manager**, then press **Run**.

Android Studio writes `local.properties` (the path to your SDK) for you. That
file is machine-specific and is not committed.

### Setup from the command line

1. Clone the repository and enter it.
2. Point `JAVA_HOME` at a JDK 17.
3. Tell Gradle where the Android SDK is. Either set the `ANDROID_HOME`
   environment variable, or create `local.properties` in the project root:

   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```

   On macOS and Linux this is a plain path such as
   `sdk.dir=/home/<you>/Android/Sdk`.
4. Build and install the debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   On Windows use `gradlew.bat` in place of `./gradlew`.

Debug builds need no signing setup. `./gradlew :app:assembleRelease` also
works without one, and then produces `app-release-unsigned.apk`.

## Project structure

```
app/src/main/java/com/towerduel/game/
├── MainActivity.kt          Entry point, hosts the Compose navigation graph
├── data/
│   ├── GameData.kt          All game content and balance numbers
│   └── GameModels.kt        Data classes and enums for that content
├── engine/
│   ├── GameEngine.kt        The simulation: rounds, events, income, targeting, projectiles, status effects, win condition
│   ├── AiController.kt      The opponent's decision making (and its draft)
│   ├── WaveGenerator.kt     Builds each round's wave from the match's roster
│   ├── MapGenerator.kt      Makes new tracks and checks them for fairness
│   ├── PathMath.kt          The lane track: a smooth curve through a map's control points
│   └── RuntimeModels.kt     Live match state (towers, units, projectiles, effects, battlefields)
└── ui/
    ├── GameViewModel.kt     Steps the engine once per display frame, exposes state to the UI
    ├── DemoMatch.kt         The AI-vs-AI match shown on the main menu
    ├── Stats.kt             Lifetime stats and how a finished match adds to them
    ├── StatsFile.kt         The export file: stats to JSON and back
    ├── Profile.kt           Saves those stats and the settings (SharedPreferences)
    ├── Navigation.kt        Screen routing
    ├── audio/SoundFx.kt     Synthesizes every sound effect at startup
    ├── components/          Buttons, panels, outlined text and the icon set
    ├── render/              Everything drawn on a lane: terrain, sprites, effects
    ├── screens/             MainMenu (Battle and Stats tabs) > Draft > Battle > Results
    └── theme/               Colours, typography, Material theme
```

The `engine` package has no Android or Compose dependencies. The view model
advances it in fixed 1/60 s steps, driven by the display's frame clock, and
the UI draws whatever state it finds. Lanes are simulated in a fixed 100 x 62
virtual coordinate space, so the game plays identically on every screen size.

There are no image or audio files. Sprites are drawn from a handful of
shapes in `ui/render/Sprites.kt`, the same code for the battlefield and for
the portraits in the UI. Each map's ground and track are painted once into a
bitmap and reused every frame.

| Component | Version |
| --- | --- |
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.4 |
| Compose BOM | 2024.02.01 (Material 3) |
| min / target / compile SDK | 26 / 34 / 34 |

## Extending the game

All content lives in
[`GameData.kt`](app/src/main/java/com/towerduel/game/data/GameData.kt).
Add an entry to one of these lists and the draft, roll and AI logic pick it
up with no other changes:

| List | Adds |
| --- | --- |
| `TROOPS` | A tower to the draft pool, with its upgrade tiers |
| `ENEMY_SENDS` | A unit. Unless marked `sendable = false` it joins the pool that match rosters are drawn from |
| `MAPS` | A named map: a theme and the track's control points in the 100 x 62 lane space |
| `MODIFIERS` | A rule |
| `RIVALS` | A named opponent: a play style, a portrait unit and its lines |

A new tower or unit is drawn as a plain turret or a plain blob in its own
colour until you give it a branch in
[`Sprites.kt`](app/src/main/java/com/towerduel/game/ui/render/Sprites.kt).

What a tower or unit can *do* is a set of fields on `TroopType` and
`EnemySendType` (splash, pierce, crit, curse, armour, regeneration and so
on), all applied in one place: `GameEngine.hit`. A new ability is a new
field there.

Global balance values (starting gold, lives, income, round timing, towers
per lane, how often events strike) are constants at the top of `GameData.kt`.

To add a random event, add a value to `MatchEventType` in `GameModels.kt` and
give it its effect in `GameEngine.kt`. To add an AI play style, add a value
to the `AiPersonality` enum and a row to the `Style` table at the top of
`AiController.kt`.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`BalanceSimulationTest` plays about 480 whole matches headless, AI against
AI, each on its own random map, roster and draft, in under a minute. It
fails if any match does not end, if a harder AI does not beat an easier one
most of the time, or if a play style can never win. It also prints a table
per matchup (wins, match length, how many matches ended before sudden death,
lives left), which is the tool to use when changing numbers in `GameData.kt`.

`GeneratorsTest` keeps the random parts fair: every named map and its mirror
passes the same checks a generated map must, the map generator almost never
gives up, rosters always hold the basics and one finisher, waves use only
units that are unlocked and stay near their health budget, and two rules
rolled together never contradict each other.

`LifetimeStatsTest` covers how a finished match is added to the stats tab's
numbers: streaks, the per-difficulty record, totals and bests.
`StatsFileTest` covers the export file: an export reads back unchanged, a
file that is not an export is refused, and impossible numbers are repaired.

## Releases and versioning

Every push to `main` runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which:

1. Works out the next version: the highest existing `v<major>.<minor>` tag
   plus one minor (`v1.0`, `v1.1`, `v1.2`, ...). The first release is `v1.0`.
2. Builds a release APK with that version baked in (`versionName` = `1.2`,
   `versionCode` = `major * 10000 + minor`).
3. Signs it with the release key.
4. Publishes a GitHub release named `TowerDuel v1.2` with
   `TowerDuel-v1.2.apk` attached and auto-generated notes.

Things worth knowing:

- **Releases are never overwritten.** The version comes from the existing
  tags, runs are queued one at a time, and publishing fails rather than
  replacing a release that already exists.
- **The workflow does not commit to the repository.** The version lives in
  the git tags, so there is no bot commit to pull after each push.
- **Starting a new major series.** Change `VERSION_MAJOR` in
  `release.yml`. The next push releases `v2.0`.
- **Skipping a release.** Put `[skip ci]` in the commit message.
- **Local builds** are marked `versionName = "dev"`. Pass
  `-PappVersionName=1.2 -PappVersionCode=10002` to override.

## Release signing

Contributors can skip this section. Debug builds use the standard Android
debug key, and pull requests never need the release key.

Android only installs an update over an existing app when both are signed
with the same key. The release key is therefore kept for the life of the
project and is deliberately **not** in the repository.

### Files

| File | Contents | Committed |
| --- | --- | --- |
| `towerduel-release.jks` | The keystore (PKCS12, alias `towerduel`) | No |
| `keystore.properties` | Keystore path, alias and password | No |

Both live in the project root and are listed in `.gitignore`.
`keystore.properties` looks like this:

```properties
storeFile=towerduel-release.jks
storePassword=<password>
keyAlias=towerduel
keyPassword=<password>
```

When both files are present, `./gradlew :app:assembleRelease` produces a
signed `app/build/outputs/apk/release/app-release.apk`.

Official releases are signed with the certificate whose SHA-256 fingerprint
is:

```
9D:7E:17:7A:31:80:37:C7:25:7A:91:8C:B0:78:BB:6F:45:C6:76:23:4D:15:EA:71:13:FB:A1:6F:01:7B:86:F0
```

### Using the key on another machine

1. Copy `towerduel-release.jks` and `keystore.properties` from the old
   machine (or from your backup) into the root of the new clone.
2. Check that it is the right key. The SHA-256 line must match the
   fingerprint above:

   ```bash
   keytool -list -v -keystore towerduel-release.jks -alias towerduel
   ```

3. Build with `./gradlew :app:assembleRelease`.

Keep a copy of both files somewhere outside the project folder, for example
in a password manager or an encrypted drive. If the keystore or its password
is lost, no new build can update an installed copy of the app. Players would
have to uninstall and reinstall.

### GitHub Actions secrets

The release workflow reads the key from two repository secrets, set under
**Settings > Secrets and variables > Actions > New repository secret**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |

To copy the base64 text to the clipboard:

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("towerduel-release.jks")) | Set-Clipboard
```

```bash
# macOS
base64 -i towerduel-release.jks | pbcopy
# Linux
base64 -w 0 towerduel-release.jks
```

Until both secrets exist, the workflow stops with an error and publishes
nothing.

### Forks

A fork cannot use the original key. Generate your own, then create
`keystore.properties` and the two secrets as described above:

```bash
keytool -genkeypair -keystore towerduel-release.jks -storetype PKCS12 \
  -alias towerduel -keyalg RSA -keysize 2048 -validity 10000
```

APKs signed with a different key cannot update an install of the official
app. The official one has to be uninstalled first.

## Contributing

1. Fork the repository and create a branch from `main`.
2. Follow [Building from source](#building-from-source) to get a debug build
   running.
3. Keep simulation logic in `engine/` free of Android and Compose imports,
   and keep content and balance numbers in `GameData.kt`.
4. Run `./gradlew :app:testDebugUnitTest`. For balance changes, include the
   simulation table before and after.
5. Run the game on a device or emulator and play at least one full match
   with your change.
6. Open a pull request that describes what changed and why.

Bug reports and ideas are welcome in the issue tracker.

## Credits

The game bundles two fonts, each under its own open licence. The licence
texts ship inside the APK, in `app/src/main/assets/licenses/`.

| Font | Used for | Licence |
| --- | --- | --- |
| [Luckiest Guy](https://fonts.google.com/specimen/Luckiest+Guy) by Astigmatic | Titles, buttons, numbers | Apache License 2.0 |
| [Fredoka](https://fonts.google.com/specimen/Fredoka) by the Fredoka Project Authors | Body text | SIL Open Font License 1.1 |
