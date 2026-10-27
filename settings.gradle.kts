// Project wiring: repositories, version catalog, and included modules.
// Convention: code and comments in English; user-facing (UI) copy in Spanish.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ActuaGT"
include(":shared")
