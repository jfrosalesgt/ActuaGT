// Android app shell: hosts the shared Compose Multiplatform UI and
// initializes Firebase from google-services.json (not versioned).
// Convention: code and comments in English; user-facing (UI) copy in Spanish.

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.actuagt.app"
    compileSdk = 35

    // Maps API key lives in local.properties (never versioned).
    val mapsApiKey = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) load(file.inputStream())
    }.getProperty("MAPS_API_KEY", "")

    defaultConfig {
        applicationId = "com.actuagt.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.firebase.messaging)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
}
