import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

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

val sourceSets = extensions.getByType<SourceSetContainer>()

tasks.register<JavaExec>("runWorkshopProxyLab") {
    group = "application"
    description = "Runs the local Steam Workshop reverse proxy lab."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("top.apricityx.workshop.workshop.lab.WorkshopProxyLabServerKt")
    providers.gradleProperty("labHost").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
        args("--host", it)
    }
    providers.gradleProperty("labPort").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
        args("--port", it)
    }
    providers.gradleProperty("labStrategy").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
        args("--strategy", it)
    }
    providers.gradleProperty("labUpstreamProxy").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
        args("--upstream-proxy", it)
    }
    if (project.hasProperty("labArgs")) {
        args(
            project.property("labArgs")
                .toString()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank),
        )
    }
    standardInput = System.`in`
}
