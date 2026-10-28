// Shared KMP module: Android + iOS targets with Compose Multiplatform UI,
// Voyager navigation, Ktor networking, and Firebase (GitLive SDK).
// Convention: code and comments in English; user-facing (UI) copy in Spanish.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.actuagt.shared"
        compileSdk = 35
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform UI
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material.icons)

            // Navigation (Voyager)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.tab.navigator)

            // Networking (Ktor) + serialization + coroutines
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // Firebase (GitLive Kotlin SDK, callable from commonMain)
            implementation(libs.gitlive.firebase.functions)
            implementation(libs.gitlive.firebase.app)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.storage)
        }

        androidMain.dependencies {
            // Pins the version GitLive firebase-functions-android omits.
            implementation(libs.firebase.functions)
            implementation(libs.firebase.messaging)
            implementation(libs.maps.compose)
            implementation(libs.maps.compose.utils)
            // ActivityResult (TakePicture) for the real camera wiring.
            implementation(libs.androidx.activity.ktx)
            // Pinned Firebase Android SDK (versions taken from firebase-bom 35.1.0).
            implementation(libs.firebase.auth)
            implementation(libs.firebase.common)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            // Android HTTP engine for Ktor
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            // iOS HTTP engine for Ktor
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
