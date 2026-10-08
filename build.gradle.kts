buildscript {
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
