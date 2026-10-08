package com.hyperstatusbar

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 模块 UI 进程的 Application。
 *
 * 启动时注册 [XposedServiceHelper] 监听器，LSPosed 会通过 XposedProvider 推送 binder；
 * 拿到 [XposedService] 后即可写入 RemotePreferences，跨进程同步给 SystemUI 中的 Hook。
 */
class PrivacyDotApp : Application(), XposedServiceHelper.OnServiceListener {

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    companion object {
        @Volatile
        private var currentService: XposedService? = null

        val service: XposedService?
            get() = currentService

        private val listeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceListener(listener: ServiceStateListener, notifyImmediately: Boolean = true) {
            listeners.add(listener)
            if (notifyImmediately) {
                runCatching { listener.onServiceStateChanged(currentService) }
            }
        }

        fun removeServiceListener(listener: ServiceStateListener) {
            listeners.remove(listener)
        }

        internal fun publishService(service: XposedService?) {
            currentService = service
            listeners.forEach { listener ->
                runCatching { listener.onServiceStateChanged(service) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { XposedServiceHelper.registerListener(this) }
    }

    override fun onServiceBind(service: XposedService) {
        publishService(service)
    }

    override fun onServiceDied(service: XposedService) {
        publishService(null)
    }
}
