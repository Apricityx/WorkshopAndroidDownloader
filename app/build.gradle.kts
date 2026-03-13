import org.gradle.api.GradleException
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/workshop-adb.gradle.kts"))

val appVersionCode = providers.gradleProperty("application.version.code").orNull?.trim()?.toInt() ?: 1
val appVersionName = providers.gradleProperty("application.version.name").orNull?.trim().orEmpty().ifBlank { "1.0" }
val releaseStoreFilePath = providers.environmentVariable("RELEASE_STORE_FILE").orNull?.trim().orEmpty()
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull?.trim().orEmpty()
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull?.trim().orEmpty()
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull?.trim().orEmpty()
val hasReleaseSigning = releaseStoreFilePath.isNotEmpty() &&
    releaseStorePassword.isNotEmpty() &&
    releaseKeyAlias.isNotEmpty() &&
    releaseKeyPassword.isNotEmpty()
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

if (hasReleaseSigning && !File(releaseStoreFilePath).isFile) {
    throw GradleException("RELEASE_STORE_FILE does not exist: $releaseStoreFilePath")
}
if (isReleaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Missing release signing env vars. Required: " +
            "RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"
    )
}

android {
    namespace = "top.apricityx.workshop"
    compileSdk = 36

    defaultConfig {
        applicationId = "top.apricityx.workshop"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"Apricityx\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"WorkshopAndroidDownloader\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":workshop-core"))
    implementation(project(":steam-protocol"))
    implementation("com.github.luben:zstd-jni:${libs.versions.zstd.get()}@aar")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
