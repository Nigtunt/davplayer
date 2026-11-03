// 独立的 Gradle 构建：与根目录的 Android 工程完全隔离，互不影响。
// 用法：gradle -p ios :composeApp:linkReleaseFrameworkIosArm64
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DavPlayerIos"
include(":composeApp")
