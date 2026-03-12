pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin") {
            name = "AliyunGradlePlugin"
        }
        maven(url = "https://maven.aliyun.com/repository/google") {
            name = "AliyunGoogle"
        }
        maven(url = "https://maven.aliyun.com/repository/public") {
            name = "AliyunPublic"
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google") {
            name = "AliyunGoogle"
        }
        maven(url = "https://maven.aliyun.com/repository/public") {
            name = "AliyunPublic"
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "WorkshopOnAndroid"

include(":app")
include(":steam-protocol")
include(":workshop-core")
