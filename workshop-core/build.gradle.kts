plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":steam-protocol"))

    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okio)
    implementation(libs.xz)
    compileOnly(libs.zstd)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.zstd)
}
