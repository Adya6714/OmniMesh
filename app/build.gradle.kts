// app/build.gradle.kts
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)      // needed for Room's code generation
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)  // needed for Firebase
    id("com.google.firebase.crashlytics")  // ADD THIS
}

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) {
        load(secretsFile.inputStream())
    }
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        load(f.inputStream())
    }
}

android {
    namespace = "omnimesh.command1"
    compileSdk = 34

    defaultConfig {
        applicationId = "omnimesh.command1"
        minSdk = 26        // Android 8.0 — required for WorkManager + ML Kit
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${secrets.getProperty("GEMINI_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "GOOGLE_CLOUD_API_KEY",
            "\"${secrets.getProperty("GOOGLE_CLOUD_API_KEY", secrets.getProperty("GEMINI_API_KEY", ""))}\""
        )
        manifestPlaceholders["MAPS_API_KEY"] =
            localProperties.getProperty("MAPS_API_KEY")
                ?: secrets.getProperty("MAPS_API_KEY")
                ?: secrets.getProperty("GOOGLE_MAPS_API_KEY")
                ?: (project.findProperty("MAPS_API_KEY") as String? ?: "")

        // 16 KB page size: ship only 64-bit ABIs with proper ELF alignment in the APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true   // Shrinks APK size — important for mesh devices
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        debug {
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true    // We'll use Jetpack Compose for UI
        buildConfig = true
    }

    // Prevents build errors from duplicate files in dependencies; 16 KB page size (Android 15+).
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Where Android looks for ML model files
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.lifecycleRuntime.get()}")
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.livedata)

    // Room — local priority packet queue
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)   // kapt generates Room boilerplate at compile time

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // CameraX
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ML Kit Gemini Nano (Signal 4)
    implementation(libs.mlkit.genai)

    // Gemini API — real dispatch analysis
    implementation(libs.generativeai)

    // MediaPipe (Signal 1 + Signal 2)
    implementation(libs.mediapipe.vision)
    implementation(libs.mediapipe.audio)

    // TFLite — motion collapse classifier (Signal 3)
    implementation(libs.tensorflow.lite)

    // Nearby Connections (Mesh Layer)
    implementation(libs.nearby)

    // Location
    implementation(libs.location)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-ml-modeldownloader")

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Google Maps
    implementation(libs.maps.android)
    implementation(libs.maps.compose)
    implementation(libs.places)
    implementation(libs.translate)  // ML Kit on-device translation — no API key needed
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")


}
