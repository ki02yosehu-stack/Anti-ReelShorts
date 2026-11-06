plugins {
    id("com.android.application")
}

android {
    namespace = "com.antireelshorts.webbox"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.antireelshorts.webbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
}

dependencies {
    // arm64 only: GeckoView bundles a whole browser engine per CPU type.
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261102144711")
}
