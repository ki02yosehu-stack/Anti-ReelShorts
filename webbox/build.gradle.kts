plugins {
    id("com.android.application")
}

android {
    namespace = "com.antireelshorts.webbox"
    // GeckoView 159 needs API 37.2.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.antireelshorts.webbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        // The release build is what gets installed: a debuggable app runs its
        // Java/Kotlin code (incl. GeckoView's) without ahead-of-time compilation.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    // GeckoView's engine is ~150 MB uncompressed. Compressing it in the APK
    // makes the download much smaller (it's unpacked once at install).
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // arm64 only: GeckoView bundles a whole browser engine per CPU type.
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261102144711")
}
