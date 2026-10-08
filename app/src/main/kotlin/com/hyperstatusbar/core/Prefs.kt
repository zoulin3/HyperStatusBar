package com.hyperstatusbar.core

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * 模块配置中枢。
 *
 * Hook 进程（SystemUI）在模块加载时通过 [attachRemote] 缓存 libxposed 提供的只读
 * RemotePreferences；设置界面通过 [remotePreferencesForUi] 写入同一 group，
 * 由 LSPosed 负责跨进程同步。
 *
 * 这里的读取都在隐私状态变化时才会发生（不是每帧），但仍然避免重复分配：
 * 包名列表只在配置变化时解析一次并缓存。
 */
object Prefs {

    /** 远程配置组名，UI 写入与 Hook 读取必须一致。 */
    const val GROUP = "privacydot_prefs"

    /** 本应用私有兜底存储：LSPosed 未连接时外观设置仍然可用。 */
    const val LOCAL_GROUP = "privacydot_local"

    /**
     * 上一次是否成功连上过 LSPosed 服务。
     *
     * 用途是消除冷启动闪烁：服务 binder 是异步送达的，首帧必然拿到 null，
     * 直接按 null 渲染就会先闪一下"未激活"再跳成"已激活"。
     * 用上次结果做初值，连上后若不一致会自动纠正。
     */
    const val LOCAL_LAST_SERVICE_READY = "last_service_ready"

    /**
     * 只由 Hook 侧写入的诊断键。
     *
     * 读写规则（PrefsState 遵循）：
     * - 这几个键以远端为准，因为只有 SystemUI 里的模块会写它们；
     * - 其余键全部"本地优先"——本应用是唯一写入方，
     *   若读的时候让远端优先，远端里上一次的旧值就会在每次 reload 时覆盖刚写入的
     *   新值，表现就是开关拨了又弹回去、下拉选了不变。这是之前"改不动"的根因。
     */
    val REMOTE_ONLY_KEYS: Set<String> = setOf(Keys.HOOK_COUNT, Keys.HOOK_TIME)

    object Keys {
        const val MASTER_ENABLE = "master_enable"

        const val HIDE_CAMERA = "hide_camera"
        const val HIDE_MIC = "hide_mic"
        const val HIDE_LOCATION = "hide_location"
        const val HIDE_MEDIA_PROJECTION = "hide_media_projection"

        /** Hook 注入诊断信息：由 SystemUI 进程内的模块写入，供 UI 展示。 */
        const val HOOK_COUNT = "hook_count"
        const val HOOK_TIME = "hook_time"

        const val FILTER_MODE = "filter_mode"
        const val FILTER_PACKAGES = "filter_packages"
        const val INDICATOR_RULES = "indicator_rules"

        const val HIDE_LAUNCHER_ICON = "hide_launcher_icon"

        // —— 状态栏管控 ——
        const val SB_ENABLE = "sb_enable"
        const val SB_MODE = "sb_mode"
        const val SB_RULES = "sb_rules"
        const val SB_DEFAULT_MASK = "sb_default_mask"
        const val SB_NIGHT = "sb_night"
        const val SB_NIGHT_START = "sb_night_start"
        const val SB_NIGHT_END = "sb_night_end"
        const val SB_LANDSCAPE = "sb_landscape"
        const val SB_LOCKSCREEN = "sb_lockscreen"
        const val SB_TRIGGER_MASK = "sb_trigger_mask"
        const val SB_CLOCK_SECONDS = "sb_clock_seconds"
        const val SB_HIDE_BATTERY_PERCENT = "sb_hide_battery_percent"

        const val THEME_MODE = "theme_mode"
        const val MONET = "monet"
        const val FLOATING_BAR = "floating_bar"
        const val LIQUID_GLASS = "liquid_glass"
        const val PREDICTIVE_BACK = "predictive_back"
    }

    /** 全部布尔开关及其默认值。 */
    val BOOLEAN_DEFAULTS: Map<String, Boolean> = mapOf(
        Keys.MASTER_ENABLE to true,
        Keys.HIDE_CAMERA to true,
        Keys.HIDE_MIC to true,
        Keys.HIDE_LOCATION to true,
        Keys.HIDE_MEDIA_PROJECTION to true,
        Keys.HIDE_LAUNCHER_ICON to false,
        Keys.MONET to false,
        Keys.FLOATING_BAR to true,
        Keys.LIQUID_GLASS to true,
        Keys.PREDICTIVE_BACK to true,
        Keys.SB_ENABLE to false,
        Keys.SB_NIGHT to false,
        Keys.SB_LANDSCAPE to false,
        Keys.SB_LOCKSCREEN to false,
        Keys.SB_CLOCK_SECONDS to false,
        Keys.SB_HIDE_BATTERY_PERCENT to false,
    )

