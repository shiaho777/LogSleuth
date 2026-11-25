plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "io.github.shiaho777.logsleuth.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Intentionally minimal: the SDK must stay lightweight, permission-free
    // and network-free so any app can embed it safely.
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "io.github.shiaho777.logsleuth"
            artifactId = "logsleuth-sdk"
            version = providers.gradleProperty("logsleuth.sdkVersion").getOrElse("0.0.0-local")
            pom {
                name.set("LogSleuth SDK")
                description.set("On-device Android log, crash and ANR capture")
                url.set("https://github.com/shiaho777/LogSleuth")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm {
                    url.set("https://github.com/shiaho777/LogSleuth")
                    connection.set("scm:git:https://github.com/shiaho777/LogSleuth.git")
                }
            }
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("maven-repository"))
        }
    }
}
