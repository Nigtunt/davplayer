import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // 本地开发用：Windows 上直接跑 Compose UI，避免每次改 UI 都等 iOS CI
    jvm("desktop")
    jvmToolchain(17)

    // iPad 2019（A10）是 arm64，真机只需要 iosArm64；模拟器目标方便本地调试
    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64()
    )

    iosTargets.forEach { target: KotlinNativeTarget ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        // 本地桌面预览：Compose Desktop + JVM 版 Ktor 引擎
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-okhttp:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
        // 解析器单元测试：不需要 iOS 设备，desktop 上秒级跑完
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // 用 maybeCreate：macOS 上由默认层级自动创建，非 macOS 主机（iOS 目标被禁用时）
        // 也不会因为找不到 iosMain 而配置失败
        maybeCreate("iosMain").dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.3")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.davplayer.app.ios.MainKt"
    }
}
