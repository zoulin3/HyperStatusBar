package com.hyperstatusbar.hook

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.hyperstatusbar.core.Prefs
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Proxy

/**
 * 状态栏管控。
 *
 * 原实现是 KernelSU 模块（statusbar_auto）：root 常驻 shell 监听
 * `wm_set_resumed_activity`，再执行 `cmd statusbar send-disable-flag` 与 `settings put`。
 * 那套做法有三个绕不开的代价：
 *
 * 1. `send-disable-flag` 只有 3 个粗粒度位，左侧通知区、时钟、系统图标区无法分开控制；
 * 2. 沉浸模式要写全局 `policy_control`，会覆盖用户自己的设置、也会影响别的应用；
 * 3. 依赖 root + 开机脚本 + 一个单独的 WebUI。
 *
 * 本模块跑在 SystemUI 进程内，状态栏就是它自己画的，所以直接改视图：
 *
 * | 目标 | 视图（HyperOS 4 / Android 17） |
 * |---|---|
 * | 时钟 | `MiuiPhoneStatusBarView` 左容器内的 `R.id.clock` |
 * | 通知图标 | `mNotificationIconAreaInner`（NotificationIconContainer） |
 * | 系统图标（信号 / WiFi / 电量） | `mSystemIconArea` |
 *
 * 相比 disable 位的好处：三项互相独立、不需要 root、不写任何全局设置、
 * 不残留（SystemUI 重启即恢复），而且改的是视图属性，不会打断 MIUI 自己的布局动画。
 *
 * 时机上也不靠轮询：前台应用变化走 `ITaskStackListener` 事件，
 * MIUI 自己改动状态栏结构的入口（`updateCutoutLocation` / `updateNotificationIconAreaInnnerParent` /
 * `setStatusBarType` / `onConfigurationChanged` …）全部挂钩后重新断言一次，
 * 再叠加一个 `OnGlobalLayoutListener` 兜底，保证不会和 MIUI 的更新互相打架。
 *
 * 所有被挂钩的方法内部都只做 runCatching 包裹的轻量操作，任何一步失败都只影响
 * 状态栏管控本身，不会波及隐私指示器那条主链路，也不会让 SystemUI 崩。
 */
internal object StatusBarHooks {

    private const val TAG = "PrivacyDot"

    private const val BAR_CLASS = "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"

    // 显示项用的系统设置键：这几个是 MIUI 自己读取的位置，属于"用户偏好"，
    // 和每应用规则不同，仍然走设置键，但会记住原值并还原（见 applyDisplaySettings）。
    private const val KEY_CLOCK_SECONDS = "clock_seconds"
    private const val KEY_BATTERY_PERCENT = "status_bar_show_battery_percent"

    /** 前台应用事件的兜底轮询间隔：只用来推动夜间时段这类与前台应用无关的条件。 */
    private const val TICK_MS = 30_000L

    /** UsageStats 落盘有小延迟，事件到达后稍等再查，避免拿到上一个应用。 */
    private const val EVENT_DELAY_MS = 120L

    /** UsageEvents.Event.ACTIVITY_RESUMED（API 29+）。 */
    private const val EVENT_ACTIVITY_RESUMED = 1

    private const val FOREGROUND_WINDOW_MS = 10_000L

    /** SystemUI 自己会 resume Activity，本模块的设置界面也不该被当成"当前应用"。 */
    private val ignoredPackages = setOf("com.android.systemui", "com.hyperstatusbar")

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var taskListener: Any? = null

    @Volatile
    private var taskListenerReady = false

    // —— 状态栏视图引用（弱引用，避免拖住 Activity 的视图树） ——

    /** 左区容器：时钟 + 通知图标 + 活动胶囊全在里面。 */
    private var leftRef = WeakReference<View>(null)

    /** 通知图标容器，左区隐藏的兜底。 */
    private var notificationRef = WeakReference<View>(null)

    /** 右区容器：信号 / WiFi / 电量。 */
    private var systemIconRef = WeakReference<View>(null)

    /** 我们是否亲手把某个视图设成 GONE；只有自己改过的才负责还原。 */
    private var leftHidden = false
    private var notificationHidden = false
    private var systemIconHidden = false

    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** 目标隐藏位；由 [recompute] 更新，[applyZones] 只做廉价应用。 */
    @Volatile
    private var desiredMask = 0

