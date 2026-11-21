import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The signing key lives in keystore/ so every build can update the installed app in place.
// keystore/ is never put in git: without it, the release APK is built unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.kolib.app"
    compileSdk = 36

    // KoLib can be switched to any of its languages, so an app bundle must keep them all.
    bundle {
        language {
            enableSplit = false
        }
    }

    defaultConfig {
        applicationId = "dev.kolib.app"
        minSdk = 29
        // Keep at 36: from 37, Android drops the opt-out in the manifest that lets the readers turn a tablet's screen.
        targetSdk = 36
        versionCode = 110
        versionName = "3.4"
    }

    // An APK for each kind of processor, a little smaller, and a universal one that runs on any of them.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file("keystore/" + keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "1536m"
                // A fresh test process now and then: the screenshot tests' native drawing memory adds up.
                test.setForkEvery(10)
                // File names in UTF-8, as on the Kobo's drive, so "Niccolò" stays "Niccolò" in the fake Kobo.
                test.environment("LC_ALL", "C.UTF-8")
                // Screenshots of the screens are written to build/outputs/roborazzi.
                test.systemProperty("roborazzi.test.record", "true")
                // Robolectric fetches its Android runtime from Google's Maven Central mirror.
                test.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                test.systemProperty("robolectric.dependency.repo.id", "google-central-mirror")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // The language models' engine finds its processor-specific parts as files in the app's library folder.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Reads CBR (RAR, RAR5) and CB7 comics, as Mihon does (libarchive; BSD licence, the Android wrapper Apache 2.0).
    implementation("me.zhanghai.android.libarchive:library:1.1.6")
    // Text recognition in scanned pages: Tesseract, with Leptonica (Apache 2.0, BSD).
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // Pairing two devices with a QR code: ZXing draws and reads the code, CameraX shows the camera (both Apache 2.0).
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.78.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.78.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
