// build.gradle.kts (root — not inside /app)
// Kotlin plugin versions must match gradle/libs.versions.toml (used by :app).
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("com.android.library") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}



