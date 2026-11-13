// Single-module Android app. No dependencies: plain Java + the Android SDK.
plugins {
    id("com.android.application") version "8.8.0"
}

android {
    namespace = "app.wowforever.addons"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.wowforever.addons"
        minSdk = 29
        targetSdk = 34
        versionCode = 11
        versionName = "0.0.11"
    }

    // The sources keep the flat layout that build.sh uses; core/ is the code shared with desktop.
    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src", "core/src")
        res.srcDirs("res")
    }

    // The release key is not in the repository. Point SIGNING_KEYSTORE at it and set
    // SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD; without it,
    // builds use the local debug key.
    val keystore = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    val signing = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
        debug {
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

// Rename the release APK to wow-addons.apk
android.applicationVariants.all {
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            if (buildType.name == "release") "wow-addons.apk" else "wow-addons-debug.apk"
    }
}
