plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.roottap.clicker"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.roottap.clicker"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "1.3.0"
    }
    signingConfigs {
        // A shared key so that sideloaded builds from GitHub always upgrade in place.
        // Documented in the README; rotate it if you fork the project for real distribution.
        create("shared") {
            storeFile = rootProject.file("keystore/roottap.jks")
            storePassword = "roottap"
            keyAlias = "roottap"
            keyPassword = "roottap"
        }
    }
    buildTypes {
        release {
            // The root injector is loaded reflectively by `app_process`; keeping the dex
            // unobfuscated is what makes that predictable on every ROM.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("en", "zh", "ru")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":kit"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.miuix.blur)
    implementation(libs.materialkolor.utilities)
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("stability_config.conf"))
    if (providers.gradleProperty("zeroui.composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
}
