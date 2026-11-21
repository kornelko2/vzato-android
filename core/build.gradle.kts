import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: dose plan, statuses, supply, reminders. No Android, tested on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 bytecode, like the app module; builds with any newer JDK (Android Studio's is 21).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
