import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 签名配置（密钥不入库，详见 docs/RELEASE.md）：
 *  - 本地：keystore/davplayer.jks + keystore/keystore.properties（均被 .gitignore 忽略）
 *  - CI ：从 GitHub Secrets 还原 jks 到 keystore/davplayer.jks，密码通过环境变量传入
 * 只有签名一致，应用内「检查更新 → 下载 → 安装」才能覆盖安装。
 * 找不到密钥时回退到默认 debug 签名（仅本地调试用，不能用于发版）。
 */
val keyPropsFile = rootProject.file("keystore/keystore.properties")
val keyProps = Properties().apply {
    if (keyPropsFile.exists()) keyPropsFile.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String, fallback: String? = null): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keyProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: fallback

val davKeystore = rootProject.file(
    System.getenv("DAV_KEYSTORE_PATH")?.takeIf { it.isNotBlank() } ?: "keystore/davplayer.jks"
)
val davStorePassword = signingValue("storePassword", "DAV_STORE_PASSWORD")
val davKeyAlias = signingValue("keyAlias", "DAV_KEY_ALIAS", "davplayer")
val davKeyPassword = signingValue("keyPassword", "DAV_KEY_PASSWORD")
val hasDavSigning = davKeystore.exists() &&
        !davStorePassword.isNullOrBlank() && !davKeyPassword.isNullOrBlank()

if (!hasDavSigning) {
    logger.warn(
        "⚠️ 未找到签名密钥 ${davKeystore.path}（或缺少密码配置），本次将使用本机默认 debug 签名。" +
                "这样打出的 APK 无法覆盖安装已发布版本，应用内更新也会失败。配置方法见 docs/RELEASE.md"
    )
}

android {
    namespace = "com.davplayer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.davplayer.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 11
        versionName = "3.1.0"
    }

    signingConfigs {
        if (hasDavSigning) {
            create("davplayer") {
                storeFile = davKeystore
                storePassword = davStorePassword
                keyAlias = davKeyAlias
                keyPassword = davKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasDavSigning) signingConfig = signingConfigs.getByName("davplayer")
        }
        release {
            isMinifyEnabled = false
            if (hasDavSigning) signingConfig = signingConfigs.getByName("davplayer")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
}
