package com.hyperstatusbar

import android.content.SharedPreferences
import android.util.Log
import com.hyperstatusbar.core.Prefs
import com.hyperstatusbar.hook.PrivacyHooks
import com.hyperstatusbar.hook.StatusBarHooks
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class ModuleMain : XposedModule() {

    private companion object {
        const val TAG = "PrivacyDot"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    /** 配置变化监听：必须在进程生命周期内保持强引用。 */
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runCatching { PrivacyHooks.refreshVisibleState() }
                .onFailure { info("配置变化后刷新失败: ${it.javaClass.simpleName}") }
            // 状态栏管控是"跟着前台应用走"的，配置一改必须立刻重算，
            // 否则要等到下一次应用切换才会生效。
            runCatching { StatusBarHooks.onConfigChanged() }
                .onFailure { info("状态栏配置刷新失败: ${it.javaClass.simpleName}") }
        }

    private fun info(message: String) {
        runCatching { log(Log.INFO, TAG, message) }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        // 只有 SystemUI 进程需要读取配置；其他进程直接退出，不保留生命周期回调。
        if (param.processName != SYSTEM_UI_PACKAGE) {
            detach()
            return
        }

        val remote = runCatching { getRemotePreferences(Prefs.GROUP) }.getOrNull()
        Prefs.attachRemote(remote)
        if (remote == null) {
            info("RemotePreferences 不可用，将使用默认配置")
        } else {
            Prefs.registerRemoteListener(preferenceListener)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SYSTEM_UI_PACKAGE) return

        val installed = runCatching {
            PrivacyHooks.install(this, param.classLoader)
        }.getOrElse {
            info("安装 Hook 失败: ${it.javaClass.simpleName}: ${it.message}")
            -1
        }

        // 状态栏管控：把原来 KernelSU 模块（statusbar_auto）的能力搬进 SystemUI 进程，
        // 不需要 root，也不需要开机脚本。装不上只是这一项不生效，不影响隐私指示器。
        val statusBarInstalled = runCatching {
            StatusBarHooks.install(this, param.classLoader)
        }.getOrElse {
            info("安装状态栏管控失败: ${it.javaClass.simpleName}: ${it.message}")
            -1
        }

        val total = if (installed < 0) installed else installed + maxOf(statusBarInstalled, 0)
        info("SystemUI Hook 安装完成，共 $total 处（状态栏管控 $statusBarInstalled）")
        // 把注入结果写回共享配置，设置页据此显示"是否真的挂上了"。
        Prefs.recordHookInstall(total)
    }
}