    /** 最近一次可靠取得的前台包名；UsageStats 短暂查不到时不立即丢失应用规则。 */
    @Volatile
    private var lastForegroundPackage: String? = null

    /** 由 ITaskStackListener 的实时回调直接更新，等价于面具模块监听 resumed_activity。 */
    @Volatile
    private var taskForegroundPackage: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { recompute("ticker") }
            runCatching { applyZones() }
            runCatching { applyDisplaySettings() }
            mainHandler.postDelayed(this, TICK_MS)
        }
    }

    /**
     * 安装。
     *
     * @return 成功建立的挂钩数量
     */
    fun install(module: XposedModule, classLoader: ClassLoader): Int {
        appContext = resolveContext()
        if (appContext == null) {
            info(module, "拿不到系统 Context，状态栏管控不生效")
        }

        var installed = 0
        installed += hookBarClass(module, classLoader)
        if (registerTaskListener(module)) installed++

        // SystemUI 起来时状态栏还没 inflate，稍后再做首轮应用。
        mainHandler.postDelayed({
            runCatching { recompute("startup") }
            runCatching { applyZones() }
            runCatching { applyDisplaySettings() }
        }, 4_000L)
        mainHandler.postDelayed(ticker, TICK_MS)

        val source = if (taskListenerReady) "TaskStackListener 事件" else "定时轮询"
        info(module, "状态栏管控已启动（前台应用来源：$source）")
        return installed
    }

    /** 配置变化后立刻重算：设置页改完马上生效，不用等下一次应用切换。 */
    fun onConfigChanged() {
        mainHandler.post {
            runCatching { recompute("config") }
            runCatching { applyZones() }
            runCatching { applyDisplaySettings() }
        }
    }

    // ------------------------------------------------------------------ 挂钩

    /**
     * 挂钩 `MiuiPhoneStatusBarView`。
     *
     * 只挂"会改变这些视图可见性 / 引用关系"的方法，不挂 onDraw 之类的热路径。
     * 每个钩子做的事都一样：先让 MIUI 自己跑完，再按当前配置断言一次，保证我们的
     * 隐藏不会被 MIUI 的更新覆盖，也不会覆盖 MIUI 自己的可见性判断（见 [setHidden]）。
     */
    private fun hookBarClass(module: XposedModule, classLoader: ClassLoader): Int {
        val barClass = runCatching { classLoader.loadClass(BAR_CLASS) }.getOrNull()
        if (barClass == null) {
            info(module, "未找到 $BAR_CLASS，状态栏管控无法生效")
            return 0
        }

        var installed = 0

        // 视图绑定与结构更新：这些是 MIUI 唯一会动到左容器 / 系统图标区 /
        // 通知图标容器的入口，挂上它们就不需要任何轮询。
        val reassertAfter = listOf(
            "onFinishInflate" to emptyList<Class<*>>(),
            "onAttachedToWindow" to emptyList(),
            "updateCutoutLocation" to emptyList(),
            "updateNotificationIconAreaInnnerParent" to emptyList(),
            "updatePaddings" to emptyList(),
            "updateSafeInsets" to emptyList(),
            "setStatusBarType" to listOf(Int::class.javaPrimitiveType!!),
            "onConfigurationChanged" to listOf(Configuration::class.java),
        )

        for ((name, params) in reassertAfter) {
            val method = runCatching {
                barClass.getDeclaredMethod(name, *params.toTypedArray())
            }.getOrNull() ?: continue
            runCatching {
                method.isAccessible = true
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        runCatching { onBarChanged(chain.thisObject as? View) }
                        return result
                    }
                })
                installed++
            }
        }

        // 通知图标容器是后续由 MIUI 塞进来的，单独挂钩以拿到最新引用。
        runCatching {
            val method = barClass.getDeclaredMethod("setNotificationIconAreaInnner", View::class.java)
            method.isAccessible = true
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val bar = chain.thisObject as? View
                    val view = chain.args.firstOrNull() as? View
                    if (view != null) notificationRef = WeakReference(view)
                    runCatching { onBarChanged(bar) }
                    return result
                }
            })
            installed++
        }

        info(module, "已挂钩 $BAR_CLASS 的 $installed 个入口")
        return installed
    }

    /** 状态栏结构变化后的统一处理：刷新引用、补挂布局监听、重新断言。 */
    private fun onBarChanged(bar: View?) {
        if (bar != null) {
            captureViews(bar)
            attachLayoutListener(bar)
        }
        runCatching { applyZones() }
    }

    /**
     * 全局布局监听兜底。
     *
     * 视图被 MIUI 重新挂到别的父容器、或系统字体 / 密度变化导致重排时，
     * 上面那些具名入口未必都会经过，这里补一道。
     */
    private fun attachLayoutListener(bar: View) {
        if (layoutListener != null) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            runCatching {
                captureViews(bar)
                applyZones()
            }
        }
        runCatching {
            bar.viewTreeObserver.addOnGlobalLayoutListener(listener)
            layoutListener = listener
        }
    }

    /** 从公开字段里取视图引用；字段名取自 HyperOS 4 的 MiuiPhoneStatusBarView。 */
    private fun captureViews(bar: View) {
        (readField(bar, "mStatusBarLeftContainer") as? View)
            ?.let { leftRef = WeakReference(it) }
        (readField(bar, "mNotificationIconAreaInner") as? View)
            ?.let { notificationRef = WeakReference(it) }
        (readField(bar, "mSystemIconArea") as? View)
            ?.let { systemIconRef = WeakReference(it) }
    }

    // ------------------------------------------------------------ 前台应用事件

    /**
     * 注册任务栈监听。
     *
     * 用反射代理实现 `ITaskStackListener` 而不是去 hook SystemUI 自己的
     * `TaskStackChangeListeners`：我们只需要"任务栈变了"这个信号，
     * 包名另外查更稳，也不必依赖那个类的内部结构。
     */
    private fun registerTaskListener(module: XposedModule): Boolean = runCatching {
        val interfaceClass = Class.forName("android.app.ITaskStackListener")
        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass),
        ) { _, method, args ->
            if (method.name == "onTaskMovedToFront") {
                val task = args?.firstOrNull()
                val pkg = task?.let { runningTaskPackage(it) }
                if (pkg != null) {
                    taskForegroundPackage = pkg
                    lastForegroundPackage = pkg
                    info(module, "前台应用切换: $pkg")
                }
            }
            when (method.name) {
                "onTaskMovedToFront",
                "onTaskStackChanged",
                "onTaskFocusChanged",
                "onTaskCreated",
                "onTaskRemoved",
                -> scheduleRecompute()
            }
            defaultValue(method.returnType)
        }
        taskListener = proxy

        val service = Class.forName("android.app.ActivityTaskManager")
            .getMethod("getService")
            .invoke(null)
            ?: error("ActivityTaskManager.getService() 返回 null")
        service.javaClass
            .getMethod("registerTaskStackListener", interfaceClass)
            .invoke(service, proxy)

        taskListenerReady = true
        true
    }.getOrElse {
        taskListenerReady = false
        info(module, "注册 TaskStackListener 失败，退化为慢速巡检: ${it.javaClass.simpleName}: ${it.message}")
        false
    }

    /**
     * 事件到达时的重算。
     *
     * 延迟 [EVENT_DELAY_MS]：任务栈事件先于 UsageStats 落盘，立刻查会拿到上一个应用。
     * 这里靠 Handler 合并连续事件（removeCallbacksAndMessages），快速连切应用时只算一次。
     */
    private var pendingRecompute = false

    private fun scheduleRecompute() {
        if (pendingRecompute) return
        pendingRecompute = true
        mainHandler.postDelayed({
            pendingRecompute = false
            runCatching {
                recompute("task-event")
                applyZones()
                applyDisplaySettings()
            }
        }, EVENT_DELAY_MS)
    }

    // ---------------------------------------------------------------- 计算 / 应用

    /** 重新计算目标隐藏位。 */
    private fun recompute(source: String) {
        if (!Prefs.boolean(Prefs.Keys.SB_ENABLE)) {
            desiredMask = 0
            return
        }
        val context = appContext
        val packageName = taskForegroundPackage
            ?: context?.let { foregroundPackage(it) }
            ?: lastForegroundPackage
        if (packageName != null) lastForegroundPackage = packageName
        val ruleMask = Prefs.statusBarRuleMask(packageName)
        val trigger = triggerMask(context)
        desiredMask = ruleMask or trigger
        Log.i(TAG, "[StatusBarRule] source=$source mode=${Prefs.string(Prefs.Keys.SB_MODE)} pkg=${packageName ?: "<unknown>"} ruleMask=$ruleMask trigger=$trigger final=$desiredMask")
    }

    /** 触发条件：夜间时段 / 横屏 / 锁屏，命中时叠加"触发时隐藏内容"。 */
    private fun triggerMask(context: Context?): Int {
        val trigger = Prefs.statusBarTriggerMask()
        if (trigger == 0) return 0
        var mask = 0
        if (Prefs.boolean(Prefs.Keys.SB_NIGHT) && inNightWindow()) mask = mask or trigger
        if (Prefs.boolean(Prefs.Keys.SB_LANDSCAPE) && isLandscape(context)) mask = mask or trigger
        if (Prefs.boolean(Prefs.Keys.SB_LOCKSCREEN) && isLocked(context)) mask = mask or trigger
        return mask
    }

    /**
     * 把 [desiredMask] 落到视图上。
     *
     * 关键点：**隐藏的是 MIUI 自己的两个容器，而不是容器里的子视图**。
     *
     * 左区容器（`phone_status_bar_left_container`）在 `status_bar.xml` 里是
     * `layout_width="wrap_content"` 的 LinearLayout，而 MIUI 的
     * `updateCutoutLocation()` 会在每次刘海 / 旋转 / 折叠状态变化时重写它的
     * `LayoutParams.width` 与 `weight`。只把里面的时钟设成 GONE，容器宽度照样被
     * 撑开、MIUI 还可能把子视图重新挂回去 —— 表现就是"左边藏不掉"。
     * 对容器本身设 GONE，该视图直接退出测量与布局，MIUI 再怎么改参数也不会显示。
     *
     * 右区（`system_icon_area`）同理，一个容器搞定，这也是它一直能生效的原因。
     */
    private fun applyZones() {
        val mask = desiredMask
        val hideLeft = (mask and Prefs.SB_LEFT) != 0
        val hideRight = (mask and Prefs.SB_RIGHT) != 0

        setHidden(leftRef.get(), hideLeft, leftHidden) { leftHidden = it }
        // 兜底：切换状态栏样式时 MIUI 会把通知图标容器临时挂到右区的
        // fullscreen_notification_icon_area 上，只藏左容器盖不住这种情况。
        setHidden(notificationRef.get(), hideLeft, notificationHidden) { notificationHidden = it }
        setHidden(systemIconRef.get(), hideRight, systemIconHidden) { systemIconHidden = it }
    }

    private inline fun setHidden(view: View?, hide: Boolean, currentlyHidden: Boolean, update: (Boolean) -> Unit) {
        if (view == null) return
        if (hide) {
            if (!currentlyHidden) {
                view.visibility = View.GONE
                update(true)
            }
        } else if (currentlyHidden) {
            // 只还原我们自己藏起来的那个，避免把 MIUI 主动隐藏的视图点亮。
            view.visibility = View.VISIBLE
            update(false)
        }
    }

    // ------------------------------------------------------------ 显示项（全局偏好）

    // 这三项是"用户偏好"而不是"每应用规则"，所以仍然走系统设置键 —— 那是 MIUI 自己
    // 读取的位置。区别是我们会记住改写前的原值，开关关掉时原样还回去，不留残留。

    private var originalClockSeconds: Int? = null
    private var originalBatteryPercent: Int? = null

    private fun applyDisplaySettings() {
        val context = appContext ?: return
        if (!Prefs.boolean(Prefs.Keys.SB_ENABLE)) {
            restoreDisplaySettings(context)
            return
        }
        val resolver = context.contentResolver

        val clockSeconds = Prefs.boolean(Prefs.Keys.SB_CLOCK_SECONDS)
        runCatching {
            if (clockSeconds) {
                if (originalClockSeconds == null) {
                    originalClockSeconds = Settings.System.getInt(resolver, KEY_CLOCK_SECONDS, 0)
                }
                putSystemInt(resolver, KEY_CLOCK_SECONDS, 1)
            } else if (originalClockSeconds != null) {
                putSystemInt(resolver, KEY_CLOCK_SECONDS, originalClockSeconds ?: 0)
                originalClockSeconds = null
            }
        }

        val hideBattery = Prefs.boolean(Prefs.Keys.SB_HIDE_BATTERY_PERCENT)
        runCatching {
            if (hideBattery) {
                if (originalBatteryPercent == null) {
                    originalBatteryPercent = Settings.System.getInt(resolver, KEY_BATTERY_PERCENT, 1)
                }
                putSystemInt(resolver, KEY_BATTERY_PERCENT, 0)
            } else if (originalBatteryPercent != null) {
                putSystemInt(resolver, KEY_BATTERY_PERCENT, originalBatteryPercent ?: 1)
                originalBatteryPercent = null
            }
        }
    }

    private fun restoreDisplaySettings(context: Context) {
        val resolver = context.contentResolver
        runCatching {
            originalClockSeconds?.let {
                putSystemInt(resolver, KEY_CLOCK_SECONDS, it)
                originalClockSeconds = null
            }
        }
        runCatching {
            originalBatteryPercent?.let {
                putSystemInt(resolver, KEY_BATTERY_PERCENT, it)
                originalBatteryPercent = null
            }
        }
    }

    /** 先读后写：值没变就不产生写入，避免每一次巡检都打一次设置。 */
    private fun putSystemInt(resolver: android.content.ContentResolver, key: String, value: Int) {
        if (Settings.System.getInt(resolver, key, Int.MIN_VALUE) == value) return
        Settings.System.putInt(resolver, key, value)
    }

    // ------------------------------------------------------------------ 环境查询

    /**
     * 前台包名。
     *
     * SystemUI 持有 PACKAGE_USAGE_STATS，这条查询没有权限坑，
     * 也不会因为某个 Android 版本改了 RunningTaskInfo 的字段名而失效。
     */
    private fun runningTaskPackage(task: Any): String? = runCatching {
        val field = task.javaClass.getField("topActivity")
        field.isAccessible = true
        val component = field.get(task) as? android.content.ComponentName
        component?.packageName?.takeUnless { it in ignoredPackages }
    }.getOrNull()

    private fun foregroundPackage(context: Context): String? {
        // 先取当前任务栈。UsageStats 是历史事件流，在 HyperOS 快速切换应用时可能落后。
        val taskPackage = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return@runCatching null
            val name = manager.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
            name?.takeUnless { it in ignoredPackages }
        }.getOrNull()
        if (taskPackage != null) return taskPackage

        // 当前任务栈不可读时，再退回 UsageStats 最近的 resumed 事件。
        return runCatching {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@runCatching null
            val end = System.currentTimeMillis()
            val events = manager.queryEvents(end - FOREGROUND_WINDOW_MS, end)
                ?: return@runCatching null
            val event = UsageEvents.Event()
            var result: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != EVENT_ACTIVITY_RESUMED) continue
                val name = event.packageName ?: continue
                if (name !in ignoredPackages) result = name
            }
            result
        }.getOrNull()
    }

    private fun isLandscape(context: Context?): Boolean =
        context?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun isLocked(context: Context?): Boolean = runCatching {
        val manager = context?.getSystemService(Context.KEYGUARD_SERVICE)
            as? android.app.KeyguardManager
        manager?.isKeyguardLocked == true
    }.getOrDefault(false)

    private fun inNightWindow(): Boolean {
        val start = parseMinutes(Prefs.string(Prefs.Keys.SB_NIGHT_START)) ?: return false
        val end = parseMinutes(Prefs.string(Prefs.Keys.SB_NIGHT_END)) ?: return false
        if (start == end) return false
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(java.util.Calendar.MINUTE)
        return if (start < end) now >= start && now < end else now >= start || now < end
    }

    private fun parseMinutes(value: String): Int? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    // ------------------------------------------------------------------ 基础设施

    private fun resolveContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val current = activityThread.getMethod("currentActivityThread").invoke(null)
        activityThread.getMethod("getSystemContext").invoke(current) as? Context
    }.getOrNull()

    private fun readField(owner: Any, name: String): Any? = runCatching {
        val field: Field = owner.javaClass.getField(name)
        field.isAccessible = true
        field.get(owner)
    }.getOrNull()

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> ' '
        else -> null
    }

    private fun info(module: XposedModule?, message: String) {
        runCatching { Log.i(TAG, "[StatusBar] $message") }
        if (module != null) {
            runCatching { module.log(Log.INFO, TAG, "[StatusBar] $message") }
        }
    }
}
