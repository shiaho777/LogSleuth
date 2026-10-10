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
        // libsu 6 is published to JitPack, not Maven Central.
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "LogSleuth"

include(":app")
include(":sdk")
include(":sample")
