// Gradle settings — declares where plugins/dependencies come from and which modules exist.
// (Kotlin DSL: this file is Kotlin, not Groovy. `.kts` = Kotlin script.)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Force all dependency repositories to be declared here (not in module build files) —
    // keeps dependency sourcing in one auditable place.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LumiLink"
include(":app")
