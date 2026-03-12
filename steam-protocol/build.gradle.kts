plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.protobuf.kotlin.lite)
    api(libs.protobuf.javalite)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                maybeCreate("java").apply {
                    option("lite")
                }
                maybeCreate("kotlin").apply {
                    option("lite")
                }
            }
        }
    }
}
