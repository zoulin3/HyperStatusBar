# libxposed 的模块入口：这个类由 LSPosed 通过反射按名加载，必须原样保留，
# 混淆掉就会变成"模块装了但完全不生效"。
-keep class com.hyperstatusbar.ModuleMain { *; }
-keep class com.hyperstatusbar.PrivacyDotApp { *; }

# libxposed API / service：模块与框架之间的契约，接口名不能改。
-keep class io.github.libxposed.** { *; }
-keep interface io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# Hook 目标类在 SystemUI 进程里，靠 ClassLoader 反射拿，这里不能裁。
-dontwarn com.android.systemui.**

# Compose 运行时依赖内部反射。
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# Miuix 组件在组合期通过稳定的合成函数调用，保留其公开 API 名称，
# 避免 R8 同名合并后出现不可复现的组合错乱。
-keep class top.yukonga.miuix.kmp.basic.** { *; }
-keep class top.yukonga.miuix.kmp.preference.** { *; }
-keep class top.yukonga.miuix.kmp.theme.** { *; }

# 保留行号便于排查线上崩溃（体积开销很小）。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
