import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/workshop-adb.gradle.kts"))

// Keep local-only signing credentials in local.properties so Android Studio and CLI can share them.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String = localProperties.getProperty(name)?.trim().orEmpty()

fun releaseSigningValue(gradleName: String, envName: String): String =
    providers.gradleProperty(gradleName).orNull?.trim().orEmpty().ifEmpty {
        providers.environmentVariable(envName).orNull?.trim().orEmpty().ifEmpty {
            localProperty(gradleName)
        }
    }

val appVersionCode = providers.gradleProperty("application.version.code").orNull?.trim()?.toInt() ?: 1
val appVersionName = providers.gradleProperty("application.version.name").orNull?.trim().orEmpty().ifBlank { "1.0" }
val releaseStoreFilePath = releaseSigningValue("release.storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("release.storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("release.keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("release.keyPassword", "RELEASE_KEY_PASSWORD")
val releaseStoreFile = releaseStoreFilePath.takeIf { it.isNotEmpty() }?.let(rootProject::file)
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword.isNotEmpty() &&
    releaseKeyAlias.isNotEmpty() &&
    releaseKeyPassword.isNotEmpty()
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

if (releaseStoreFile != null && !releaseStoreFile.isFile) {
    throw GradleException("Release signing store file does not exist: ${releaseStoreFile.path}")
}
if (isReleaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Missing release signing config. Set gradle properties or local.properties keys " +
            "release.storeFile, release.storePassword, release.keyAlias, release.keyPassword " +
            "or env vars RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"
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
                storeFile = releaseStoreFile
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
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(project(":workshop-core"))
    implementation(project(":steam-protocol"))
    implementation("com.github.luben:zstd-jni:${libs.versions.zstd.get()}@aar")

    implementation(platform(libs.okhttpBom))
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
    implementation(libs.xlog)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.okhttpBom))
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.mockwebserver3)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
