# Working on Runeloader

These instructions apply to every coding agent (Claude Code, GitHub Copilot, Codex and others) and to human contributors.

## Project

Runeloader is an Android app that installs, updates and removes addons for the [WoW Forever Android client](https://github.com/jaredgei/wow-forever-android). It is plain Java on the Android SDK, with no libraries and no XML layouts: the whole UI is built in code. Read `README.md` for the build. The product name is Runeloader; the package and the build output keep their old names for now.

- Package `app.wowforever.addons`, minSdk 29, targetSdk 34, compileSdk 35.
- Addons go to `<game folder>/_forever_/Interface/AddOns`.

## How the app works

- `MainActivity.java` holds the UI: the tabs (installed, browse, settings), the list and detail panel, the folder picker, installs, updates and gamepad input.
- `Sources.java` and `Catalog.java` find addons: GitHub releases with a `forever` flavor in `release.json`, CurseForge (the user's own key) and WoWInterface.
- `Installer.java` unpacks a zip into the AddOns folder: it finds addon folders by their `.toc`, unpacks to a temporary folder, swaps atomically and refuses zip-slip paths.
- `Toc.java` picks the TOC file the same way the Forever client does: `_Forever`, then `_Camelot`, then `_Mainline`, then the base `.toc`.
- `Store.java` keeps settings and the list of installed addons. `Http.java` follows redirects by hand, so the CurseForge key goes only to CurseForge hosts.

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
- When the task is complete and [Verification](#verification) passes, open a pull request to `main`. `main` takes changes only through pull requests with green CI. Do not push to `main` directly.

## Working conventions

- Keep the app free of third-party libraries. Use the Android SDK and the Java standard library only. A new dependency needs an issue and the owner's decision first.
- Keep the source compatible with Java 8 language level. `build.sh` compiles with `-source 8 -target 8`, and Gradle with Java 17. Code that compiles only in one of them breaks the other build.
- There are two build paths, Gradle (`build.gradle.kts`) and `build.sh`. They must produce the same app. When you change the package, the version, an SDK level, a permission or the signing setup, change both files in the same pull request. CI builds both and compares their package, version, SDK levels and permissions.
- Bump `versionCode` and `versionName` in both build files for every release. Android refuses an install over a newer `versionCode`.
- Never commit a signing key or its passwords. A release build gets the key from the `SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` environment variables (CI secrets, see [Releases](#releases)); without them, both build paths sign with a debug key. Only the release key makes an APK that installs over a published one.
- Build the UI in code through the helpers in `Ui.java` (palette, buttons, pills, icons). Do not add XML layouts.
- Landscape is the main layout: most players hold the device sideways, as in the game. Every screen must work in landscape at 4:3, 16:9 and 20:9, and also in portrait, by touch and by gamepad. Text must not be cut mid-word, and buttons must not leave the screen. Check all of them when you change a screen (see [Verification](#verification)).
- Do the network and disk work off the main thread. Show progress and errors to the user, never fail silently.
- Treat every downloaded zip and every server reply as untrusted. Keep the zip-slip check, the atomic swap in `Installer.java`, and the host check that keeps the CurseForge key on CurseForge hosts.
- A new addon source goes into `Sources.java` with its own section in `README.md`. A new catalog entry goes into `Catalog.java` with the folders the addon installs, so the app can recognize a manual install.
- A new TOC suffix or a change to the TOC order goes into `Toc.java` only, and it must match the order the Forever client uses. Add a case for it to `test/InstallTest.java`.
- Keep comments short and about intent that the code does not show.
- Do not commit build output (`build/`, `*.apk`, `*.idsig`) or `local.properties`.

## Releases

`.github/workflows/release.yml` builds a release from a tag:

1. In a pull request, bump `versionCode` and `versionName` in `build.gradle.kts` and `build.sh`, and merge it.
2. Tag the merge commit on `main` with `v<versionName>` and push the tag: `git tag v0.0.1 && git push origin v0.0.1`.
3. The workflow checks that the tag matches both build files, builds with Gradle and `build.sh` using the release key, compares the two APKs, refuses a debug signature, and creates a **draft** release with `runeloader-<version>.apk` and its SHA-256.
4. The owner reviews the draft and publishes it. Only the owner publishes a release.

The workflow needs these repository secrets (Settings, Secrets and variables, Actions). It fails when one is missing:

- `SIGNING_KEYSTORE_BASE64`: the release keystore file in base64 (`base64 -w0 release.jks`).
- `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`: the keystore password, the key alias and the key password.

A new secret for the app build (for example a CurseForge key) goes into both `ci.yml` and `release.yml`, and into this list.

## Language

- Write code, identifiers, comments, commit messages, issues, pull requests and these instructions in English.
- `README.md` is in English and stays short: what the app is, how to install it from a release, how to build it.
- The app UI is in Russian today. English becomes the main UI language, with more languages to follow; until then, keep new UI strings in the style of the existing ones.

## Navigation

- `src/app/wowforever/addons/`: all app code. Besides the files in [How the app works](#how-the-app-works): `Ui.java` (palette, buttons, pills, letter icons, formatting), `ImageLoader.java` (logos with a memory and disk cache), `Model.java` (data classes).- `res/`: the adaptive launcher icon and vector icons.
- `stubs/`: lambda bootstrap stubs for `javac` with a trimmed `android.jar` (used only by `build.sh`).
- `test/`: JVM tests for the installer, TOC and network paths.
- `AndroidManifest.xml`, `build.gradle.kts`, `build.sh`: the manifest and the two build paths.
- `.github/workflows/`: CI (`ci.yml`) and releases (`release.yml`).

## Verification

CI runs these on every pull request to `main`. Run them before you open one:

```sh
./gradlew assembleDebug assembleRelease lint
ANDROID_HOME=<sdk> ./build.sh
```

When you change `Installer.java`, `Toc.java` or `Model.java`, also run the installer test on real addon zips:

```sh
mkdir -p /tmp/t && javac -d /tmp/t src/app/wowforever/addons/{Installer,Toc,Model}.java test/InstallTest.java
java -cp /tmp/t app.wowforever.addons.InstallTest <folder-with-zips> /tmp/fakegame
```

For a UI change, install the debug APK on an emulator or a device (`adb install -r build/outputs/apk/debug/wow-addons-debug.apk`) and click through the changed screens. Check landscape first, then portrait. On an emulator, these commands give each aspect ratio without a new virtual device:

```sh
adb shell cmd window user-rotation lock 1   # landscape
adb shell wm size 1080x1440                 # 4:3
adb shell wm size 1080x1920                 # 16:9
adb shell wm size reset                     # the device's own ratio
adb shell cmd window user-rotation free     # rotation back to normal
```

Attach screenshots of the changed screens at 4:3 and 16:9 landscape to the pull request. Say what you checked and on which Android version. Report any check that failed or that you could not run.
