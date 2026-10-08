# HyperStatusBar

适配 **HyperOS 4 / Android 17（API 37）** 的 LSPosed 模块，用于隐藏隐私指示器并控制状态栏显示内容。

模块运行在 `com.android.systemui` 进程内，直接操作状态栏自己的视图，不依赖 Magisk / KernelSU 脚本，也不写入系统设置（显示项除外，且会记住原值并在关闭时还原）。

## 功能

### 隐私指示器

- 摄像头 / 麦克风 / 定位 / 投屏 四类指示器可分别开关
- 应用范围：全部应用 / 白名单 / 黑名单 / 仅按应用规则
- 「仅按应用规则」可为每个应用单独指定要隐藏的类型
- 修复了 HyperOS 4 上频繁切换麦克风 / 摄像头状态导致 SystemUI 崩溃的问题

### 状态栏管控

- 隐藏左区（时钟 + 通知图标 + 活动胶囊）
- 隐藏右区（信号 / Wi-Fi / 电量）
- 左右都隐藏
- 应用范围：全部应用 / 仅按应用规则
- 显示项：时钟显示秒、隐藏电池百分比
- 触发条件：夜间时段 / 横屏 / 锁屏时自动隐藏指定区域

### 界面

- 主题模式（跟随系统 / 浅色 / 深色）与 Monet 取色
- 悬浮底栏 / 液态玻璃效果
- 预测性返回手势开关
- 一键重启 SystemUI
- 一键导出日志

界面素材来自 Miuix 与 KernelSU（均为 Apache-2.0）。

## 适配环境

| 项目 | 值 |
| --- | --- |
| 系统 | HyperOS 4 / Android 17（API 37） |
| 注入框架 | LSPosed（libxposed API 102） |
| 作用域 | `com.android.systemui` |
| minSdk | 34 |
| targetSdk | 36 |
| compileSdk | 37 |

## 使用

1. 安装 APK，在 LSPosed 中启用模块。
2. 作用域勾选「系统界面」（`com.android.systemui`）。
3. 重启 SystemUI 或重启设备。
4. 打开模块，按需配置。
5. 修改设置后点右上角「重启 SystemUI」生效。

## 构建

需要 JDK 25 与 Android SDK（compileSdk 37）。

```
./gradlew :app:assembleRelease --no-daemon --console=plain
```

产物位于 `app/build/outputs/apk/release/app-release.apk`。

签名配置从仓库外的 `signing.properties` 读取，该文件已被 `.gitignore` 排除：

```
storeFile=your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

没有该文件时依然可以正常构建，只是产出未签名 APK。

## 已知问题

- **状态栏「仅按应用规则」仍属实验功能。** 部分环境下会表现为所有应用都套用默认规则，即与「全部应用」表现一致。
- 状态栏「全部应用」模式已验证可用。
- 隐私指示器功能基本正常。
- 注入 SystemUI 属于高风险操作，遇到 SystemUI 反复崩溃时，请长按电源键强制重启，或通过 LSPosed 管理器停用模块。

## 风险提示

- 模块 hook 的是 SystemUI 关键路径，SystemUI 版本变化后可能失效甚至崩溃。
- 请勿同时启用多个修改状态栏的模块。
- 使用前建议备份，并确认自己能通过 Recovery 或 LSPosed 管理器停用模块。

## 许可

界面素材来自 Miuix 与 KernelSU，遵循 Apache-2.0。
