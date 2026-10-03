# TowerDuel

[![Release](../../actions/workflows/release.yml/badge.svg)](../../actions/workflows/release.yml)

A tower-defense duel for Android. You and an AI opponent each defend a lane
with a random hand of towers while sending units down the other side's lane.
Whoever runs out of lives first loses.

Native Kotlin + Jetpack Compose. No game engine, no external art, no network
access, no ads.

**[Download the latest APK](../../releases/latest)**

## Contents

- [Features](#features)
- [Install](#install)
- [How a match plays](#how-a-match-plays)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Extending the game](#extending-the-game)
- [Releases and versioning](#releases-and-versioning)
- [Release signing](#release-signing)
- [Contributing](#contributing)

## Features

- **Random draft every match.** You and the AI are each dealt 3 towers from a
  pool of 12, independently, so neither side knows what the other holds.
- **12 towers** with distinct roles: Sentry, Sniper, Frost Spire, Bomb Tower,
  Gatling, Chain Lightning, Poison Totem, Gold Mine, Stun Turret, Anti-Air
  Net, Support Beacon, Mortar. Splash, chaining, slows, stuns, damage over
  time, damage auras and pure economy are all covered.
- **7 sendable units** shared by both sides: Runner, Grunt, Tank, Swarm Pack,
  Flyer, Healer, Boss.
- **3 maps** (S-Curve, Zigzag, Diagonal Sweep) with different chokepoints.
- **6 match modifiers**, one rolled per match: Rush Hour, Gold Rush, Glass
  Cannons, Fortified, Blitz, Iron Lives.
- **An AI with a personality.** On top of the difficulty you pick (Easy,
  Medium, Hard), the AI rolls a Rusher, Turtle or Balanced play style.
- **Short matches.** 4 minutes by default, 2 under the Blitz modifier.

Towers, map, modifier and AI personality are rolled independently, so two
matches rarely play the same way.

## Install

1. Open the [latest release](../../releases/latest) on your Android phone and
   download `TowerDuel-vX.Y.apk`.
2. Open the file. Android will ask you to allow installs from the app you
   downloaded it with (browser or file manager). Allow it, then tap
   **Install**.

Requires Android 8.0 (API 26) or newer. Every release is signed with the same
key, so a newer APK installs over the old one and keeps it updated in place.

## How a match plays

Each side starts with 120 gold and 100 lives and earns gold passively.

- **BUILD row.** Tap one of your 3 drafted towers to arm it, then tap your
  lane (the bottom one) to place it. A lane holds up to 8 towers, and towers
  cannot overlap.
- **Tap a placed tower** to see its range and stats, upgrade it (once per
  tower) or sell it for half of what you spent on it.
- **SEND row.** Spend gold to send units down the opponent's lane (the top
  one). Every unit that reaches a base costs that side lives, and every unit
  you kill pays you a bounty.
- **PAUSE**, or the system back button, stops the match. Leaving the app
  pauses it automatically.
- The first side to reach 0 lives loses. If the clock runs out, the side with
  more lives wins.

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
│   ├── GameEngine.kt        The simulation: income, targeting, attacks, status effects, win condition
│   ├── AiController.kt      The opponent's decision making
│   ├── PathMath.kt          Path geometry helpers
│   └── RuntimeModels.kt     Live match state (towers, units, battlefields)
└── ui/
    ├── GameViewModel.kt     Runs the game loop and exposes state to the UI
    ├── Navigation.kt        Screen routing
    ├── screens/             MainMenu > Draft > Battle > Results
    └── theme/               Colours, typography, Material theme
```

The `engine` package has no Android or Compose dependencies. The view model
ticks it about 60 times per second and the UI draws whatever state it finds.
Lanes are simulated in a fixed 100 x 46 virtual coordinate space, so the game
plays identically on every screen size.

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
Add an entry to one of these lists and the draft and roll logic picks it up
with no other changes:

| List | Adds |
| --- | --- |
| `TROOPS` | A tower to the draft pool |
| `ENEMY_SENDS` | A sendable unit |
| `MAPS` | A map, defined by its path points in the 100 x 46 lane space |
| `MODIFIERS` | A match modifier |

Global balance values (starting gold, lives, income, match length, towers per
lane) are constants at the top of the same file.

To add an AI personality, add a value to the `AiPersonality` enum in
`GameModels.kt` and give it a weighting branch in `AiController.kt`.

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
4. Run the game on a device or emulator and play at least one full match
   with your change.
5. Open a pull request that describes what changed and why. For balance
   changes, say how you tested them.

Bug reports and ideas are welcome in the issue tracker.
