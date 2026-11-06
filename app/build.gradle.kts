plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.antireelshorts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.antireelshorts"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    // CI signs with a fixed key when the SIGNING_KEYSTORE_B64 / SIGNING_PASSWORD
    // secrets are set, so updates install over the previous version (keeping
    // the adb-granted permission). Without them, the per-machine debug key is used.
    val keystore = System.getenv("SIGNING_KEYSTORE")
    if (keystore != null) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(keystore)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "antireelshorts"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
