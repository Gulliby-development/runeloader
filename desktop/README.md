# Runeloader for Windows, Linux and macOS

The desktop app: Kotlin and Compose Desktop on the shared Java core in `../core/src`.
It is its own Gradle build, so the Android build never loads Kotlin or Compose.

```sh
./gradlew -p desktop run          # start the app
./gradlew -p desktop build        # compile and run the tests
./gradlew -p desktop packageMsi   # Windows installer (on Windows); packageDeb, packageDmg on their systems
```

It needs JDK 21. The texts and icons are the Android app's own `res/values*/strings.xml` and
`res/drawable/*.xml`, copied into the jar by the build, so every language is shared.

Settings and the list of installed addons live in `%APPDATA%\Runeloader` on Windows,
`~/Library/Application Support/Runeloader` on macOS and `~/.config/runeloader` on Linux.
