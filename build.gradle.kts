plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 compiles Kotlin itself; this only picks the Kotlin version it uses.
    // GeckoView 159 is built against Kotlin 2.4.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
}
