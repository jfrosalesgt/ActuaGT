// Android app shell: hosts the shared Compose Multiplatform UI and
// initializes Firebase from google-services.json (not versioned).
// Convention: code and comments in English; user-facing (UI) copy in Spanish.

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

    defaultConfig {
        applicationId = "com.actuagt.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    // Firebase Android BoM: resolves versions for transitive Firebase artifacts
    // NOTE: string notation required, platform(CatalogAccessor) is an error in Kotlin 2.3 (KT-58759).
    implementation(platform("com.google.firebase:firebase-bom:${libs.versions.firebase.bom.get()}"))
}