    /** 字符串型开关默认值。 */
    val STRING_DEFAULTS: Map<String, String> = mapOf(
        Keys.FILTER_MODE to FILTER_OFF,
        Keys.FILTER_PACKAGES to "",
        Keys.INDICATOR_RULES to "",
        Keys.THEME_MODE to THEME_SYSTEM,
        Keys.HOOK_COUNT to "",
        Keys.HOOK_TIME to "",
        Keys.SB_MODE to SB_MODE_OFF,
        Keys.SB_RULES to "",
        Keys.SB_DEFAULT_MASK to "0",
        Keys.SB_NIGHT_START to "23:00",
        Keys.SB_NIGHT_END to "07:00",
        Keys.SB_TRIGGER_MASK to "3",
    )

    const val FILTER_OFF = "off"
    const val FILTER_BLACKLIST = "blacklist"
    const val FILTER_WHITELIST = "whitelist"
    const val FILTER_PER_APP = "per_app"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    // 状态栏管控：生效范围模式。
    const val SB_MODE_OFF = "off"
    const val SB_MODE_LISTED = "listed"
    const val SB_MODE_UNLISTED = "unlisted"
    const val SB_MODE_PER_APP = "per_app"

    // 状态栏管控：隐藏位。
    //
    // 左区 = MiuiPhoneStatusBarView.mStatusBarLeftContainer（时钟 + 通知图标 + 活动胶囊），
    // 右区 = 同一个视图里的 mSystemIconArea（信号 / WiFi / 电量）。
    // 直接按 MIUI 自己的两个容器划分，而不是逐个隐藏子视图 —— 子视图会被 MIUI 的
    // updateCutoutLocation() 重新摆位，容器级 GONE 才稳定。
    // 刻意写字面量：const val 的初始化式里不能调用 or()（它是函数调用，
    // 不算编译期常量），写成 SB_LEFT or SB_RIGHT 会直接编译不过。
    const val SB_LEFT = 1
    const val SB_RIGHT = 2
    const val SB_BOTH = 3

    /**
     * 显式豁免：这个应用不隐藏。
     *
     * 与"没有规则"（[Keys.SB_RULES] 里没有这一行）必须区分开：
     * 没有规则表示"跟随外层默认"，而豁免表示"无论外层怎么设都不隐藏"。
     * 之前两者都用 0 表示，所以设置页的"默认"和"不隐藏"看起来一样，
     * 选完再进来又跳回"默认" —— 这就是单独开关那个 bug。
     */
    const val SB_NONE = -1

    // 与 StatusBarUtils.PRIVACY_TYPE = {1, 2, 4, 8} 的下标一致。
    const val TYPE_CAMERA = 0
    const val TYPE_MIC = 1
    const val TYPE_LOCATION = 2
    const val TYPE_MEDIA_PROJECTION = 3
    const val TYPE_COUNT = 4

    // —— 状态栏管控 ——

    /** 解析后的每应用隐藏位缓存。 */
    @Volatile
    private var cachedRulesRaw: String? = null

    @Volatile
    private var cachedRules: Map<String, Int> = emptyMap()

    /**
     * 解析 [Keys.SB_RULES]：每行 `包名|掩码`。
     *
     * 只在配置变化时重新解析一次，命中缓存时不做任何分配。
     */
    private fun rules(): Map<String, Int> {
        val raw = string(Keys.SB_RULES)
        if (raw != cachedRulesRaw) {
            val parsed = HashMap<String, Int>()
            for (line in raw.split('\n')) {
                val entry = line.trim()
                if (entry.isEmpty()) continue
                val separator = entry.lastIndexOf('|')
                if (separator <= 0) continue
                val name = entry.substring(0, separator).trim().lowercase()
                val mask = entry.substring(separator + 1).trim().toIntOrNull() ?: 0
                if (name.isNotEmpty()) parsed[name] = mask
            }
            cachedRulesRaw = raw
            cachedRules = parsed
        }
        return cachedRules
    }

    /**
     * 某个前台应用应隐藏的状态栏部分（不含触发条件）。
     *
     * 模式语义：
     * - [SB_MODE_LISTED]：只有列表内的应用按自己的规则隐藏，其余不处理；
     * - [SB_MODE_UNLISTED]：列表外的应用按 [Keys.SB_DEFAULT_MASK] 隐藏，
     *   列表内的应用按自己的规则（掩码 0 即显式豁免）；
     * - [SB_MODE_OFF]：所有应用统一按 [Keys.SB_DEFAULT_MASK]。
     */
    fun statusBarRuleMask(packageName: String?): Int {
        if (!boolean(Keys.SB_ENABLE)) return 0
        val defaultMask = (string(Keys.SB_DEFAULT_MASK).toIntOrNull() ?: 0) and SB_BOTH
        if (string(Keys.SB_MODE) != SB_MODE_PER_APP) return defaultMask
        val pkg = packageName?.trim()?.lowercase() ?: return 0
        val rule = rules()[pkg] ?: return 0
        return if (rule == SB_NONE) 0 else rule and SB_BOTH
    }

