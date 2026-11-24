import java.util.Properties

// Kotlin support is built into AGP 9; applying org.jetbrains.kotlin.android is an error.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The release key lives outside the repository: keystore.properties in the root (gitignored) or
// ~/.android-keys/vzato-keystore.properties. Absent on a fresh clone: release then builds unsigned.
val keystoreProperties = Properties().apply {
    val file = listOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".android-keys/vzato-keystore.properties"),
    ).firstOrNull { it.exists() }
    file?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.vzato"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.vzato"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Puts Vzato into Android 13+'s per-app language picker (src/main/res/resources.properties).
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
    }

    lint {
        // A hardcoded or untranslated string ships English to Czech readers.
        error += setOf("HardcodedText", "MissingTranslation", "StringFormatInvalid", "StringFormatMatches", "MissingQuantity")
    }
}

ksp {
    // The schema of every database version is kept in git, so migrations can be tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
