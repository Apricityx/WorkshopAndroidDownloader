plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":steam-protocol"))

    implementation(platform(libs.okhttpBom))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okio)
    implementation(libs.xz)
    compileOnly(libs.zstd)

    testImplementation(platform(libs.okhttpBom))
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.zstd)
}