    /** 触发条件（夜间 / 横屏 / 锁屏）命中时叠加的隐藏位。 */
    fun statusBarTriggerMask(): Int =
        (string(Keys.SB_TRIGGER_MASK).toIntOrNull() ?: 0) and SB_BOTH

    /** Hook 进程缓存的只读 remote preferences。 */
    @Volatile
    private var remote: SharedPreferences? = null

    /** 解析后的包名列表缓存，避免每次事件都做字符串切分。 */
    @Volatile
    private var cachedMode: String? = null

    @Volatile
    private var cachedPackagesRaw: String? = null

    @Volatile
    private var cachedPackages: List<String> = emptyList()

    fun attachRemote(preferences: SharedPreferences?) {
        remote = preferences
    }

    fun registerRemoteListener(listener: SharedPreferences.OnSharedPreferenceChangeListener): Boolean {
        val preferences = remote ?: return false
        return runCatching {
            preferences.registerOnSharedPreferenceChangeListener(listener)
            true
        }.getOrDefault(false)
    }

    fun boolean(key: String): Boolean {
        val default = BOOLEAN_DEFAULTS[key] ?: false
        return runCatching { remote?.getBoolean(key, default) }.getOrNull() ?: default
    }

    fun string(key: String): String {
        val default = STRING_DEFAULTS[key] ?: ""
        return runCatching { remote?.getString(key, default) }.getOrNull() ?: default
    }

    /** UI 进程获取可写的 RemotePreferences；service 未就绪时返回 null。 */
    fun remotePreferencesForUi(service: XposedService?): SharedPreferences? =
        runCatching { service?.getRemotePreferences(GROUP) }.getOrNull()

    /**
     * Hook 侧写入诊断信息。
     *
     * 用 apply() 异步落盘，绝不阻塞 SystemUI 主线程；写失败只是 UI 少一条信息，
     * 不影响拦截本身。
     */
    fun recordHookInstall(count: Int) {
        val preferences = remote ?: return
        runCatching {
            preferences.edit()
                .putString(Keys.HOOK_COUNT, count.toString())
                .putString(Keys.HOOK_TIME, java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
                ).format(java.util.Date()))
                .apply()
        }
    }

    private fun indicatorRules(): Map<String, Int> = buildMap {
        string(Keys.INDICATOR_RULES).lineSequence().forEach { line ->
            val i = line.lastIndexOf('|')
            if (i > 0) line.substring(i + 1).trim().toIntOrNull()?.let {
                put(line.substring(0, i).trim().lowercase(), it and 15)
            }
        }
    }

    /** 按当前范围模式和包名单独判断该隐私类型。 */
    fun shouldHideType(typeIndex: Int, packageName: String? = null): Boolean {
        if (!boolean(Keys.MASTER_ENABLE) || typeIndex !in 0 until TYPE_COUNT) return false
        if (string(Keys.FILTER_MODE) == FILTER_PER_APP) {
            val pkg = packageName?.lowercase() ?: return false
            return (indicatorRules()[pkg]?.and(1 shl typeIndex) ?: 0) != 0
        }
        if (!packageAllowed(packageName)) return false
        return when (typeIndex) {
            TYPE_CAMERA -> boolean(Keys.HIDE_CAMERA)
            TYPE_MIC -> boolean(Keys.HIDE_MIC)
            TYPE_LOCATION -> boolean(Keys.HIDE_LOCATION)
            TYPE_MEDIA_PROJECTION -> boolean(Keys.HIDE_MEDIA_PROJECTION)
            else -> false
        }
    }

    /**
     * 依据应用包名和当前白/黑名单模式判断该条提示是否应被隐藏。
     * 返回 false 表示该应用被排除，不做隐藏处理。
     *
     * 列表只在配置变化时重新解析，命中缓存时不做任何分配。
     */
    fun packageAllowed(packageName: String?): Boolean {
        val mode = string(Keys.FILTER_MODE)
        if (mode == FILTER_OFF || mode == FILTER_PER_APP) return true
        val name = packageName ?: return true

        val raw = string(Keys.FILTER_PACKAGES)
        if (raw != cachedPackagesRaw || mode != cachedMode) {
            cachedMode = mode
            cachedPackagesRaw = raw
            cachedPackages = raw
                .split('\n', ',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        val entries = cachedPackages
        if (entries.isEmpty()) return true

        val listed = entries.any { it.equals(name, ignoreCase = true) }
        return when (mode) {
            FILTER_BLACKLIST -> !listed
            FILTER_WHITELIST -> listed
            else -> true
        }
    }
}
