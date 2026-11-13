// The desktop app is its own Gradle build, so the Android build never loads Kotlin or Compose (see desktop/README.md).
// Run it from the repository root: ./gradlew -p desktop run
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "runeloader-desktop"
