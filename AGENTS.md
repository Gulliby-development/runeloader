# Working on Runeloader

These instructions apply to every coding agent (Claude Code, GitHub Copilot, Codex and others) and to human contributors.

## Project

Runeloader is an Android app that installs, updates and removes addons for WoW Forever. It works with any client: it only writes to the AddOns folder the user picks, so do not tie it to one client app, package or folder layout. It is plain Java on the Android SDK, with no libraries and no XML layouts: the whole UI is built in code. Read `README.md` for the build. The product name is Runeloader; the package and the build output keep their old names for now.

- Package `app.wowforever.addons`, minSdk 29, targetSdk 34, compileSdk 35.
- Addons go to the AddOns folder that `AddonsFolder.java` finds in the chosen folder: `<game>/_<flavor>_/Interface/AddOns` (`_forever_` first), `<game>/Interface/AddOns`, a picked `Interface` or `AddOns` folder, or any plain folder the user confirms. A new folder layout goes into `AddonsFolder.java` with a case in `core/test/AddonsFolderTest.java`.

## How the app works

- `MainActivity.java` is the shell: lifecycle, the rail or bottom navigation, tab switching, gamepad keys, permissions, and the shared overlays (modal, confirm, snackbar, progress card). It holds the state that several screens share.
- Each tab is a `Screen` subclass: `LibraryScreen` (installed addons), `BrowseScreen` (one addon search over all sources), `SettingsScreen`. `SetupScreen` shows the storage permission and game folder prompts, `DetailPanel` the addon details, `GameFolder` finds and picks the game folder. `Screen.java` has the helpers they share (headers, rows, empty states).
- `AddonManager.java` scans the AddOns folder and drives installs, updates and removes from the screens. `Updater.java` does the actual install and update without a screen, under one process-wide lock.
- `UpdateJobService.java` is the background auto-update: a periodic `JobScheduler` job that `AutoUpdate.java` schedules from the settings and that posts the result as a notification. `UpdatePolicy.java` holds its plain-Java rules (which addons, which versions count as new).
- `Index.java`, `Sources.java` and `Catalog.java` find addons: GitHub releases with a `forever` flavor in `release.json`, searched through the nightly [runeloader-index](https://github.com/Gulliby-development/runeloader-index) `index.json` or added by link, and WoWInterface. The index is only for search: installs and update checks read the repository's release directly. CurseForge is not a source (owner's decision); do not add it back. `Search.java` merges every source into the one Browse list, without per-source tabs: hand-picked catalog entries the index lacks, then the index, then WoWInterface for addons GitHub does not have (same folder or name). Each row shows its source as a tag. That merge order lives only in `Search.java`; a new source joins it there. Without a query, Browse lists everything by `Popularity` (latest-release downloads plus ten per star); a new popularity signal goes into `Popularity.score` with a case in `core/test/PopularityTest.java`. The Categories mode of Browse shows a grid of `Categories.KEYS` and then one category's list; a new category needs a key there (matching the index's `category` value), a `cat_<key>` string in every language and a `cat_<key>` icon (a PNG in `res/drawable-nodpi/` or, until one is drawn, a vector in `res/drawable/`, mapped in `CategoryIcons`). `Categories.TAGS` are tiles that select by the index's `tags` list instead of `category` (today `gamepad`, which also shows as a tag on the row); a new tag gets a key there, its strings and icon, a case in `core/test/IndexFieldsTest.java`, and its rule in runeloader-index.
- `Installer.java` unpacks a zip into the AddOns folder: it finds addon folders by their `.toc`, unpacks to a temporary folder, swaps atomically and refuses zip-slip paths.
- `Toc.java` picks the TOC file the same way the game does: `_Forever`, then `_Camelot`, then `_Mainline`, then the base `.toc`.
- `SelfUpdate.java` updates Runeloader itself: on start (at most every 12 hours) and from Settings it reads this repository's latest GitHub release, offers the newer version, downloads `runeloader-<version>.apk`, checks it against the `.apk.sha256` file, its package name and a higher `versionCode`, and installs it through `PackageInstaller`. The asset names come from `release.yml` and `AppVersion.apkName`; a change to one changes the other. Version comparison lives in `AppVersion.java` with a case in `core/test/AppVersionTest.java`.
- `Store.java` keeps settings and the list of installed addons; there is one instance per process (`Store.get`), shared by the screens and the background job. The Android app sends no GitHub token (there is no token setting there). The desktop app has an optional personal token in Settings (`GithubToken`, its own owner-only file); core sends it through `Http.githubToken` to `api.github.com` only, and `Http.rateLimitText` tells when a refused call's limit resets. Keep API calls rare anyway, since the anonymous limit is 60 per hour. `Http.java` follows redirects by hand and drops an `Authorization` header on any host but GitHub.

## Shared core

Runeloader is coming to Windows, Linux and macOS (#95) as a Compose Desktop app in `desktop/`, in this repository. Code that both apps need lives once, in `core/src/app/wowforever/addons/` (same package as the app):

- `core` is plain Java with the Java 8 API: no `android.*`, no `org.json`, no `R`, no libraries. `core/test.sh` compiles it alone with `javac --release 8` and fails on those references.
- JSON in core goes through `Json.java`: `Json.Obj` and `Json.Arr` keep the org.json method names and conversions, `Json.Reader` is the pull reader for large files. Android code uses it too, so data moves between both apps without a second parser.
- Text in core comes from `Texts.s("<string name>", args)`, the name of a string in `res/values/strings.xml`. Android resolves it through `Lang.CORE`; the desktop app reads the same files. A new core text is a new string in every language, as for Android.
- Today `core` has the installer, TOC, modules and packages, versions, update policy, popularity, categories, the index, the sources (GitHub, WoWInterface, catalog), the search, `Updater` and `Store`.
- What core needs from the app it runs in goes through `Platform` (app folders, `Platform.Prefs` for settings, the folders to look for a game in). Android implements it in `AndroidPlatform`; a new need is a new `Platform` method with an Android and a desktop implementation.
- Both Android build paths compile `core/src` with `src` (`build.gradle.kts` and `build.sh`).
- Logic both apps need goes into `core`, never into a copy in `src/` or `desktop/`. A class that moves there takes its test to `core/test/<Name>Test.java` (a class with a `main` that exits non-zero on failure); `core/test.sh` runs every one, so CI needs no new step.
- Desktop releases use tags `desktop-v<version>` and are created as not the latest release, so `SelfUpdate` on Android keeps reading only Android releases.

## Desktop app

- `desktop/` is its own Gradle build (Kotlin, Compose Desktop, JDK 21): `./gradlew -p desktop run`, `build`, `packageMsi`. It compiles `../core/src` into the app; the Android build never loads Kotlin or Compose. See `desktop/README.md`.
- Its Kotlin code uses the package `app.wowforever.addons`, so it reaches the package-private core classes. A core type that a Kotlin declaration shows in its signature (`Store`, `Platform`, `Model.Entry`, `Model.Item`) is declared `public`, because Kotlin refuses to expose a package-private Java type; its members can stay package-private. It holds the UI and the desktop side of `Platform` only (`DesktopPlatform`: app folders, `settings.json`, the drives and folders to look for a game in).
- Texts and icons are the Android resources: the build copies `res/values*/strings.xml` and `res/drawable/*.xml` into the jar, `DesktopTexts` reads the strings (same Android escaping, English fallback) and `drawable(name)` the vector icons. Put no UI text in Kotlin: a desktop-only text is a new string in every `values-<lang>` file, as for Android. A new language is a new entry in `DesktopTexts.LANGUAGES` too.
- The colours are `Palette` in `Theme.kt`, the same values as `Ui.java`; a brand colour changes in both.
- The look follows the design (glass panels, `Label` heads in JetBrains Mono, titles in Manrope, text in Inter). The fonts are OFL files in `desktop/src/main/resources/fonts/` and come only through `Fonts` in `Theme.kt`; shared pieces (`Button`, `Tag`, `Pill`, `Segmented`, `Field`, `Switch`, `glass`) live in `Ui.kt`. `ScreenshotTest` renders every tab with sample data into `desktop/build/shots`; CI keeps them as the `desktop-screenshots` artifact. A new tab or a changed screen gets a shot there, and a pull request that changes the look says what the shots showed.
- The installer version is `appVersion` in `desktop/build.gradle.kts`. CI builds and tests the app on Linux and builds the Windows installer (`runeloader-desktop-windows` artifact). On Windows, CI and the release also start the shipped app with `--smoke=<file>` (it waits for the first scan, writes `ok` and quits), so an app that crashes on start never ships. Code that runs on start keeps working under that flag.
- The Library rules (rows, what an install brings, remove, update check results) live in `core/Library.java`; Android's `AddonManager` and the desktop `AppState` only run them on their threads and show the result. A new Library rule goes there with a case in `core/test/LibraryTest.java`.
- Desktop releases: bump `appVersion` in a pull request, merge it, then start **Desktop release** (`.github/workflows/desktop-release.yml`) on `main` from the Actions tab (with `publish` when the owner asked for a release), or push the tag `desktop-v<appVersion>`. It builds `runeloader-desktop-<version>.msi` with its SHA-256 and creates the release with `--latest=false`, so Android `SelfUpdate` keeps reading only Android releases.

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
- Install and update addons only through `Updater`, never with a copy of its logic in a screen or the background job. Any other write to the AddOns folder (remove, ZIP install) runs inside `Updater.LOCK`, so the screens and the background job never write it at the same time. Get the store through `Store.get` (on Android `AndroidPlatform.store(ctx)`), never a second `Store`.
- Do the network and disk work off the main thread. Show progress and errors to the user, never fail silently.
- Treat every downloaded zip and every server reply as untrusted. Keep the zip-slip check, the atomic swap in `Installer.java`, and the host check in `Http.java` that keeps an `Authorization` header on GitHub hosts.
- A new addon source goes into `Sources.java` with a row in the "Where addons come from" table of `README.md`. It declares which TOC field (`X-WoWI-ID`, `X-Wago-ID`, ...) holds its id.
- The app (`Sources.GitHub.latest`) and the indexer (`runeloader-index`, `indexer/forever.py`) pick the Forever build with the same rules. A change to those rules changes both, with a test case in each. The tokens that mark a desktop program zip (`AssetName.java`, `is_app_binary` there) stay identical on both sides, with a case in `core/test/AssetNameTest.java`. A repo that releases each module on its own tag (`Core-v1`, `Wheel-v2`, ...) is listed as its core (`owner/repo`) and one item per module (`owner/repo#Module`, the index's `module` key); each installs the newest published release of its own group: `Modules.java` here and `module_groups` there, with a case in `core/test/ModulesTest.java`. Code that needs the repository of an item id uses `Modules.repoOf`. Such a repo is a package in the app: Browse shows only its core (`Index.packageOf`), installing a core that is not installed yet installs every module (`AddonManager.install`), and the Library lists the modules under their core (`Packages.fold`, with a case in `core/test/PackagesTest.java`). Code that walks every Library row uses `AddonManager.all`, which includes the modules. The index's `requires` keys become `item.deps`, which `Updater` resolves through the catalog, then the index. A new `index.json` field is optional for the app; a breaking change raises `schema` there and `Index.SCHEMA` here.
- Discontinued addons come from the index only (`discontinued`, `discontinued_note`, `successor`, read in `IndexFields`, with a case in `core/test/IndexFieldsTest.java`). A new end-of-life signal goes into runeloader-index (`details.ended_notice` or `discontinued.txt`), never into the app. `Popularity.ORDER` keeps discontinued addons last.
- A new catalog entry goes into `Catalog.java` with the folders the addon installs, so the app can recognize a manual install.
- The GitHub index (runeloader-index) is a hint for search, metadata and update checks. An install always resolves the asset from GitHub directly (`Sources.latest`), because the index can be a day old. If the index is unreachable, the update check falls back to the per-addon check.
- The indexer owns icon conversion (TGA/BLP to PNG); the app only shows a ready PNG and keeps the letter icon until it loads. A new optional index key is mapped in `IndexFields.java`, with a case in `core/test/IndexFieldsTest.java`; the app must work when the key is missing.
- A new TOC suffix or a change to the TOC order goes into `Toc.java` only, and it must match the order the game uses. Add a case for it to `core/test/InstallTest.java`.
- Keep comments short and about intent that the code does not show.
- Do not commit build output (`build/`, `*.apk`, `*.idsig`) or `local.properties`.

## Releases

`.github/workflows/release.yml` builds a release from a tag:

1. In a pull request, bump `versionCode` and `versionName` in `build.gradle.kts` and `build.sh`, and merge it.
2. Tag the merge commit on `main` with `v<versionName>` and push the tag (`git tag v0.0.1 && git push origin v0.0.1`), or start the Release workflow on `main` from the Actions tab ("Run workflow"), which tags the commit it built.
3. The workflow checks that the tag matches both build files, builds with Gradle and `build.sh` using the release key, compares the two APKs, refuses a debug signature, and creates a **draft** release with `runeloader-<version>.apk` and its SHA-256.
4. The owner reviews the draft and publishes it. An agent publishes only when the owner asked for it: it starts the manual run with `publish` set, and the workflow publishes the release after the build.

The owner asks for a release after every merged bug fix: once the fix is merged with green CI, bump the version in a follow-up pull request (or in the fix itself) and publish the release right away, with the manual run and `publish` set. Do not wait for other changes to collect.

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
  - Android code outside an activity gets its text through `Lang.s()` and `Lang.q()`; core code through `Texts.s()` (see [Shared core](#shared-core)). `Installer.java` must also compile on a plain JVM, so its messages go through `Installer.Texts`, with an English default there and the resource in `Lang.INSTALLER`.
  - A new language needs a `res/values-<lang>` folder and an entry in `Lang.TAGS` and `Lang.NAMES`, with the language named in itself.
  - Each tab has one name (`nav_installed`, `nav_browse`, `nav_settings`), used by both the rail and the bottom bar. It must fit the 68dp rail item at 11sp: about nine Latin characters.

## Navigation

- `desktop/`: the desktop app (see [Desktop app](#desktop-app)).
- `core/src/app/wowforever/addons/`: code shared with the desktop app (see [Shared core](#shared-core)); `Model.java` there holds the data classes. `core/test/`: its tests, run by `core/test.sh`.
- `src/app/wowforever/addons/`: the Android app code. Besides the files in [How the app works](#how-the-app-works): `Ui.java` (palette, buttons, pills, letter icons, formatting), `ImageLoader.java` (logos with a memory and disk cache), `Lang.java` (UI language and localized strings outside an activity).
- `res/`: the adaptive launcher icon and vector icons.
- `stubs/`: lambda bootstrap stubs for `javac` with a trimmed `android.jar` (used only by `build.sh`).
- `test/`: JVM tests of Android-side classes (`LayoutSpec`, the network check).
- `AndroidManifest.xml`, `build.gradle.kts`, `build.sh`: the manifest and the two build paths.
- `.github/workflows/`: CI (`ci.yml`), Android releases (`release.yml`) and desktop releases (`desktop-release.yml`).
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

The code shared with the desktop app lives in `core/` (see [Shared core](#shared-core)). When you change anything there, run its tests (CI runs them too). The installer test also runs on real addon zips when you pass a folder of them:

```sh
core/test.sh
core/test.sh <folder-with-zips>
```

When you change `desktop/` or `core/`, also build and test the desktop app (CI does it on Linux and Windows):

```sh
./gradlew -p desktop build
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
