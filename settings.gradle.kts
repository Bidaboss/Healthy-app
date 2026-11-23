pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Healthy"

// :core = logique pure Kotlin (format d'import), testable sans SDK Android.
// :app  = l'application Android (Compose + Room).
include(":core")
include(":app")
