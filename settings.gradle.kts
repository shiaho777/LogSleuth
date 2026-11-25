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
        if (providers.gradleProperty("logsleuth.sdkVersion").isPresent) {
            exclusiveContent {
                forRepository {
                    maven { url = uri(rootDir.resolve("sdk/build/maven-repository")) }
                }
                filter { includeModule("io.github.shiaho777.logsleuth", "logsleuth-sdk") }
            }
        }
        // libsu 6 is published to JitPack, not Maven Central.
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "LogSleuth"

include(":app")
include(":sdk")
include(":sample")
