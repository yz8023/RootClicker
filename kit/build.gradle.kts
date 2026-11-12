plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.zeroui.kit"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        val lock = providers.gradleProperty("zeroui.style").orNull.orEmpty()
        require(lock in setOf("", "miuix", "material")) { "zeroui.style must be miuix, material or empty" }
        buildConfigField("boolean", "LOCK_MIUIX", (lock == "miuix").toString())
        buildConfigField("boolean", "LOCK_MATERIAL", (lock == "material").toString())
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.miuix.ui)
    api(libs.miuix.icons)
    api(libs.miuix.nav)
    api(libs.androidx.navigationevent.compose)

    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.materialkolor.utilities)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

composeCompiler {
    if (providers.gradleProperty("zeroui.composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.zeroui"
                artifactId = "zeroui-kit"
                version = providers.gradleProperty("zeroui.version").getOrElse("0.1.0")
                pom {
                    name.set("ZeroUI Kit")
                    description.set("Compose design system with Miuix and Material 3 styles.")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "local"
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
    }
}
