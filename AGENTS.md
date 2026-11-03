# Working on Runeloader

These instructions apply to every coding agent (Claude Code, GitHub Copilot, Codex and others) and to human contributors.

## Project

Runeloader is an Android app that installs, updates and removes addons for the [WoW Forever Android client](https://github.com/jaredgei/wow-forever-android). It is plain Java on the Android SDK, with no libraries and no XML layouts: the whole UI is built in code. Read `README.md` for the build. The product name is Runeloader; the package and the build output keep their old names for now.

- Package `app.wowforever.addons`, minSdk 29, targetSdk 34, compileSdk 35.
- Addons go to `<game folder>/_forever_/Interface/AddOns`.

## How the app works

- `MainActivity.java` is the shell: lifecycle, the rail or bottom navigation, tab switching, gamepad keys, permissions, and the shared overlays (modal, confirm, snackbar, progress card). It holds the state that several screens share.
- Each tab is a `Screen` subclass: `LibraryScreen` (installed addons), `BrowseScreen` (one addon search over all sources), `SettingsScreen`. `SetupScreen` shows the storage permission and game folder prompts, `DetailPanel` the addon details, `GameFolder` finds and picks the game folder. `Screen.java` has the helpers they share (headers, rows, empty states).
- `AddonManager.java` scans the AddOns folder and drives installs, updates and removes from the screens. `Updater.java` does the actual install and update without a screen, under one process-wide lock.
- `UpdateJobService.java` is the background auto-update: a periodic `JobScheduler` job that `AutoUpdate.java` schedules from the settings and that posts the result as a notification. `UpdatePolicy.java` holds its plain-Java rules (which addons, which versions count as new).
- `Index.java`, `Sources.java` and `Catalog.java` find addons: GitHub releases with a `forever` flavor in `release.json`, searched through the nightly [runeloader-index](https://github.com/Gulliby-development/runeloader-index) `index.json` or added by link, and WoWInterface. The index is only for search: installs and update checks read the repository's release directly. CurseForge is not a source (owner's decision); do not add it back. `Search.java` merges every source into the one Browse list, without per-source tabs: hand-picked catalog entries the index lacks, then the index, then WoWInterface for addons GitHub does not have (same folder or name). Each row shows its source as a tag. That merge order lives only in `Search.java`; a new source joins it there. Without a query, Browse lists everything by `Popularity` (latest-release downloads plus ten per star); a new popularity signal goes into `Popularity.score` with a case in `test/PopularityTest.java`. The Categories mode of Browse shows a grid of `Categories.KEYS` and then one category's list; a new category needs a key there (matching the index's `category` value), a `cat_<key>` string in every language and a `cat_<key>` icon (a PNG in `res/drawable-nodpi/` or, until one is drawn, a vector in `res/drawable/`).
- `Installer.java` unpacks a zip into the AddOns folder: it finds addon folders by their `.toc`, unpacks to a temporary folder, swaps atomically and refuses zip-slip paths.
- `Toc.java` picks the TOC file the same way the Forever client does: `_Forever`, then `_Camelot`, then `_Mainline`, then the base `.toc`.
- `Store.java` keeps settings and the list of installed addons; there is one instance per process (`Store.get`), shared by the screens and the background job. `Http.java` follows redirects by hand, so the GitHub token goes only to GitHub hosts.

## Task workflow

- Track planned work as GitHub Issues in this repository, not in Markdown files or TODO comments. Before you start, find the issue for the task or open one. Record a new idea, a follow-up or a deferred item as an issue.
  - Issue types: Task, Bug or Feature.
  - Labels: `idea` (not decided yet), `deferred` (agreed for later), `needs-owner` (only the owner can do or decide it), `verification` (needs a real device, a real game folder or an account), and `area:ui|sources|installer|build|ci`.
  - Put `Priority: P0|P1|P2` on the first line of the issue body. P0 blocks current work or a release, P1 is next, P2 is later or an idea.
- Before you open or extend a Bug issue, follow the [file-issue skill](.agents/skills/file-issue/SKILL.md). It finds the root cause and sweeps the code for every other place with the same defect. A pull request that fixes such an issue fixes every place the issue lists and reports the result in a `## Sweep` section of its description.
- Every Feature and Task issue has a `## Maintainability` section. It names the rule a later change must follow when the task adds something that later code must extend (a new source, a new TOC suffix, a new build path). It says `None` when nothing needs extending. The pull request that adds such a mechanism adds its rule to this file.
- Start every task on its own branch from an up-to-date `main`:
  - new feature, documentation, CI or refactor: `feature/<name>`;
  - bug fix: `bugfix/<name>`.

  `<name>` is a few lowercase words joined by hyphens. Do not use other prefixes (`docs/`, `fix/`, `claude/`, `copilot/`) or generic names such as `patch` or `update`. If your tool proposes a default branch, rename it before the first push. A random suffix your platform adds after the name is fine. CI fails a pull request whose branch breaks this rule; only Dependabot's `dependabot/` branches are exempt.
- Commit working states on the task branch. Before each commit, run at least `./gradlew assembleDebug`, so every commit builds.
- A pull request that finishes an issue says `Closes #N` in its description. Use `Refs #N` when the pull request does only part of it.
- Before you finish a task, check `README.md` against your change and update it in the same pull request: features, install steps, FAQ answers, button names, screenshots and build commands must match the app as it is after the merge. Say in the pull request description that you checked it, or what you changed.
- When the task is complete and [Verification](#verification) passes, open a pull request to `main`. `main` takes changes only through pull requests with green CI. Do not push to `main` directly.

## Working conventions

- Keep the app free of third-party libraries. Use the Android SDK and the Java standard library only. A new dependency needs an issue and the owner's decision first.
- Keep the source compatible with Java 8 language level. `build.sh` compiles with `-source 8 -target 8`, and Gradle with Java 17. Code that compiles only in one of them breaks the other build.
- There are two build paths, Gradle (`build.gradle.kts`) and `build.sh`. They must produce the same app. When you change the package, the version, an SDK level, a permission or the signing setup, change both files in the same pull request. CI builds both and compares their package, version, SDK levels and permissions.
- Bump `versionCode` and `versionName` in both build files for every release. Android refuses an install over a newer `versionCode`.
- Never commit a signing key or its passwords. A release build gets the key from the `SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` environment variables (CI secrets, see [Releases](#releases)); without them, both build paths sign with a debug key. Only the release key makes an APK that installs over a published one.
- Build the UI in code through the helpers in `Ui.java` (palette, buttons, pills, icons). Do not add XML layouts.
- Put a new screen in its own `Screen` subclass, and work that changes addons on disk in `AddonManager`. Keep `MainActivity` to the shell and the state that screens share.
- Layout decisions come from `LayoutSpec` (window size in dp): rail or bottom bar, a docked detail panel or a sheet, compact headers on low screens. Screens read `app.layout` instead of comparing width and height themselves. When you change its thresholds, update `test/LayoutSpecTest.java`.
- Landscape is the main layout: most players hold the device sideways, as in the game. Every screen must work in landscape at 4:3, 16:9 and 20:9, and also in portrait, by touch and by gamepad. Text must not be cut mid-word, and buttons must not leave the screen. Check all of them when you change a screen (see [Verification](#verification)).
- Install and update addons only through `Updater`, never with a copy of its logic in a screen or the background job. Any other write to the AddOns folder (remove, ZIP install) runs inside `Updater.LOCK`, so the screens and the background job never write it at the same time. Get the store through `Store.get`, never a second `Store`.
- Do the network and disk work off the main thread. Show progress and errors to the user, never fail silently.
- Treat every downloaded zip and every server reply as untrusted. Keep the zip-slip check, the atomic swap in `Installer.java`, and the host check in `Http.java` that keeps the GitHub token on GitHub hosts.
- A new addon source goes into `Sources.java` with a row in the "Where addons come from" table of `README.md`. It declares which TOC field (`X-WoWI-ID`, `X-Wago-ID`, ...) holds its id.
- The app (`Sources.GitHub.latest`) and the indexer (`runeloader-index`, `indexer/forever.py`) pick the Forever build with the same rules. A change to those rules changes both, with a test case in each. A new `index.json` field is optional for the app; a breaking change raises `schema` there and `Index.SCHEMA` here.
- A new catalog entry goes into `Catalog.java` with the folders the addon installs, so the app can recognize a manual install.
- The GitHub index (runeloader-index) is a hint for search, metadata and update checks. An install always resolves the asset from GitHub directly (`Sources.latest`), because the index can be a day old. If the index is unreachable, the update check falls back to the per-addon check.
- The indexer owns icon conversion (TGA/BLP to PNG); the app only shows a ready PNG and keeps the letter icon until it loads. A new optional index key is mapped in `IndexFields.java`, with a case in `test/IndexFieldsTest.java`; the app must work when the key is missing.
- A new TOC suffix or a change to the TOC order goes into `Toc.java` only, and it must match the order the Forever client uses. Add a case for it to `test/InstallTest.java`.
- Keep comments short and about intent that the code does not show.
- Do not commit build output (`build/`, `*.apk`, `*.idsig`) or `local.properties`.

## Releases

`.github/workflows/release.yml` builds a release from a tag:

1. In a pull request, bump `versionCode` and `versionName` in `build.gradle.kts` and `build.sh`, and merge it.
2. Tag the merge commit on `main` with `v<versionName>` and push the tag (`git tag v0.0.1 && git push origin v0.0.1`), or start the Release workflow on `main` from the Actions tab ("Run workflow"), which tags the commit it built.
3. The workflow checks that the tag matches both build files, builds with Gradle and `build.sh` using the release key, compares the two APKs, refuses a debug signature, and creates a **draft** release with `runeloader-<version>.apk` and its SHA-256.
4. The owner reviews the draft and publishes it. Only the owner publishes a release.

The workflow needs these repository secrets (Settings, Secrets and variables, Actions). It fails when one is missing:

- `SIGNING_KEYSTORE_BASE64`: the release keystore file in base64 (`base64 -w0 release.jks`).
- `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`: the keystore password, the key alias and the key password.

A new secret for the app build (for example an API key) goes into both `ci.yml` and `release.yml`, and into this list.

## Language

- Write code, identifiers, comments, commit messages, issues, pull requests and these instructions in English.
- `README.md` is the project's landing page for players. It is in plain English (about B1 level): short sentences, no jargon in the main part. It keeps this order: the wordmark, a one-line pitch, features, install steps, FAQ. Technical details (sources, install internals, build) go inside `<details>` spoilers. Its images live in `docs/images/` and stay small (under about 400 KB each; use JPEG for large photo-like images).
- The UI is localized through string resources. English in `res/values/strings.xml` is the base and the fallback; translations live in `res/values-<lang>/strings.xml` (today `de`, `es`, `fr`, `pl`, `pt`, `ru`, `zh`). By default the app follows the system language; the user can pick another one in Settings.
  - Put no UI text in Java. Add every new string to `res/values/strings.xml` and to every `values-<lang>` file in the same pull request; lint reports a key that is missing from a language.
  - Count words through `<plurals>` and `getQuantityString`, never by hand: Polish and Russian need `few` and `many` forms.
  - Code outside an activity gets its text through `Lang.s()` and `Lang.q()`. `Installer.java` must also compile on a plain JVM, so its messages go through `Installer.Texts`, with an English default there and the resource in `Lang.INSTALLER`.
  - A new language needs a `res/values-<lang>` folder and an entry in `Lang.TAGS` and `Lang.NAMES`, with the language named in itself.
  - Each tab has one name (`nav_installed`, `nav_browse`, `nav_settings`), used by both the rail and the bottom bar. It must fit the 68dp rail item at 11sp: about nine Latin characters.

## Navigation

- `src/app/wowforever/addons/`: all app code. Besides the files in [How the app works](#how-the-app-works): `Ui.java` (palette, buttons, pills, letter icons, formatting), `ImageLoader.java` (logos with a memory and disk cache), `Model.java` (data classes), `Lang.java` (UI language and localized strings outside an activity).
- `res/`: the adaptive launcher icon and vector icons.
- `stubs/`: lambda bootstrap stubs for `javac` with a trimmed `android.jar` (used only by `build.sh`).
- `test/`: JVM tests for the installer, TOC and network paths.
- `AndroidManifest.xml`, `build.gradle.kts`, `build.sh`: the manifest and the two build paths.
- `.github/workflows/`: CI (`ci.yml`) and releases (`release.yml`).
- `scripts/`: helper scripts for CI (`apk-summary.sh`) and verification (`screenshots.sh`, every tab at each screen ratio).

## Verification

CI runs these on every pull request to `main`. Run them before you open one:

```sh
./gradlew assembleDebug assembleRelease lint
ANDROID_HOME=<sdk> ./build.sh
```

When you change `LayoutSpec.java`, run its test:

```sh
mkdir -p /tmp/l && javac -d /tmp/l src/app/wowforever/addons/LayoutSpec.java test/LayoutSpecTest.java
java -cp /tmp/l app.wowforever.addons.LayoutSpecTest
```

When you change `Installer.java`, `Toc.java` or `Model.java`, also run the installer test on real addon zips:

```sh
mkdir -p /tmp/t && javac -d /tmp/t src/app/wowforever/addons/{Installer,Toc,Model}.java test/InstallTest.java
java -cp /tmp/t app.wowforever.addons.InstallTest <folder-with-zips> /tmp/fakegame
```

When you change `UpdatePolicy.java`, run its test (CI runs it too):

```sh
mkdir -p /tmp/u && javac -d /tmp/u src/app/wowforever/addons/{Model,UpdatePolicy}.java test/UpdatePolicyTest.java
java -cp /tmp/u app.wowforever.addons.UpdatePolicyTest
```

When you change `Popularity.java` (the default Browse order), run its test (CI runs it too):

```sh
mkdir -p /tmp/p && javac -d /tmp/p src/app/wowforever/addons/{Model,Popularity}.java test/PopularityTest.java
java -cp /tmp/p app.wowforever.addons.PopularityTest
```

When you change `Markdown.java` (release notes in the detail panel), run its test (CI runs it too):

```sh
mkdir -p /tmp/m && javac -d /tmp/m src/app/wowforever/addons/Markdown.java test/MarkdownTest.java
java -cp /tmp/m app.wowforever.addons.MarkdownTest
```

To run the background job on a device now: `adb shell cmd jobscheduler run -f app.wowforever.addons 1901`.

For a UI change, install the debug APK on an emulator or a device (`adb install -r build/outputs/apk/debug/wow-addons-debug.apk`) and click through the changed screens. Check landscape first, then portrait. On an emulator, these commands give each aspect ratio without a new virtual device:

```sh
adb shell cmd window user-rotation lock 1   # landscape
adb shell wm size 1080x1440                 # 4:3
adb shell wm size 1080x1920                 # 16:9
adb shell wm size 1080x2400                 # 20:9
adb shell wm size reset                     # the device's own ratio
adb shell cmd window user-rotation free     # rotation back to normal
```

`scripts/screenshots.sh` does all of this for you: it sets each ratio (4:3, 16:9 and 20:9 landscape, then portrait), captures every tab into `screenshots/<preset>-<tab>.png` and restores the size and rotation. When you add a tab, add it to the script's `TABS` list.

```sh
scripts/screenshots.sh --apk build/outputs/apk/debug/wow-addons-debug.apk
```

Attach screenshots of the changed screens at 4:3 and 16:9 landscape to the pull request. Say what you checked and on which Android version. Report any check that failed or that you could not run.
