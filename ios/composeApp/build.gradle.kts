import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
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
            }
        }
        // 用 maybeCreate：macOS 上由默认层级自动创建，非 macOS 主机（iOS 目标被禁用时）
        // 也不会因为找不到 iosMain 而配置失败
        maybeCreate("iosMain").dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.3")
        }
    }
}
