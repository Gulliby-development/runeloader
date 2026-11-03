# Copilot instructions

Follow [AGENTS.md](../AGENTS.md) in the repository root. It holds the rules for every coding agent in this project: the issue and branch workflow, the working conventions and the checks to run before a pull request.

The most important rules:

- Work on a `feature/<name>` or `bugfix/<name>` branch from `main`. Never push to `main`.
- Plain Java on the Android SDK, Java 8 language level, no third-party libraries, no XML layouts.
- Keep `build.gradle.kts` and `build.sh` in step: the same package, version, SDK levels and permissions.
- Before a pull request, run `./gradlew assembleDebug assembleRelease lint` and `./build.sh`.
- Before you file or fix a bug, follow [the file-issue skill](../.agents/skills/file-issue/SKILL.md).
