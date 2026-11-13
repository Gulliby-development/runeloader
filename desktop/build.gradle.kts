// Runeloader for Windows, Linux and macOS: Compose Desktop on the shared Java core (../core/src).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

/** The desktop app's own version; desktop releases are tagged desktop-v<version>. */
val appVersion = "0.2.0"

kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

sourceSets["main"].java.srcDir("../core/src")

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    // Dispatchers.Main on the desktop is the Swing event thread; Compose does not bring it.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    testImplementation(kotlin("test"))
}

// The UI strings and vector icons are the Android app's own files, so every language is shared.
tasks.processResources {
    from("../res") {
        include("values*/strings.xml", "drawable/*.xml", "drawable-nodpi/*.png")
        into("res")
    }
    val v = appVersion
    inputs.property("version", v)
    filesMatching("version.txt") { expand("version" to v) }
}

compose.desktop {
    application {
        mainClass = "app.wowforever.addons.DesktopMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Runeloader"
            // jpackage wants MAJOR > 0 on Windows and macOS, so installers count from 1.x: 0.1.0 -> 1.1.0.
            packageVersion = appVersion.split(".").let { "${it[0].toInt() + 1}.${it[1]}.${it[2]}" }
            description = "Install and update World of Warcraft addons"
            vendor = "Gulliby"
            includeAllModules = true
            windows {
                iconFile.set(project.file("icons/runeloader.ico"))
                perUserInstall = true
                menuGroup = "Runeloader"
                // A fixed upgrade code lets a new installer replace the old version.
                upgradeUuid = "5f6f3c1e-2d0b-4b8e-9a51-7c3d2e1b9a40"
            }
            linux { iconFile.set(project.file("icons/runeloader.png")) }
            macOS { bundleID = "app.wowforever.addons.desktop" }
        }
    }
}

tasks.test { useJUnitPlatform() }
