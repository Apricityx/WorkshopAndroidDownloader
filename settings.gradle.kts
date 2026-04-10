pluginManagement {
    repositories {
        val useAliyunMirror = run {
            fun parseBooleanFlag(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null
            }

            parseBooleanFlag(gradle.startParameter.projectProperties["useAliyunMirror"])
                ?: parseBooleanFlag(System.getenv("USE_ALIYUN_MIRROR"))
                ?: (System.getenv("CI").isNullOrBlank() && System.getenv("GITHUB_ACTIONS").isNullOrBlank())
        }

        if (useAliyunMirror) {
            maven(url = "https://maven.aliyun.com/repository/gradle-plugin") {
                name = "AliyunGradlePlugin"
            }
            maven(url = "https://maven.aliyun.com/repository/google") {
                name = "AliyunGoogle"
            }
            maven(url = "https://maven.aliyun.com/repository/public") {
                name = "AliyunPublic"
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

fun parseBooleanFlag(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> null
}

val useAliyunMirror = parseBooleanFlag(gradle.startParameter.projectProperties["useAliyunMirror"])
    ?: parseBooleanFlag(System.getenv("USE_ALIYUN_MIRROR"))
    ?: (System.getenv("CI").isNullOrBlank() && System.getenv("GITHUB_ACTIONS").isNullOrBlank())

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useAliyunMirror) {
            maven(url = "https://maven.aliyun.com/repository/google") {
                name = "AliyunGoogle"
            }
            maven(url = "https://maven.aliyun.com/repository/public") {
                name = "AliyunPublic"
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "WorkshopOnAndroid"

include(":app")
include(":steam-protocol")
include(":workshop-core")
