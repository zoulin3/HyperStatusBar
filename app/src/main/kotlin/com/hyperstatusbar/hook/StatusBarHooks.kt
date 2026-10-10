package com.hyperstatusbar.hook

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    /**
     * SystemUI 自己那个任务栈监听器的实现类。
     *
     * 它是 `ITaskStackListener.Stub` 的子类，并且由 SystemUI 在 `addListener()` 里
     * 用 `registerTaskStackListener(this)` 注册 —— 传的是真实 Binder，所以能被回调。
     * 我们挂钩它的入口，就等于复用 SystemUI 已经建好的这条链路。
     */
    private const val TASK_LISTENER_IMPL = "com.android.systemui.shared.system.TaskStackChangeListeners\$Impl"

    // 显示项用的系统设置键：这几个是 MIUI 自己读取的位置，属于"用户偏好"，
    // 和每应用规则不同，仍然走设置键，但会记住原值并还原（见 applyDisplaySettings）。
    private const val KEY_CLOCK_SECONDS = "clock_seconds"
    private const val KEY_BATTERY_PERCENT = "status_bar_show_battery_percent"

    /**
     * 巡检间隔（事件正常时的兜底）。
     *
     * 前台应用变化走 [hookTaskStackEvents] 挂到的 SystemUI 任务栈回调，是实时的；
     * 这个 ticker 只负责夜间时段 \/ 横屏 \/ 锁屏这类与前台应用无关的条件，
     * 以及视图被 MIUI 重建后的一次兜底，因此间隔可以放到很大。
     */
    private const val TICK_MS = 30_000L

    /** 事件路径不可用时的降级巡检间隔：宁可贵一点，也不能让规则失效。 */
    private const val FALLBACK_TICK_MS = 2_000L

    /** UsageStats 落盘有小延迟，事件到达后稍等再查，避免拿到上一个应用。 */
    private const val EVENT_DELAY_MS = 150L

    /** UsageEvents.Event.ACTIVITY_RESUMED（API 29+）。 */
    private const val EVENT_ACTIVITY_RESUMED = 1

    /** 任务栈事件里的包名只在这段时间内算"新鲜"；过期就重新查，避免一直用陈旧值。 */
    private const val TASK_EVENT_TTL_MS = 3_000L

    /** UsageStats 兜底窗口：够长才能覆盖"停在某个应用里很久"的情况。 */
    private const val USAGE_WINDOW_MS = 300_000L

    /** 取包名时依次尝试的 TaskInfo 字段；`topActivity` 被脱敏时还有别的可用。 */
    private val taskComponentFields =
        listOf("topActivity", "baseActivity", "origActivity", "realActivity")

    /** SystemUI 自己会 resume Activity，本模块的设置界面也不该被当成"当前应用"。 */
    private val ignoredPackages = setOf("com.android.systemui", "com.hyperstatusbar")

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    /** SystemUI 任务栈事件是否已经挂上（决定用实时事件还是降级巡检）。 */
    @Volatile
    private var taskEventsReady = false

    // —— 状态栏视图引用（弱引用，避免拖住 Activity 的视图树） ——

    /** 左区容器：时钟 + 通知图标 + 活动胶囊全在里面。 */
    private var leftRef = WeakReference<View>(null)

    /** 通知图标容器，左区隐藏的兜底。 */
    private var notificationRef = WeakReference<View>(null)

    /** 右区容器：信号 / WiFi / 电量。 */
    private var systemIconRef = WeakReference<View>(null)

    /** 我们亲手设成 GONE 的那个视图实例；只有自己改过的才负责还原（见 [setHidden]）。 */
    private var leftHiddenRef: WeakReference<View>? = null
    private var notificationHiddenRef: WeakReference<View>? = null
    private var systemIconHiddenRef: WeakReference<View>? = null

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

    /** [taskForegroundPackage] 的时间戳，用来判断这个值还新不新鲜。 */
    @Volatile
    private var taskForegroundAt = 0L

    /** 模块引用：日志写进 LSPosed 日志文件（SystemUI 的 Log 在部分 HyperOS 上看不到）。 */
    @Volatile
    private var moduleRef: XposedModule? = null

    /** 上一次实际生效的隐藏位；只有它变化时才记一条日志。 */
    @Volatile
    private var lastLoggedMask = -1

    private val ticker = object : Runnable {
        override fun run() {
            // 判断依据是"事件是否真的来过"，而不是"hook 是否挂上了"：
            // 挂上但收不到事件（厂商改动、监听器尚未注册）时也要能自动降级。
            val eventsWorking = taskForegroundAt != 0L
            if (!eventsWorking || hasTriggerConditions()) {
                runCatching { recompute("ticker") }
            }
            runCatching { applyZones() }
            runCatching { applyDisplaySettings() }
            mainHandler.postDelayed(this, if (eventsWorking) TICK_MS else FALLBACK_TICK_MS)
        }
    }

    /** 是否有"夜间 \/ 横屏 \/ 锁屏"这类需要靠 ticker 推动的全局条件。 */
    private fun hasTriggerConditions(): Boolean =
        Prefs.boolean(Prefs.Keys.SB_NIGHT) ||
            Prefs.boolean(Prefs.Keys.SB_LANDSCAPE) ||
            Prefs.boolean(Prefs.Keys.SB_LOCKSCREEN)

    /**
     * 安装。
     *
     * @return 成功建立的挂钩数量
     */
    fun install(module: XposedModule, classLoader: ClassLoader): Int {
        moduleRef = module
        appContext = resolveContext()
        if (appContext == null) {
            info(module, "拿不到系统 Context，状态栏管控不生效")
        }

        var installed = 0
        installed += hookBarClass(module, classLoader)
        installed += hookTaskStackEvents(module, classLoader)

        // SystemUI 起来时状态栏还没 inflate，稍后再做首轮应用。
        mainHandler.postDelayed({
            runCatching { recompute("startup") }
            runCatching { applyZones() }
            runCatching { applyDisplaySettings() }
        }, 4_000L)
        mainHandler.postDelayed(ticker, TICK_MS)

        val source = if (taskEventsReady) "SystemUI 任务栈事件（实时）" else "降级巡检 ${FALLBACK_TICK_MS}ms"
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
     * 挂钩 SystemUI 自己的任务栈监听器。
     *
     * 早先的写法是用 `Proxy` 动态实现 `ITaskStackListener` 再注册到
     * `ActivityTaskManager`，结果是**一次回调都收不到**：`registerTaskStackListener`
     * 收到的是接口实例，system_server 要用 `asBinder()` 拿真实 Binder，而动态代理
     * 生成的 `asBinder()` 返回 null，注册成功但永远不会被调用 —— 表现就是切换应用
     * 之后要等下一次轮询才生效。
     *
     * 现在改成挂钩 SystemUI 自己的 `TaskStackChangeListeners$Impl`（`ITaskStackListener.Stub`
     * 的真实子类）：事件由 binder 线程送到它的 `onTaskMovedToFront`，参数里就带着
     * `RunningTaskInfo`，包名不用另外查，也没有任何轮询。
     */
    private fun hookTaskStackEvents(module: XposedModule, classLoader: ClassLoader): Int {
        val implClass = runCatching { classLoader.loadClass(TASK_LISTENER_IMPL) }.getOrNull()
        if (implClass == null) {
            info(module, "未找到 $TASK_LISTENER_IMPL，前台应用只能靠巡检")
            return 0
        }

        var installed = 0

        // 任务移到前台：参数里直接有包名，可以立刻重算并立刻应用，没有延迟。
        runCatching {
            val method = implClass.getDeclaredMethod(
                "onTaskMovedToFront",
                ActivityManager.RunningTaskInfo::class.java,
            )
            method.isAccessible = true
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val pkg = chain.args.firstOrNull()?.let { taskPackageName(it) }
                    runCatching { onForegroundChanged(pkg, "task-front") }
                    return result
                }
            })
            installed++
        }

        // 任务栈整体变化：没有包名，走一次带延迟的查询（合并连续事件）。
        runCatching {
            val method = implClass.getDeclaredMethod("onTaskStackChanged")
            method.isAccessible = true
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    runCatching { scheduleRecompute() }
                    return result
                }
            })
            installed++
        }

        taskEventsReady = installed > 0
        if (!taskEventsReady) info(module, "挂钩任务栈事件失败，前台应用只能靠巡检")
        return installed
    }

    /**
     * 前台应用变化的即时处理。
     *
     * 这个方法由 binder 线程调用，所以只更新 volatile 状态，视图操作一律 post 回主线程。
     */
    private fun onForegroundChanged(pkg: String?, source: String) {
        if (pkg != null) {
            taskForegroundPackage = pkg
            taskForegroundAt = System.currentTimeMillis()
            lastForegroundPackage = pkg
        }
        mainHandler.post {
            runCatching { recompute(source) }
            runCatching { applyZones() }
        }
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
        val mode = Prefs.string(Prefs.Keys.SB_MODE)
        // 全局模式与前台应用无关，省掉每次巡检的 binder 查询。
        val packageName = if (mode == Prefs.SB_MODE_PER_APP) resolveForegroundPackage() else null
        if (packageName != null) lastForegroundPackage = packageName
        val ruleMask = Prefs.statusBarRuleMask(packageName)
        val trigger = triggerMask(context)
        desiredMask = ruleMask or trigger
        logState(source, mode, packageName, ruleMask, trigger)
    }

    /**
     * 记录一次判定结果。
     *
     * 只在"实际生效的隐藏位"变化时才写，切到规则相同的应用不会产生任何日志；
     * 不写共享配置，避免每次切换应用都做一次跨进程写入。
     */
    private fun logState(source: String, mode: String, packageName: String?, ruleMask: Int, trigger: Int) {
        if (desiredMask == lastLoggedMask) return
        lastLoggedMask = desiredMask
        val text = "src=$source mode=$mode pkg=${packageName ?: "?"} rule=$ruleMask trig=$trigger final=$desiredMask"
        runCatching { Log.i(TAG, "[StatusBarRule] $text") }
        moduleRef?.let { module -> runCatching { module.log(Log.INFO, TAG, "[StatusBarRule] $text") } }
    }

    /**
     * 前台应用包名。
     *
     * 三条来源按可靠性排序，第一个有结果的就用：
     *
     * 1. `ITaskStackListener.onTaskMovedToFront` 带过来的 TaskInfo（3 秒内新鲜才用）——
     *    最及时，但 Android 14 起 `topActivity` 会按调用者权限脱敏，经常是 null；
     * 2. `ActivityManager.getRunningTasks(1)` —— SystemUI 持有 REAL_GET_TASKS；
     * 3. `UsageStatsManager.queryEvents` 里最近的 ACTIVITY_RESUMED —— 最稳，作兜底。
     *
     * 三条都失败时返回上一次的结果：宁可用旧值，也不要在切应用的一瞬间把规则丢掉，
     * 否则状态栏会先显示出来、再藏回去。
     */
    private fun resolveForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        taskForegroundPackage
            ?.takeIf { now - taskForegroundAt <= TASK_EVENT_TTL_MS }
            ?.let { return it }
        runningTaskPackage()?.let { return it }
        usageStatsPackage()?.let { return it }
        return taskForegroundPackage ?: lastForegroundPackage
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

        setHidden(leftRef.get(), hideLeft, leftHiddenRef) { leftHiddenRef = it }
        // 兜底：切换状态栏样式时 MIUI 会把通知图标容器临时挂到右区的
        // fullscreen_notification_icon_area 上，只藏左容器盖不住这种情况。
        setHidden(notificationRef.get(), hideLeft, notificationHiddenRef) { notificationHiddenRef = it }
        setHidden(systemIconRef.get(), hideRight, systemIconHiddenRef) { systemIconHiddenRef = it }
    }

    /**
     * 按需设置可见性。
     *
     * 记的是"我们亲手设成 GONE 的那个 View 实例"，而不是一个布尔标志：MIUI 重建
     * 状态栏时会换出全新的 View 实例（默认 VISIBLE），只记布尔值的话新实例会被误判
     * 成"已经藏好了"而不再隐藏 —— 这正是切换应用后隐藏失效的原因之一。
     */
    private inline fun setHidden(
        view: View?,
        hide: Boolean,
        current: WeakReference<View>?,
        update: (WeakReference<View>?) -> Unit,
    ) {
        if (view == null) return
        val marked = current?.get()
        if (hide) {
            if (marked !== view) {
                view.visibility = View.GONE
                update(WeakReference(view))
            }
        } else if (marked === view) {
            // 只还原我们自己藏起来的那个，避免把 MIUI 主动隐藏的视图点亮。
            view.visibility = View.VISIBLE
            update(null)
        }
    }

    // ------------------------------------------------------------ 显示项（全局偏好）

    // 这三项是"用户偏好"而不是"每应用规则"，所以仍然走系统设置键 —— 那是 MIUI 自己
    // 读取的位置。区别是我们会记住改写前的原值，开关关掉时原样还回去，不留残留。

    private var originalClockSeconds: Int? = null
    private var originalBatteryPercent: Int? = null

    /** 已经写进系统设置的开关状态；只在它变化时才动 Settings，避免巡检时反复写。 */
    private var appliedClockSeconds: Boolean? = null
    private var appliedBatteryPercent: Boolean? = null

    private fun applyDisplaySettings() {
        val context = appContext ?: return
        if (!Prefs.boolean(Prefs.Keys.SB_ENABLE)) {
            restoreDisplaySettings(context)
            return
        }
        val resolver = context.contentResolver

        val clockSeconds = Prefs.boolean(Prefs.Keys.SB_CLOCK_SECONDS)
        if (appliedClockSeconds != clockSeconds) {
            appliedClockSeconds = clockSeconds
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
        }

        val hideBattery = Prefs.boolean(Prefs.Keys.SB_HIDE_BATTERY_PERCENT)
        if (appliedBatteryPercent != hideBattery) {
            appliedBatteryPercent = hideBattery
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
    }

    private fun restoreDisplaySettings(context: Context) {
        // 复位"已应用"标记：重新启用时要再写一次，否则会以为已经写过了。
        appliedClockSeconds = null
        appliedBatteryPercent = null
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

    /** 从 TaskInfo 的公开字段里取包名；`topActivity` 被脱敏时还有 baseIntent 可用。 */
    private fun taskPackageName(task: Any): String? {
        for (name in taskComponentFields) {
            val component = runCatching {
                val field = task.javaClass.getField(name)
                field.isAccessible = true
                field.get(task) as? ComponentName
            }.getOrNull() ?: continue
            component.packageName?.takeUnless { it in ignoredPackages }?.let { return it }
        }
        val intent = runCatching {
            val field = task.javaClass.getField("baseIntent")
            field.isAccessible = true
            field.get(task) as? Intent
        }.getOrNull()
        return intent?.component?.packageName?.takeUnless { it in ignoredPackages }
    }

    /** 当前任务栈顶部应用（SystemUI 持有 REAL_GET_TASKS）。 */
    private fun runningTaskPackage(): String? = runCatching {
        val context = appContext ?: return@runCatching null
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@runCatching null
        val task = manager.getRunningTasks(1).firstOrNull() ?: return@runCatching null
        task.topActivity?.packageName?.takeUnless { it in ignoredPackages }
            ?: task.baseActivity?.packageName?.takeUnless { it in ignoredPackages }
    }.getOrNull()

    /** UsageStats 里最近的 ACTIVITY_RESUMED。 */
    private fun usageStatsPackage(): String? = runCatching {
        val context = appContext ?: return@runCatching null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@runCatching null
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(end - USAGE_WINDOW_MS, end) ?: return@runCatching null
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
