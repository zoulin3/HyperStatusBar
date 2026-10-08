import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

android {
    namespace = "com.hyperstatusbar"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.hyperstatusbar"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // 签名信息从仓库外的 signing.properties 读取（该文件已在 .gitignore 中排除），
    // 避免把密钥口令提交到公开仓库。文件格式：
    //   storeFile=xxx.jks
    //   storePassword=...
    //   keyAlias=...
    //   keyPassword=...
    val signingProps = Properties()
    val signingPropsFile = rootProject.file("signing.properties")
    if (signingPropsFile.exists()) {
        signingPropsFile.inputStream().use { signingProps.load(it) }
    }
    val signingFileName: String? = signingProps.getProperty("storeFile")
    val signingKeystore: File? = signingFileName?.let { rootProject.file(it) }
    val hasSigningKey: Boolean =
        signingKeystore != null && signingKeystore.exists()

    signingConfigs {
        if (hasSigningKey) {
            create("eta") {
                storeFile = signingKeystore
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 开启 R8：dex 从 ~26 MB 压到 ~8 MB，且去掉全部未引用的 Compose 组件。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没有 signing.properties 时产出未签名 APK，仍然可以正常构建与调试。
            if (hasSigningKey) signingConfig = signingConfigs.getByName("eta")
        }
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("eta")
        }
    }

    compileOptions {
        // Miuix 的 miuix-nav 用 JVM 21 编译，entry<T>() 是 inline 函数，
        // 目标版本低于 21 会直接报 "Cannot inline bytecode built with JVM target 21"。
        // KernelSU 的 manager 同样是 21。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // META-INF/xposed/** 是 libxposed 的模块声明，必须保留在 APK 里。
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // 关闭「预测性返回手势」时要自己接住返回事件，抢在 miuix-nav 的处理器之前。
    implementation(libs.androidx.navigationevent.compose)
}
