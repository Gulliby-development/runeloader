# Runeloader

Addon manager for the [WoW Forever Android client](https://github.com/jaredgei/wow-forever-android). It installs, updates and removes addons in `<game folder>/_forever_/Interface/AddOns`.

Addons come from GitHub releases built for Forever, found through the nightly [Runeloader index](https://github.com/Gulliby-development/runeloader-index) or added by link, plus WoWInterface. No accounts or API keys.

Optional background auto-update (Settings → Auto-update): checks every 6–24 hours, also when the app is closed, and either notifies you or installs the updates.

Android 10 or newer. Works only with WoW Forever builds. The app speaks English, German, Spanish, French, Polish, Portuguese, Russian and Chinese, and follows your system language unless you pick one in Settings.

## Install

1. Download `runeloader-<version>.apk` from the latest [release](../../releases/latest).
2. Open it on your device and allow installs from this source when Android asks.
3. Start Runeloader, allow "All files access", and pick the WoW Forever folder (the one with `.build.info` and `_forever_`).

## Build

JDK 17 and the Android SDK (`platforms;android-35`, `build-tools;35.0.0`):

```bash
./gradlew assembleRelease               # build/outputs/apk/release/wow-addons.apk
ANDROID_HOME=~/Android/Sdk ./build.sh   # the same app without Gradle: ./wow-addons.apk
```

Without the release key, both builds are signed with a debug key. [AGENTS.md](AGENTS.md) has the code map, the development workflow and the checks to run.

Runeloader is a fan project. It is not affiliated with Blizzard Entertainment.
