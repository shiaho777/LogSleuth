import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val protectedSigning = listOf(
    "LOGSLEUTH_SIGNING_STORE_FILE",
    "LOGSLEUTH_SIGNING_STORE_PASSWORD",
    "LOGSLEUTH_SIGNING_KEY_ALIAS",
    "LOGSLEUTH_SIGNING_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val useProtectedSigning = protectedSigning.values.any { it != null }
if (useProtectedSigning) {
    require(protectedSigning.values.all { !it.isNullOrBlank() }) {
        "All LogSleuth release signing environment variables are required"
    }
}

android {
    namespace = "io.github.shiaho777.logsleuth.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.shiaho777.logsleuth.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (useProtectedSigning) {
                storeFile = file(protectedSigning.getValue("LOGSLEUTH_SIGNING_STORE_FILE")!!)
                storePassword = protectedSigning.getValue("LOGSLEUTH_SIGNING_STORE_PASSWORD")
                keyAlias = protectedSigning.getValue("LOGSLEUTH_SIGNING_KEY_ALIAS")
                keyPassword = protectedSigning.getValue("LOGSLEUTH_SIGNING_KEY_PASSWORD")
            } else if (providers.environmentVariable("GITHUB_ACTIONS").orNull != "true") {
                val props = Properties()
                val propsFile = rootProject.file("keystore.properties")
                if (propsFile.isFile) propsFile.inputStream().use { props.load(it) }
                props.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { storeFile = file(it) }
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    sourceSets.getByName("main").assets.srcDir(rootProject.file("mcp/contract"))

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coroutines.android)

    implementation(libs.shizuku.aidl)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.libsu.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    // Two-device remote-ADB verification (src/androidTest); skipped unless
    // a remote_host argument is passed to the runner.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.junit)
}
