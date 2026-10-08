package com.hyperstatusbar.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperstatusbar.PrivacyDotApp
import com.hyperstatusbar.R
import com.hyperstatusbar.core.LogSnapshot
import com.hyperstatusbar.core.Prefs
import com.hyperstatusbar.ui.ksu.FloatingBottomBar
import com.hyperstatusbar.ui.ksu.CheckCircleOutlineIcon
import com.hyperstatusbar.ui.ksu.FloatingBottomBarItem
import com.hyperstatusbar.ui.ksu.rememberMainPagerState
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical
/** 页面顺序：状态 / 指示器 / 应用 / 设置 */
private const val PAGE_STATUS = 0
private const val PAGE_PRIVACY = 1
private const val PAGE_STATUS_BAR = 2
private const val PAGE_SETTINGS = 3
private const val PAGE_COUNT = 4

/**
 * 二级页面路由。
 *
 * 页面切换用 Miuix 自带的导航组件（KernelSU 用的是同一套）：
 * NavDisplay 渲染返回栈，NavBackStack 负责状态保存，过渡动画、预测性返回、
 * 边滑返回都由它内部实现，不再自己写 graphicsLayer 位移。
 *
 * 需要 @Serializable 的原因：rememberNavBackStack 会把返回栈存进 Bundle，
 * 这样转屏、进程被回收后还能回到原来的二级页。
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object IndicatorTypes : Route
    @Serializable data object IndicatorApps : Route
    @Serializable data class IndicatorAppDetail(val packageName: String) : Route
    @Serializable data object StatusBarApps : Route
    @Serializable data class StatusBarAppDetail(val packageName: String) : Route

}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PrivacyDotRoot() }
    }
}

/**
 * 配置快照：把 RemotePreferences 的读写收敛成可观察状态。
 *
 * Hook 进程与 UI 进程共享同一个 group，这里只负责本进程的展示与提交。
 */
private class PrefsState(private val local: SharedPreferences) {
    val bools = mutableStateMapOf<String, Boolean>()
    val strings = mutableStateMapOf<String, String>()

    /**
     * 远端偏好（LSPosed 的 RemotePreferences）。
     *
     * 它由框架异步送达，通常在启动后几百毫秒才可用。
     * 所以这里做成"可后挂载的引用"而不是构造参数：
     * 如果把它当 remember 的 key，binder 一到就会重建整个 PrefsState，
     * 连带 themeMode 回默认值、ThemeController 重建 —— 界面上就是主题闪一下。
     */
    @Volatile
    private var remote: SharedPreferences? = null

    init {
        reload()
    }

    /** 远端就绪/失效时调用：换引用并重读，对象本身保持同一个。 */
    fun attachRemote(preferences: SharedPreferences?) {
        remote = preferences
        reload()
        publishToRemote()
    }

    /**
     * 读取顺序：RemotePreferences -> 本地兜底 -> 代码默认值。
     *
     * 本地兜底的意义：外观类设置（主题、底栏样式）只影响本应用，
     * LSPosed 未连接时也应该能用；连接后远端值优先，保持与 Hook 一致。
     */
    fun reload() {
        Prefs.BOOLEAN_DEFAULTS.forEach { (key, default) ->
            bools[key] = readBool(key, default)
        }
        Prefs.STRING_DEFAULTS.forEach { (key, default) ->
            strings[key] = readString(key, default)
        }
    }

    private fun readBool(key: String, default: Boolean): Boolean =
        if (key in Prefs.REMOTE_ONLY_KEYS) {
            remote?.getBoolean(key, default) ?: local.getBoolean(key, default)
        } else {
            local.getBoolean(key, default)
        }

    private fun readString(key: String, default: String): String =
        if (key in Prefs.REMOTE_ONLY_KEYS) {
            remote?.getString(key, default) ?: local.getString(key, default) ?: default
        } else {
            local.getString(key, default) ?: default
        }

    fun bool(key: String): Boolean = bools[key] ?: (Prefs.BOOLEAN_DEFAULTS[key] ?: false)

    fun string(key: String): String = strings[key] ?: (Prefs.STRING_DEFAULTS[key] ?: "")

    /**
     * 把本地值推回远端。
     *
     * 本应用是这些键的唯一写入方，本地才是权威副本；远端只是给 SystemUI
     * 里的 Hook 看的镜像。首次连上 LSPosed 或远端被清空时用它对齐一次，
     * 否则 Hook 会一直读到旧值。
     */
    fun publishToRemote() {
        val target = remote ?: return
        runCatching {
            val editor = target.edit()
            bools.forEach { (key, value) ->
                if (key !in Prefs.REMOTE_ONLY_KEYS) editor.putBoolean(key, value)
            }
            strings.forEach { (key, value) ->
                if (key !in Prefs.REMOTE_ONLY_KEYS) editor.putString(key, value)
            }
            editor.apply()
        }
    }

    /**
     * 写入顺序：先落本地（立刻可见），需要跨进程的再写远端。
     *
     * 本地写是同步生效的兜底，远端写失败也不影响本应用界面，
     * 因此不需要把两者做成事务。
     */
    fun setBool(key: String, value: Boolean) {
        bools[key] = value
        local.edit().putBoolean(key, value).apply()
        if (key !in Prefs.REMOTE_ONLY_KEYS) {
            remote?.edit()?.putBoolean(key, value)?.apply()
        }
    }

    fun setString(key: String, value: String) {
        strings[key] = value
        local.edit().putString(key, value).apply()
        if (key !in Prefs.REMOTE_ONLY_KEYS) {
            remote?.edit()?.putString(key, value)?.apply()
        }
    }

    /** 按包名读写指示器类型掩码，0/ null 表示删除规则。 */
    fun setIntRule(key: String, packageName: String, mask: Int?) {
        val entries = string(key).lineSequence().map(String::trim).filter(String::isNotEmpty)
            .filterNot { line -> val i=line.lastIndexOf('|'); (if(i>0) line.substring(0,i).trim() else line).equals(packageName,true) }
            .toMutableList()
        if (mask != null && mask != 0) entries.add("$packageName|$mask")
        setString(key, entries.joinToString("\n"))
    }

    /** 读改写一个用换行分隔的包名列表；返回值仅用于本地回显。 */
    fun togglePackage(key: String, packageName: String, inList: Boolean) {
        val current = string(key)
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        current.removeAll { it.equals(packageName, ignoreCase = true) }
        if (inList) current.add(packageName)
        setString(key, current.joinToString("\n"))
    }

    /**
     * 读改写状态栏规则串（每行 `包名|掩码`）。
     *
     * mask 传 null 表示「默认」：直接删掉这一行，让这个应用回到外层规则的管辖。
     * 传 [Prefs.SB_NONE] 则是「不隐藏」的显式豁免，会写成 `包名|-1` 留在表里，
     * 这样两个状态在界面上才能区分开。
     */
    fun setRule(key: String, packageName: String, mask: Int?) {
        val entries = string(key).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .filterNot { entry ->
                val separator = entry.lastIndexOf('|')
                val name = if (separator > 0) entry.substring(0, separator).trim() else entry
                name.equals(packageName, ignoreCase = true)
            }.toMutableList()
        // 列表成员必须有可识别的显式记录；掩码 0 表示在白名单中走默认隐藏区。
        entries.add("$packageName|${mask ?: 0}")
        setString(key, entries.joinToString("\n"))
    }

    fun removeRule(key: String, packageName: String) {
        val entries = string(key).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .filterNot { entry ->
                val separator = entry.lastIndexOf('|')
                val name = if (separator > 0) entry.substring(0, separator).trim() else entry
                name.equals(packageName, ignoreCase = true)
            }
        setString(key, entries.joinToString("\n"))
    }
}

@Composable
private fun PrivacyDotRoot() {
    val context = LocalContext.current
    val localPreferences = remember {
        context.getSharedPreferences(Prefs.LOCAL_GROUP, Context.MODE_PRIVATE)
    }

    /*
     * 首帧的初值取"上次是否连上过"，而不是 false。
     *
     * LSPosed 的 binder 是异步送到的，冷启动首帧必然拿不到服务；
     * 如果按 false 渲染，就会先闪一下"未激活/未连接"再跳正确状态。
     * 模块是否生效很少变化，用上次结果做初值几乎总是对的，
     * 真的变了也会在 binder 到达后立刻纠正。
     */
    var serviceReady by remember {
        mutableStateOf(
            PrivacyDotApp.service != null ||
                localPreferences.getBoolean(Prefs.LOCAL_LAST_SERVICE_READY, false),
        )
    }
    // 只以本地偏好为 key。远端是异步到的，若把它也作为 key，
    // binder 一到就会重建 PrefsState 并连带重建 ThemeController —— 界面闪一下。
    val state = remember(localPreferences) { PrefsState(localPreferences) }

    DisposableEffect(Unit) {
        val listener = object : PrivacyDotApp.ServiceStateListener {
            override fun onServiceStateChanged(service: io.github.libxposed.service.XposedService?) {
                serviceReady = service != null
                localPreferences.edit()
                    .putBoolean(Prefs.LOCAL_LAST_SERVICE_READY, service != null)
                    .apply()
                // 只换引用，不重建对象。
                state.attachRemote(Prefs.remotePreferencesForUi(service))
            }
        }
        PrivacyDotApp.addServiceListener(listener)
        onDispose { PrivacyDotApp.removeServiceListener(listener) }
    }

    DisposableEffect(localPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.reload() }
        localPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { localPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // colorSchemeMode 是只读状态，主题输入变化时必须重建 controller。
    val monet = state.bool(Prefs.Keys.MONET)
    val themeMode = state.string(Prefs.Keys.THEME_MODE)
    val mode = remember(monet, themeMode) {
        when {
            monet && themeMode == Prefs.THEME_LIGHT -> ColorSchemeMode.MonetLight
            monet && themeMode == Prefs.THEME_DARK -> ColorSchemeMode.MonetDark
            monet -> ColorSchemeMode.MonetSystem
            themeMode == Prefs.THEME_LIGHT -> ColorSchemeMode.Light
            themeMode == Prefs.THEME_DARK -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }

    MiuixTheme(controller = controller) {
        PrivacyDotShell(state = state, serviceReady = serviceReady)
    }
}

@Composable
private fun PrivacyDotShell(
    state: PrefsState,
    serviceReady: Boolean,
) {
    /*
     * 导航结构对齐 KernelSU：
     *   Scaffold（唯一一层，只作为 popup / dialog 的宿主）
     *     └─ NavDisplay（返回栈渲染 + 过渡 + 预测性返回）
     *          ├─ Route.Main            一级页：底栏 + HorizontalPager
     *          ├─ Route.IndicatorTypes   二级页
     *
     * 二级页是返回栈上的新条目，整屏盖在一级页之上；一级页留在下面继续组合，
     * 所以拖动返回时看到的是真正的一级页，而不是之前那种空白。
     *
     * 「预测性返回手势」开关直接映射成 NavSwipeDirection：
     *   LeftToRight —— 打开：右滑返回并跟手；
     *   None        —— 关闭：不能用边滑手势划走二级页，只能按返回键 / 系统返回。
     */
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val predictiveBack = state.bool(Prefs.Keys.PREDICTIVE_BACK)
    val swipeDismiss = if (predictiveBack) {
        NavSwipeDirection.LeftToRight
    } else {
        NavSwipeDirection.None
    }
    val push: (Route) -> Unit = { route -> if (route !in backStack) backStack.add(route) }
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    val canPop = backStack.size > 1

    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
            onBack = pop,
        ) {
            // 根条目没有可返回的上一级，手势必须关掉，否则会把整屏往右拖。
            entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) {
                MainScreen(
                    state = state,
                    serviceReady = serviceReady,
                    onOpenIndicatorTypes = { push(Route.IndicatorTypes) },
                    onOpenIndicatorApps = { push(Route.IndicatorApps) },
                    onOpenStatusBarApps = { push(Route.StatusBarApps) },
                )
            }
            entry<Route.IndicatorTypes>(swipeDismiss = swipeDismiss) {
                DetailPage(
                    title = stringResource(R.string.indicator_types_entry),
                    onBack = pop,
                ) {
                    item { SmallTitle(text = stringResource(R.string.section_types)) }
                    item { SectionCard { IndicatorTypeSwitches(state) } }
                }
            }
            entry<Route.IndicatorApps>(swipeDismiss = swipeDismiss) { AppListPage("应用范围", state, { if(state.string(Prefs.Keys.FILTER_MODE)==Prefs.FILTER_PER_APP) indicatorRuleOf(state.string(Prefs.Keys.INDICATOR_RULES),it)!=null else indicatorPackages(state).contains(it) }, { push(Route.IndicatorAppDetail(it)) }, pop) }
            entry<Route.IndicatorAppDetail>(swipeDismiss = swipeDismiss) { route -> IndicatorAppDetailPage(state, route.packageName, pop) }
            entry<Route.StatusBarApps>(swipeDismiss = swipeDismiss) { AppListPage("应用规则", state, { sbRuleOf(state.string(Prefs.Keys.SB_RULES), it) != null }, { push(Route.StatusBarAppDetail(it)) }, pop) }
            entry<Route.StatusBarAppDetail>(swipeDismiss = swipeDismiss) { route -> StatusBarAppDetailPage(state, route.packageName, pop) }

        }
        /*
         * 关闭「预测性返回手势」时，靠 NavSwipeDirection.None 只能关掉 NavDisplay 自己的
         * 边缘滑动识别器，系统级预测性返回仍然直接接到 NavDisplay 内部，手势照样跟手。
         *
         * 所以再挂一个自己的返回处理器：navigationevent 的仲裁是「后组合且启用者优先」，
         * 它写在 NavDisplay 之后，就会先拿到手势。这里只消费、不跟进进度，
         * 于是拖动过程中界面不动，松手后才返回上一级 —— 等价于普通返回。
         *
         * 打开开关时这个处理器不注册，交回给 NavDisplay 的跟手效果。
         */
        if (!predictiveBack) {
            val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
            NavigationBackHandler(
                state = navEventState,
                isBackEnabled = canPop,
                onBackCompleted = pop,
            )
        }
    }
}

/**
 * 一级页：顶栏 + HorizontalPager + 底栏。
 *
 * 这里不再用 Scaffold：Scaffold 的 topBar / bottomBar 槽位只是替我们算内边距，
 * 而底栏要在二级页打开时被盖住、顶栏要自己控制层级，直接用一个 Box 摆位更直观，
 * 也顺带消掉了「Scaffold 套 Scaffold」。
 * 顶栏和底栏是浮在内容之上的两层，所以必须画在采样图层之外（之前那次爆栈就是这样来的）。
 */
@Composable
private fun MainScreen(
    state: PrefsState,
    serviceReady: Boolean,
    onOpenIndicatorTypes: () -> Unit,
    onOpenIndicatorApps: () -> Unit,
    onOpenStatusBarApps: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    // 底栏与 pager 的共享状态，负责让"点切换"和"手动滑动"两条路径不打架。
    val mainPagerState = rememberMainPagerState(pagerState)

    // 层级照搬 KernelSU 的 MainScreen：
    //   blurBackdrop —— 整屏采样源，给顶栏和通栏底栏做磨砂；
    //   backdrop     —— 只圈住 HorizontalPager，给悬浮胶囊做折射采样。
    // 两者分开是关键：胶囊要采样的只有页面内容，若与整屏共用一个，
    // 胶囊自身的绘制会被录进采样源，形成自引用（也就是之前那次爆栈）。
    val glass = state.bool(Prefs.Keys.LIQUID_GLASS) && isRuntimeShaderSupported()
    val floating = state.bool(Prefs.Keys.FLOATING_BAR)
    val surfaceColor = MiuixTheme.colorScheme.surface

    val blurBackdrop = if (glass) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp }

    val items = listOf(
        NavigationItem(stringResource(R.string.tab_home), MiuixIcons.Demibold.Info),
        NavigationItem(stringResource(R.string.tab_privacy), MiuixIcons.Demibold.Hide),
        NavigationItem(stringResource(R.string.tab_status_bar), MiuixIcons.Demibold.WorldClock),
        NavigationItem(stringResource(R.string.tab_settings), MiuixIcons.Demibold.Settings),
    )
    // 手动滑动时把 currentPage 同步给底栏（点切换期间由 isNavigating 挡住）。
    LaunchedEffect(pagerState.currentPage) { mainPagerState.syncPage() }
    val tab = mainPagerState.selectedPage
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 一级 Tab 的大标题与全部列表内容从顶栏下方直接开始：不再为固定顶栏额外
    // 预留 SmallTopAppBar 高度，整体上移约 60dp；右上角操作按钮仍悬在内容上方。
    val topSpace = statusBarTop + 8.dp

    // 底栏高度实测得来：悬浮和通栏两种形态高度不同，写死会在切换样式时错位。
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf(64.dp) }

    val pageStates = remember { List(PAGE_COUNT) { LazyListState() } }
    val topTitleAlpha by remember(tab, pageStates[tab], density) {
        derivedStateOf {
            val listState = pageStates[tab]
            val offsetDp = if (listState.firstVisibleItemIndex > 0) 240f else with(density) { listState.firstVisibleItemScrollOffset.toDp().value }
            ((offsetDp - 160f) / 80f).coerceIn(0f, 1f)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = if (blurBackdrop != null) {
                Modifier.layerBackdrop(blurBackdrop)
            } else {
                Modifier
            },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (floating && glass) Modifier.layerBackdrop(backdrop) else Modifier),
            ) { page ->
                when (page) {
                    PAGE_STATUS -> StatusPage(
                        listState = pageStates[PAGE_STATUS],
                        state = state,
                        serviceReady = serviceReady,
                        bottomSpace = bottomBarHeight,
                        topSpace = topSpace,
                    )
                    PAGE_PRIVACY -> PrivacyPage(
                        listState = pageStates[PAGE_PRIVACY],
                        state = state,
                        serviceReady = serviceReady,
                        bottomSpace = bottomBarHeight,
                        topSpace = topSpace,
                        onOpenTypes = onOpenIndicatorTypes,
                        onOpenApps = onOpenIndicatorApps,
                    )
                    PAGE_STATUS_BAR -> StatusBarOverviewPage(
                        listState = pageStates[PAGE_STATUS_BAR],
                        state = state,
                        bottomSpace = bottomBarHeight,
                        topSpace = topSpace,
                        onOpenApps = onOpenStatusBarApps,
                    )
                    else -> SettingsPage(
                        listState = pageStates[PAGE_SETTINGS],
                        state = state,
                        serviceReady = serviceReady,
                        context = context,
                        bottomSpace = bottomBarHeight,
                        topSpace = topSpace,
                    )
                }
            }
        }

        TopBar(
            backdrop = blurBackdrop,
            glass = glass,
            title = stringResource(listOf(R.string.tab_device, R.string.tab_privacy, R.string.tab_status_bar, R.string.tab_settings)[tab]),
            titleAlpha = topTitleAlpha,
            actions = { RestartAction() },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
        ) {
            BottomBar(
                items = items,
                selectedIndex = tab,
                onSelected = { index -> mainPagerState.animateToPage(index) },
                backdrop = backdrop,
                blurBackdrop = blurBackdrop,
                floating = floating,
                glass = glass,
                bottomPadding = bottomPadding,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 二级页外壳：不透明背景 + 顶栏（带返回箭头）+ 列表。
 *
 * 背景色必须显式画成 surface：二级页整屏盖在一级页之上，不画背景的话
 * 底下一级页的列表会透上来，看起来就是"只显示一半"。
 * 底部留白只算导航条：二级页没有底栏。
 */
@Composable
private fun DetailPage(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topSpace = statusBarTop + TopAppBarDefaults.SmallTopAppBarCenterHeight +
        TopAppBarDefaults.SubtitleBottomPadding

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        PageColumn(topSpace = topSpace, bottomSpace = bottomInset + 8.dp, content = content)
        TopBar(
            backdrop = null,
            glass = false,
            title = title,
            onBack = onBack,
            actions = { RestartAction() },
        )
    }
}

/** 顶栏：与 KernelSU 的 TopBar 一致，整条矩形磨砂。 */
@Composable
private fun TopBar(
    backdrop: LayerBackdrop?,
    glass: Boolean,
    title: String,
    titleAlpha: Float = 1f,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val active = glass && backdrop != null
    val barColor = if (active) Color.Transparent else MiuixTheme.colorScheme.surface.copy(alpha = titleAlpha)
    Box(
        modifier = if (active) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        SmallTopAppBar(
            title = title,
            titleColor = MiuixTheme.colorScheme.onBackground.copy(alpha = titleAlpha),
            color = barColor,
            navigationIcon = { if (onBack != null) BackButton(onClick = onBack) },
            actions = actions,
            scrollBehavior = MiuixScrollBehavior(),
        )
    }
}

/** 二级页顶栏的返回箭头：与 KernelSU 的二级页一致，用 Miuix 的 IconButton + Back 图标。 */
@Composable
private fun BackButton(onClick: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(onClick = onClick) {
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            imageVector = MiuixIcons.Back,
            contentDescription = stringResource(R.string.back),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 顶栏右上角的「重启系统界面」。
 *
 * 每个页面都在顶栏放同一个入口：状态栏、导航栏的改动本来就要等 SystemUI 重启才生效，
 * 把它固定在最右侧，比每次进设置页找开关顺手。
 * 点了先弹确认框 —— 误触会直接让状态栏消失几秒，值得拦一道。
 */
@Composable
private fun RestartAction() {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

    IconButton(onClick = { showConfirm = true }) {
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = stringResource(R.string.restart_systemui),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }

    OverlayDialog(
        show = showConfirm,
        title = stringResource(R.string.restart_systemui),
        summary = stringResource(R.string.restart_systemui_confirm),
        onDismissRequest = { showConfirm = false },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showConfirm = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    showConfirm = false
                    restartSystemUi(context)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * 底栏：对齐 KernelSU 的 BottomBar 两种形态。
 *
 * 通栏态 = 矩形磨砂 + NavigationBar；
 * 悬浮态交给从 KernelSU 原样移植的 FloatingBottomBar，
 * 尺寸、弹性、折射参数全部使用它的原值，不做二次调整。
 */
@Composable
private fun BottomBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
    blurBackdrop: LayerBackdrop?,
    floating: Boolean,
    glass: Boolean,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (!floating) {
        val active = glass && blurBackdrop != null
        Box(
            modifier = if (active) {
                Modifier.textureBlur(
                    backdrop = blurBackdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                        ),
                    ),
                )
            } else {
                Modifier
            },
        ) {
            NavigationBar(
                modifier = modifier,
                color = if (active) Color.Transparent else MiuixTheme.colorScheme.surface,
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        icon = item.icon,
                        label = item.label,
                        selected = selectedIndex == index,
                        onClick = { onSelected(index) },
                    )
                }
            }
        }
        return
    }

    FloatingBottomBar(
        modifier = modifier.padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        backdrop = backdrop,
        tabsCount = items.size,
        isBlurEnabled = glass,
    ) { activateTab ->
        items.forEachIndexed { index, item ->
            FloatingBottomBarItem(
                selected = selectedIndex == index,
                onClick = { activateTab(index) },
                // 4 个 tab 时按需收紧最小宽度，按数量收紧最小宽度。
                modifier = Modifier.defaultMinSize(minWidth = 76.dp),
            ) {
                Icon(imageVector = item.icon, contentDescription = item.label)
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}
@Composable
private fun PageColumn(
    topSpace: Dp,
    bottomSpace: Dp,
    title: String? = null,
    listState: LazyListState = rememberLazyListState(),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scrollDp by remember(listState, density) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) Float.POSITIVE_INFINITY
            else with(density) { listState.firstVisibleItemScrollOffset.toDp().value }
        }
    }
    val titleAlpha = (1f - ((scrollDp - 160f) / 80f)).coerceIn(0f, 1f)
    val pageContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        if (title != null) item(key = "large-page-title") {
            Text(text = title, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 36.dp, end = 20.dp, bottom = 20.dp).graphicsLayer { alpha = titleAlpha }, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onBackground)
        }
        content()
    }
    val contentPadding = PaddingValues(top = topSpace + 5.dp, bottom = bottomSpace + 12.dp)
    if (onRefresh == null) {
        LazyColumn(modifier = Modifier.fillMaxSize().overScrollVertical(), state = listState, contentPadding = contentPadding, overscrollEffect = null, content = pageContent)
    } else {
        PullToRefresh(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, color = MiuixTheme.colorScheme.primary, refreshTexts = listOf(stringResource(R.string.refresh_pull), stringResource(R.string.refresh_release), stringResource(R.string.refresh_doing), stringResource(R.string.refresh_done)), refreshTextStyle = MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.primary)) {
            LazyColumn(modifier = Modifier.fillMaxSize().overScrollVertical(), state = listState, contentPadding = contentPadding, overscrollEffect = null, content = pageContent)
        }
    }
}

@Composable
private fun StatusPage(
    listState: LazyListState,
    state: PrefsState,
    serviceReady: Boolean,
    bottomSpace: Dp,
    topSpace: Dp,
) {
    val context = LocalContext.current
    val rootVersion = remember(context) { readRootManagerVersion(context) }
    val storageInfo = remember(context) {
        runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.totalBytes - stat.availableBytes to stat.totalBytes
        }.getOrNull()
    }
    val hookCount = state.string(Prefs.Keys.HOOK_COUNT)
    val masterOn = state.bool(Prefs.Keys.MASTER_ENABLE)
    val active = serviceReady && masterOn

    PageColumn(title = stringResource(R.string.tab_device), listState = listState, topSpace = topSpace, bottomSpace = bottomSpace) {
        item {
            StatusCard(
                active = active,
                serviceReady = serviceReady,
                masterOn = masterOn,
                hookReady = hookCount.isNotBlank() && hookCount != "0",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        item {
            SectionCard {
                BasicComponent(
                    title = stringResource(R.string.device_model),
                    summary = "${Build.MANUFACTURER} ${Build.MODEL}",
                )
                BasicComponent(
                    title = stringResource(R.string.android_version),
                    summary = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                )
                BasicComponent(
                    title = stringResource(R.string.cpu_arch),
                    summary = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                )
                BasicComponent(
                    title = stringResource(R.string.root_manager),
                    summary = rootVersion ?: stringResource(R.string.value_unknown),
                )
                BasicComponent(
                    title = stringResource(R.string.storage_info),
                    summary = storageInfo?.let {
                        "${formatBytes(it.first)} / ${formatBytes(it.second)}"
                    } ?: stringResource(R.string.value_unknown),
                )
            }
        }
    }
}

/**
 * 状态卡：与 KernelSU 的「工作中」卡片同构。
 *
 * 结构（自上而下）：粗体标题 / 绿色副标题（版本号）/ 次级说明行，
 * 右侧一个大的绿色对勾。浅绿底、深色文字，深色模式下换成同色系的暗绿，
 * 避免浅绿在暗色主题里刺眼。
 *
 * 全部用 Miuix 现成素材拼：Card + Text + MiuixIcons，没有任何自绘图形。
 *//**
 * 状态卡：对齐 KernelSU 的 HomeMiuix.StatusCard。
 *
 * 勾号素材就是 KSU 用的那个：Material Rounded 的 CheckCircleOutline，
 * 靠 align(BottomEnd) + offset 探出卡片右下角，超出部分被卡片裁掉，
 * 视觉上是一个"露出角落的大圆环"。
 *
 * 注意不复刻 KSU 的三层 fillMaxSize Box：它的卡片高度由外层
 * Row(IntrinsicSize.Min) 撑开，我这里没有那层，fillMaxSize 会退化成
 * 各自的内容尺寸，三行文字就会叠在左上角。改成 Box 内直接 align 定位。
 */
@Composable
private fun StatusCard(
    active: Boolean,
    serviceReady: Boolean,
    masterOn: Boolean,
    hookReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val container = when {
        !active -> MiuixTheme.colorScheme.surfaceContainer
        isDark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val onContainer = when {
        !active -> MiuixTheme.colorScheme.onSurfaceContainer
        isDark -> Color(0xFFEAF8ED)
        else -> Color(0xFF0A1F10)
    }
    val checkTint = when {
        !active -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        isDark -> Color(0xFF5FD98A)
        else -> Color(0xFF36D167)
    }

    val title = when {
        !serviceReady -> stringResource(R.string.status_service_missing_title)
        !masterOn -> stringResource(R.string.status_disabled)
        else -> stringResource(R.string.status_active)
    }
    val version = when {
        !serviceReady -> stringResource(R.string.status_service_missing)
        !masterOn -> stringResource(R.string.status_disabled_summary)
        else -> stringResource(R.string.status_version)
    }
    val mode = when {
        !active -> null
        hookReady -> stringResource(R.string.status_hook_ready)
        else -> stringResource(R.string.status_hook_pending)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
    ) {
        /*
         * 这个 Box 必须 fillMaxWidth。
         *
         * 不加的话它的宽度由文字撑开（约 229dp），而卡片实际有 370dp，
         * align(BottomEnd) 就会对齐到这个窄盒子的右边 —— 勾号落在卡片中间，
         * 之前调 offset 也只是在窄盒子里挪一点，所以"移动了一点点"。
         *
         * clip 同样必要：Compose 的 Box 默认不裁剪，勾号超出卡片的部分
         * 会画到卡片外面去。
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardDefaults.CornerRadius)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, bottom = 10.dp, end = 96.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(1.dp))
                Text(text = version, fontSize = 15.sp)
                if (mode != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(text = mode, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            /*
             * 勾号贴住卡片右边缘：圆环有一小段探出卡片被裁掉，对勾完整保留。
             *
             * viewBox 24 里，圆环占 x=2..22，对勾占 x=6..18、y=7.58..17。
             * 图标尺寸 S、右移 dx 时，卡片能显示到 viewBox 的 24 − 24·dx/S 处；
             * 对勾右端要留在卡内：
             *     24 − 24·dx/S ≥ 18   →   dx ≤ S/4
             * 这里 S = 108dp、dx = 24dp（约 S/4.5）：对勾右端距卡片边缘约 3dp，
             * 圆环则被裁掉约 15dp，即"贴边、只露一部分"。
             * 注意这个比例不随 S 变：dx = S/4 时圆环恒定被裁掉直径的 20%
             * （对勾伸到圆环直径的 80% 处），所以"露得更少"和"对勾完整"
             * 在几何上互斥，最多只能裁到 20%。
             *
             * y = 32dp 是"对勾完整"这条约束下的上限，已经是最后一次可加的量。
             *
             * 卡片内容高 H ≈ 111dp，图标垂直居中时中心在 H/2 + y 处；
             * 对勾下缘相对中心 = (17 − 12) / 24 × S = 0.208S：
             *     H/2 + y + 0.208S ≤ H   →   y ≤ H/2 − 0.208S = 55.5 − 22.5 ≈ 33dp
             * 取 32dp，对勾下缘离卡片底边只剩约 1dp。
             * 圆环此时上、下两段都被裁掉，只在中部偏右留一段弧。
             * 想再往下只能接受切到对勾，或者换一个"对勾更靠圆心上方"的图标。
             */
            Icon(
                imageVector = CheckCircleOutlineIcon,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 24.dp, y = 32.dp)
                    .size(108.dp),
            )
        }
    }
}

@Composable
private fun PrivacyPage(listState: LazyListState, state: PrefsState, serviceReady: Boolean, bottomSpace: Dp, topSpace: Dp, onOpenTypes: () -> Unit, onOpenApps: () -> Unit) {
 val mode=state.string(Prefs.Keys.FILTER_MODE)
 val hint=when(mode){Prefs.FILTER_WHITELIST->stringResource(R.string.filter_mode_hint_whitelist);Prefs.FILTER_BLACKLIST->stringResource(R.string.filter_mode_hint_blacklist);Prefs.FILTER_PER_APP->stringResource(R.string.filter_mode_hint_per_app);else->stringResource(R.string.filter_mode_hint_off)}
 val modes=listOf(stringResource(R.string.filter_off),stringResource(R.string.filter_whitelist),stringResource(R.string.filter_blacklist),stringResource(R.string.filter_per_app))
 PageColumn(title=stringResource(R.string.tab_privacy),listState=listState,topSpace=topSpace,bottomSpace=bottomSpace){
  item { SmallTitle(text=stringResource(R.string.section_types)); SectionCard {
   SwitchPreference(title=stringResource(R.string.master_enable),summary=stringResource(R.string.master_enable_summary),checked=state.bool(Prefs.Keys.MASTER_ENABLE),onCheckedChange={state.setBool(Prefs.Keys.MASTER_ENABLE,it)})
   if(state.bool(Prefs.Keys.MASTER_ENABLE)) ArrowPreference(title=stringResource(R.string.indicator_types_entry),summary=stringResource(R.string.indicator_types_entry_summary),onClick=onOpenTypes)
  } }
  item { SmallTitle(text=stringResource(R.string.section_indicator_apps)); SectionCard {
   OverlayDropdownPreference(title=stringResource(R.string.filter_mode),summary=hint,items=modes,selectedIndex=when(mode){Prefs.FILTER_WHITELIST->1;Prefs.FILTER_BLACKLIST->2;Prefs.FILTER_PER_APP->3;else->0},onSelectedIndexChange={i->state.setString(Prefs.Keys.FILTER_MODE,when(i){1->Prefs.FILTER_WHITELIST;2->Prefs.FILTER_BLACKLIST;3->Prefs.FILTER_PER_APP;else->Prefs.FILTER_OFF})})
   ArrowPreference(title="管理应用",summary="搜索应用并单独设置",onClick=onOpenApps)
  } }
 }
}

@Composable
private fun IndicatorTypeSwitches(state: PrefsState) {
    SwitchPreference(
        title = stringResource(R.string.privacy_camera),
        summary = stringResource(R.string.privacy_camera_summary),
        checked = state.bool(Prefs.Keys.HIDE_CAMERA),
        onCheckedChange = { state.setBool(Prefs.Keys.HIDE_CAMERA, it) },
    )
    SwitchPreference(
        title = stringResource(R.string.privacy_mic),
        summary = stringResource(R.string.privacy_mic_summary),
        checked = state.bool(Prefs.Keys.HIDE_MIC),
        onCheckedChange = { state.setBool(Prefs.Keys.HIDE_MIC, it) },
    )
    SwitchPreference(
        title = stringResource(R.string.privacy_location),
        summary = stringResource(R.string.privacy_location_summary),
        checked = state.bool(Prefs.Keys.HIDE_LOCATION),
        onCheckedChange = { state.setBool(Prefs.Keys.HIDE_LOCATION, it) },
    )
    SwitchPreference(
        title = stringResource(R.string.privacy_media),
        summary = stringResource(R.string.privacy_media_summary),
        checked = state.bool(Prefs.Keys.HIDE_MEDIA_PROJECTION),
        onCheckedChange = { state.setBool(Prefs.Keys.HIDE_MEDIA_PROJECTION, it) },
    )
}

/** 应用选择器里的一行数据。 */
private data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
) {
    /**
     * 预转小写的标签。
     *
     * 搜索是每敲一个字符就要过一遍全表，之前每次比较都调 label.lowercase()，
     * 200 个应用就是 200 次字符串分配。这里在构建列表时算一次。
     */
    val labelLower: String = label.lowercase()
}

/**
 * 应用页与状态栏页共用的应用列表状态。
 *
 * 两个页面之前各写了一份 AppCache + 搜索 + 下拉刷新，行为还不一致。
 * 收敛成一份，顺带把首帧、刷新、过滤都统一到同一套语义上。
 */
private class AppListState(
    val apps: List<AppEntry>,
    val refreshing: Boolean,
    val refresh: () -> Unit,
)

@Composable
private fun rememberAppList(): AppListState {
    val context = LocalContext.current

    // 首帧直接用缓存，命中时不会有任何加载态。
    var apps by remember { mutableStateOf(AppCache.peekApps()) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 只有"缓存还没建立"时才需要读一次；已经有了就永远是它。
    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { AppCache.apps(context) }
        }
    }

    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                apps = withContext(Dispatchers.IO) { AppCache.refresh(context) }
                refreshing = false
            }
        }
    }
    return AppListState(apps, refreshing, refresh)
}

/** 两页共用的过滤：先按系统应用开关，再按关键词匹配名称或包名，最后按名称排序。 */
private fun filterApps(
    apps: List<AppEntry>,
    query: String,
    showSystem: Boolean,
    marked: (String) -> Boolean = { false },
): List<AppEntry> {
    val q = query.trim().lowercase()
    return apps.asSequence()
        .filter { showSystem || !it.isSystem }
        .filter { q.isEmpty() || it.labelLower.contains(q) || it.packageName.contains(q) }
        // 已开启/已配置的应用始终置顶；同组内再按应用名称排序。
        .sortedWith(compareByDescending<AppEntry> { marked(it.packageName) }.thenBy { it.labelLower })
        .toList()
}

/**
 * 已安装应用列表的进程级缓存。
 *
 * 之前每次滑到「应用」页都会重新枚举一遍 PackageManager 并转圈等结果，
 * 这里把它做成只读一次的懒加载单例：
 * - 列表本身只在首次访问时枚举；
 * - 图标单独缓存，且只在 LazyColumn 真正组合到那一行时才解码，
 *   翻回上一页再回来不会重复解码。
 * 设备上装/卸载应用属于低频事件，不做失效监听，重启应用即刷新。
 */
private object AppCache {
    @Volatile
    private var apps: List<AppEntry>? = null

    private val iconCache = android.util.LruCache<String, ImageBitmap>(256)

    /** 不触发加载地看一眼缓存，用于把首帧直接渲染成已有数据。 */
    fun peekApps(): List<AppEntry> = apps ?: emptyList()

    /**
     * 只读一次。
     *
     * 之前是"缓存为空就重新枚举"，只要列表被系统回收（进程被清后台）就会重读，
     * 用户看到的就是每次进来都转一圈。这里把刷新收敛成显式动作：只有
     * [refresh]（用户下拉）会重新枚举，其余情况永远返回同一份数据。
     */
    fun apps(context: Context): List<AppEntry> {
        apps?.let { return it }
        return synchronized(this) {
            apps ?: loadInstalledApps(context.applicationContext).also { apps = it }
        }
    }

    /** 用户主动下拉时重新枚举，并丢弃图标缓存（图标可能已经变了）。 */
    fun refresh(context: Context): List<AppEntry> {
        val fresh = loadInstalledApps(context.applicationContext)
        synchronized(this) {
            apps = fresh
            iconCache.evictAll()
        }
        return fresh
    }

    /** 只看缓存、不解码。给组合阶段判断"要不要异步加载"用。 */
    fun peekIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    fun icon(context: Context, packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val bitmap = runCatching {
            loadIconBitmap(context.applicationContext, packageName)
        }.getOrNull() ?: return null
        iconCache.put(packageName, bitmap)
        return bitmap
    }
}

/**
 * 第三个页面：应用生效范围。
 *
 * 这里是【唯一】管理范围的地方：先在顶部选模式，再在列表里勾选应用。
 * 之前范围逻辑分散在「指示器」页（模式 + 手输包名）和「应用」页（勾选）
 * 两处，既重复又互相看不见对方的改动，表现就是"点了没反应"。
 *
 * 勾选语义按模式决定，界面上直接显示成"隐藏/不显示"，不再让用户自己换算。
 */
@Composable
private fun AppsPage(
    state: PrefsState,
    bottomSpace: Dp,
    topSpace: Dp,
) {
    val mode = state.string(Prefs.Keys.FILTER_MODE)
    val rawList = state.string(Prefs.Keys.FILTER_PACKAGES)
    val selected = remember(rawList) {
        rawList.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val appList = rememberAppList()
    val filtered = remember(appList.apps, query, showSystem, selected) {
        filterApps(appList.apps, query, showSystem) { selected.contains(it) }
    }

    val modes = listOf(
        stringResource(R.string.filter_off),
        stringResource(R.string.filter_whitelist),
        stringResource(R.string.filter_blacklist),
    )

    PageColumn(
        topSpace = topSpace,
        bottomSpace = bottomSpace,
        refreshing = appList.refreshing,
        onRefresh = appList.refresh,
    ) {
        item {
            SectionCard {
                OverlayDropdownPreference(
                    title = stringResource(R.string.filter_mode),
                    items = modes,
                    selectedIndex = when (mode) {
                        Prefs.FILTER_WHITELIST -> 1
                        Prefs.FILTER_BLACKLIST -> 2
                        else -> 0
                    },
                    onSelectedIndexChange = { index ->
                        state.setString(
                            Prefs.Keys.FILTER_MODE,
                            when (index) {
                                1 -> Prefs.FILTER_WHITELIST
                                2 -> Prefs.FILTER_BLACKLIST
                                else -> Prefs.FILTER_OFF
                            }
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.filter_mode),
                    summary = when (mode) {
                        Prefs.FILTER_WHITELIST -> stringResource(R.string.filter_mode_hint_whitelist)
                        Prefs.FILTER_BLACKLIST -> stringResource(R.string.filter_mode_hint_blacklist)
                        else -> stringResource(R.string.filter_mode_hint_off)
                    },
                    enabled = false,
                )
                SwitchPreference(
                    title = stringResource(R.string.show_system_apps),
                    summary = stringResource(R.string.show_system_apps_summary),
                    checked = showSystem,
                    onCheckedChange = { showSystem = it },
                )
            }
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                label = stringResource(R.string.search_apps),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
        }
        if (mode != Prefs.FILTER_OFF) {
            item {
                SmallTitle(text = stringResource(R.string.selected_count, selected.size))
            }
        }
        items(filtered, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                mode = mode,
                inList = selected.contains(app.packageName),
                enabled = mode != Prefs.FILTER_OFF,
                onToggle = { nowInList ->
                    state.togglePackage(Prefs.Keys.FILTER_PACKAGES, app.packageName, nowInList)
                },
            )
        }
        if (filtered.isEmpty()) {
            item {
                SectionCard {
                    BasicComponent(
                        title = stringResource(R.string.no_apps_found),
                        enabled = false,
                    )
                }
            }
        }
    }
}

/**
 * 应用图标 + "已配置"标记。
 *
 * 两个应用列表共用：指示器页标记在名单里的应用，状态栏页标记有单独规则的应用。
 * 标记做成图标右下角的小圆点而不是只染文字：行禁用时（总开关关闭）文字会统一变灰，
 * 圆点不受 enabled 影响，翻列表时一眼能看出哪些应用改过规则。
 */
@Composable
private fun AppIconMarked(packageName: String, marked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AppIcon(packageName = packageName, modifier = Modifier.size(40.dp))
        if (marked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(10.dp)
                    .background(MiuixTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/**
 * 一行应用。
 *
 * 开关位置表达"这个应用是否在名单里"，右侧文案表达最终效果：
 * 黑名单模式下勾上会显示"不隐藏"，不会让人以为设置反了。
 */
@Composable
private fun AppRow(
    app: AppEntry,
    mode: String,
    inList: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val effect = when (mode) {
        Prefs.FILTER_WHITELIST -> if (inList) {
            stringResource(R.string.effect_hidden)
        } else {
            stringResource(R.string.effect_shown)
        }

        Prefs.FILTER_BLACKLIST -> if (inList) {
            stringResource(R.string.effect_shown)
        } else {
            stringResource(R.string.effect_hidden)
        }

        else -> stringResource(R.string.effect_hidden)
    }

    BasicComponent(
        title = app.label,
        // 已配置（在名单里）的应用：标题用主色高亮。
        // disabledColor 保持默认 —— 禁用时全部行统一变灰，不用颜色区分状态。
        titleColor = if (inList) {
            BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary)
        } else {
            BasicComponentDefaults.titleColor()
        },
        summary = app.packageName,
        enabled = enabled,
        startAction = {
            AppIconMarked(packageName = app.packageName, marked = inList)
        },
        endActions = {
            if (enabled) {
                Text(
                    text = effect,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Switch(
                checked = inList,
                enabled = enabled,
                onCheckedChange = if (enabled) onToggle else null,
            )
        },
    )
}

/**
 * 按需解码应用图标。
 *
 * 故意不用 core-ktx 的 `Drawable.toBitmap()`：这里直接走 android.graphics，
 * 少一个依赖，也让"只有可见行才解码"这件事显式可控。
 * 解码失败（图标适配器抛异常、图标为 null）时退化成 1dp 的空白占位。
 */
/**
 * 按需加载应用图标。
 *
 * 之前是在 `remember` 里同步调 `AppCache.icon()`，而它内部要做一次
 * PackageManager 查询再把 Drawable 画进 Bitmap —— 全在主线程上，
 * 列表第一次滚动时掉帧很明显。
 *
 * 现在分两步：
 * - 组合阶段只查内存缓存（[AppCache.peekIcon]），命中就直接出图，零成本；
 * - 未命中才起一个 IO 协程去解码，期间先画占位。
 *
 * 这样一来"翻回上一页再回来"永远是命中路径，只有首次出现的新行才解码。
 */
@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cached = remember(packageName) { AppCache.peekIcon(packageName) }
    val bitmap by produceState(initialValue = cached, key1 = packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppCache.icon(context, packageName) }
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(modifier = modifier)
    }
}

private fun loadIconBitmap(context: Context, packageName: String): ImageBitmap? {
    val drawable = context.packageManager.getApplicationIcon(packageName)
    return drawableToBitmap(drawable)?.asImageBitmap()
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

/**
 * 一次性枚举已安装应用；调用方保证在 IO 线程。
 *
 * 只列有启动入口的应用（纯后台库/组件对用户没有意义）。
 *
 * 判断"有没有入口"用的是**一次** queryIntentActivities，而不是对每个包调一次
 * getLaunchIntentForPackage —— 后者是每个应用一次跨进程 PackageManager 查询，
 * 装 200 个应用就是 200 次 IPC，这是列表加载慢的主因。
 */
private fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager

    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = runCatching {
        pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toHashSet()
    }.getOrNull() ?: return emptyList()
    if (launchable.isEmpty()) return emptyList()

    val infos = runCatching {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    }.getOrNull() ?: return emptyList()

    val result = ArrayList<AppEntry>(launchable.size)
    for (info in infos) {
        val pkg = info.packageName ?: continue
        if (pkg !in launchable) continue
        val label = runCatching {
            pm.getApplicationLabel(info).toString()
        }.getOrNull() ?: pkg
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        result.add(AppEntry(pkg, label, isSystem))
    }
    return result
}

/**
 * 「状态栏」一级页：状态栏管控总览 + 全局规则 + 应用列表。
 *
 * 原 KernelSU 模块 StatusBar Auto Hide 的原生化版本。设计取舍：
 * - 隐藏左区 / 右区是"跟着前台应用走"的，所以放进应用列表逐项指定；
 * - 时钟秒、电池百分比是持久偏好，与前台应用无关，单列「显示项」（二级页）；
 * - 夜间时段 / 横屏 / 锁屏是全局触发，命中时叠加「触发时隐藏内容」（二级页）。
 */
@Composable
private fun StatusBarOverviewPage(listState: LazyListState, state: PrefsState, bottomSpace: Dp, topSpace: Dp, onOpenApps: () -> Unit) {
 val globalOptions=sbGlobalMaskLabels()
 // 三种范围与指示器页面完全统一：全部应用、白名单、黑名单。
 val modeOptions=listOf(stringResource(R.string.filter_off),stringResource(R.string.filter_per_app))
 PageColumn(title=stringResource(R.string.tab_status_bar),listState=listState,topSpace=topSpace,bottomSpace=bottomSpace){
  item { SmallTitle(text=stringResource(R.string.section_status_bar)); SectionCard {
   SwitchPreference(title=stringResource(R.string.sb_enable),summary=stringResource(R.string.sb_enable_summary),checked=state.bool(Prefs.Keys.SB_ENABLE),onCheckedChange={state.setBool(Prefs.Keys.SB_ENABLE,it)})
   OverlayDropdownPreference(title=stringResource(R.string.sb_mode),items=modeOptions,selectedIndex=if(state.string(Prefs.Keys.SB_MODE)==Prefs.SB_MODE_PER_APP) 1 else 0,onSelectedIndexChange={i->state.setString(Prefs.Keys.SB_MODE,if(i==1) Prefs.SB_MODE_PER_APP else Prefs.SB_MODE_OFF)})
   OverlayDropdownPreference(title=stringResource(R.string.sb_default_mask),summary=stringResource(R.string.sb_default_mask_summary),items=globalOptions,selectedIndex=sbGlobalMaskIndex(state.string(Prefs.Keys.SB_DEFAULT_MASK).toIntOrNull()?:0),enabled=state.string(Prefs.Keys.SB_MODE)!=Prefs.SB_MODE_PER_APP,onSelectedIndexChange={state.setString(Prefs.Keys.SB_DEFAULT_MASK,sbGlobalMaskFromIndex(it).toString())})
  } }
  item { SectionCard { ArrowPreference(title="管理应用规则",summary="为每个应用设置独立隐藏区域",onClick=onOpenApps) } }
 }
}

/** 每个应用的四档选项。 */
@Composable
private fun sbRuleLabels(): List<String> = listOf(
    stringResource(R.string.sb_rule_default),
    stringResource(R.string.sb_hide_left),
    stringResource(R.string.sb_hide_right),
    stringResource(R.string.sb_rule_none),
)

/** 全局隐藏内容（默认隐藏内容 / 触发时隐藏内容）的四档选项。 */
@Composable
private fun sbGlobalMaskLabels(): List<String> = listOf(
    stringResource(R.string.sb_hide_none),
    stringResource(R.string.sb_hide_left),
    stringResource(R.string.sb_hide_right),
    stringResource(R.string.sb_hide_both),
)

private fun sbRuleIndex(rule: Int?): Int = when (rule) {
    null -> 0
    Prefs.SB_LEFT -> 1
    Prefs.SB_RIGHT -> 2
    Prefs.SB_NONE -> 3
    else -> 0
}

private fun sbRuleFromIndex(index: Int): Int? = when (index) {
    1 -> Prefs.SB_LEFT
    2 -> Prefs.SB_RIGHT
    3 -> Prefs.SB_NONE
    else -> null
}

private fun sbGlobalMaskIndex(mask: Int): Int = when (mask and Prefs.SB_BOTH) {
    Prefs.SB_LEFT -> 1
    Prefs.SB_RIGHT -> 2
    Prefs.SB_BOTH -> 3
    else -> 0
}

private fun sbGlobalMaskFromIndex(index: Int): Int = when (index) {
    1 -> Prefs.SB_LEFT
    2 -> Prefs.SB_RIGHT
    3 -> Prefs.SB_BOTH
    else -> 0
}

/** 从 `包名|掩码` 规则串里取某个应用的规则；没有这一行返回 null（即「默认」）。 */
private fun sbRuleOf(raw: String, packageName: String): Int? {
    for (line in raw.split('\n')) {
        val entry = line.trim()
        if (entry.isEmpty()) continue
        val separator = entry.lastIndexOf('|')
        if (separator <= 0) continue
        if (entry.substring(0, separator).trim().equals(packageName, ignoreCase = true)) {
            return entry.substring(separator + 1).trim().toIntOrNull()
        }
    }
    return null
}

@Composable
private fun SettingsPage(
    listState: LazyListState,
    state: PrefsState,
    serviceReady: Boolean,
    context: Context,
    bottomSpace: Dp,
    topSpace: Dp,
) {
    PageColumn(title = stringResource(R.string.tab_settings), listState = listState, topSpace = topSpace, bottomSpace = bottomSpace) {
        item { ThemeSection(state) }
        item { LayoutSection(state) }
        item { ActionsSection(state, serviceReady, context) }
        item { LogSection(state, serviceReady, context) }
        item { AboutSection() }
    }
}

@Composable
private fun ThemeSection(state: PrefsState) {
    SmallTitle(text = stringResource(R.string.section_theme))
    SectionCard {
        val themes = listOf(
            stringResource(R.string.theme_system),
            stringResource(R.string.theme_light),
            stringResource(R.string.theme_dark),
        )
        val themeIndex = when (state.string(Prefs.Keys.THEME_MODE)) {
            Prefs.THEME_LIGHT -> 1
            Prefs.THEME_DARK -> 2
            else -> 0
        }
        OverlayDropdownPreference(
            title = stringResource(R.string.theme_mode),
            items = themes,
            selectedIndex = themeIndex,
            onSelectedIndexChange = { index ->
                state.setString(
                    Prefs.Keys.THEME_MODE,
                    when (index) {
                        1 -> Prefs.THEME_LIGHT
                        2 -> Prefs.THEME_DARK
                        else -> Prefs.THEME_SYSTEM
                    }
                )
            },
        )
        SwitchPreference(
            title = stringResource(R.string.monet),
            summary = stringResource(R.string.monet_summary),
            checked = state.bool(Prefs.Keys.MONET),
            onCheckedChange = { state.setBool(Prefs.Keys.MONET, it) },
        )
    }
}

@Composable
private fun LayoutSection(state: PrefsState) {
    SmallTitle(text = stringResource(R.string.section_layout))
    SectionCard {
        SwitchPreference(
            title = stringResource(R.string.floating_bar),
            summary = stringResource(R.string.floating_bar_summary),
            checked = state.bool(Prefs.Keys.FLOATING_BAR),
            onCheckedChange = { state.setBool(Prefs.Keys.FLOATING_BAR, it) },
        )
        SwitchPreference(
            title = stringResource(R.string.liquid_glass),
            summary = stringResource(R.string.liquid_glass_summary),
            checked = state.bool(Prefs.Keys.LIQUID_GLASS),
            enabled = isRuntimeShaderSupported(),
            onCheckedChange = { state.setBool(Prefs.Keys.LIQUID_GLASS, it) },
        )
        SwitchPreference(
            title = stringResource(R.string.predictive_back),
            summary = stringResource(R.string.predictive_back_summary),
            checked = state.bool(Prefs.Keys.PREDICTIVE_BACK),
            onCheckedChange = { state.setBool(Prefs.Keys.PREDICTIVE_BACK, it) },
        )
    }
}

@Composable
private fun ActionsSection(state: PrefsState, serviceReady: Boolean, context: Context) {
    SmallTitle(text = stringResource(R.string.section_actions))
    SectionCard {
        ArrowPreference(
            title = stringResource(R.string.restart_systemui),
            summary = stringResource(R.string.restart_systemui_summary),
            onClick = { restartSystemUi(context) },
        )
        SwitchPreference(
            title = stringResource(R.string.hide_launcher_icon),
            summary = stringResource(R.string.hide_launcher_icon_summary),
            checked = state.bool(Prefs.Keys.HIDE_LAUNCHER_ICON),
            onCheckedChange = {
                state.setBool(Prefs.Keys.HIDE_LAUNCHER_ICON, it)
                applyLauncherIcon(context, it)
            },
        )
    }
}

/**
 * 「发送日志」入口。
 *
 * 位置按需求放在「关于」上面：排查问题时先拿到日志，再看版本信息。
 */
@Composable
private fun LogSection(state: PrefsState, serviceReady: Boolean, context: Context) {
    var showDialog by remember { mutableStateOf(false) }
    SmallTitle(text = stringResource(R.string.section_logs))
    SectionCard {
        ArrowPreference(
            title = stringResource(R.string.send_log),
            summary = stringResource(R.string.send_log_summary),
            onClick = { showDialog = true },
        )
    }
    if (showDialog) {
        SendLogDialog(
            show = true,
            snapshotProvider = { logSnapshot(state, serviceReady, context) },
            onDismissRequest = { showDialog = false },
        )
    }
}

/** 把当前配置整理成日志报告要用的快照。 */
private fun logSnapshot(state: PrefsState, serviceReady: Boolean, context: Context): LogSnapshot {
    val rawList = state.string(Prefs.Keys.FILTER_PACKAGES)
    val ruleCount = state.string(Prefs.Keys.SB_RULES)
        .split('\n')
        .count { it.isNotBlank() }
    return LogSnapshot(
        serviceReady = serviceReady,
        masterEnabled = state.bool(Prefs.Keys.MASTER_ENABLE),
        typeSwitches = listOf(
            context.getString(R.string.privacy_camera) to state.bool(Prefs.Keys.HIDE_CAMERA),
            context.getString(R.string.privacy_mic) to state.bool(Prefs.Keys.HIDE_MIC),
            context.getString(R.string.privacy_location) to state.bool(Prefs.Keys.HIDE_LOCATION),
            context.getString(R.string.privacy_media) to state.bool(Prefs.Keys.HIDE_MEDIA_PROJECTION),
        ),
        filterMode = state.string(Prefs.Keys.FILTER_MODE),
        filterCount = rawList.split('\n', ',', ';').count { it.isNotBlank() },
        statusBarEnabled = state.bool(Prefs.Keys.SB_ENABLE),
        statusBarMode = state.string(Prefs.Keys.SB_MODE),
        statusBarRuleCount = ruleCount,
        hookCount = state.string(Prefs.Keys.HOOK_COUNT),
        hookTime = state.string(Prefs.Keys.HOOK_TIME),
    )
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    SmallTitle(text = stringResource(R.string.section_about))
    SectionCard {
        BasicComponent(
            title = stringResource(R.string.app_name),
            summary = stringResource(R.string.about_version),
        )
        BasicComponent(
            title = stringResource(R.string.about_target_title),
            summary = stringResource(R.string.about_target),
        )
        Card(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zoulin3/HyperStatusBar")),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicComponent(
                title = stringResource(R.string.about_license_title),
                summary = stringResource(R.string.about_license),
            )
        }
    }
}

/**
 * 读取 Root 管理器（KernelSU / Magisk / APatch 等）的名称与版本。
 *
 * 显示名直接写死：这几个管理器的 Application label 是 null
 * （它们的 manifest 没有 android:label，靠代码在运行时设置），
 * 走 getApplicationLabel 只会拿到包名，界面上就只剩一个版本号。
 * 版本号仍然从 PackageManager 实时读，所以升级管理器能立刻反映出来。
 *
 * 都不装则返回 null，由调用方显示"未知"，不猜也不编。
 */
private fun readRootManagerVersion(context: Context): String? {
    val managers = listOf(
        "me.weishu.kernelsu" to "KernelSU",
        "me.rifsxd.ksunext" to "KernelSU Next",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "com.topjohnwu.magisk" to "Magisk",
        "me.bmax.apatch" to "APatch",
    )
    val pm = context.packageManager
    for ((pkg, displayName) in managers) {
        val version = runCatching {
            pm.getPackageInfo(pkg, 0).versionName
        }.getOrNull() ?: continue
        return if (version.isNullOrBlank()) displayName else "$displayName $version"
    }
    return null
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return "%.1f %s".format(java.util.Locale.US, value, units[index])
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        content()
    }
}

/** 重启 SystemUI。优先用 root shell；没有 root 时明确告知失败，不做静默兜底。 */
private fun restartSystemUi(context: Context) {
    val ok = runCatching {
        Runtime.getRuntime()
            .exec(arrayOf("su", "-c", "killall com.android.systemui"))
            .waitFor() == 0
    }.getOrDefault(false)
    Toast.makeText(
        context,
        context.getString(
            if (ok) R.string.restart_systemui_done else R.string.restart_systemui_failed
        ),
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * 显示/隐藏桌面图标。
 *
 * 桌面入口由 activity-alias `.LauncherIcon` 提供；主 Activity 保留
 * MODULE_SETTINGS category，因此隐藏桌面图标后依然能从 LSPosed 管理器打开。
 */
private fun applyLauncherIcon(context: Context, hidden: Boolean) {
    runCatching {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, "com.hyperstatusbar.LauncherIcon"),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }
}

private fun indicatorRuleOf(raw: String, pkg: String): Int? = raw.lineSequence().map(String::trim).firstNotNullOfOrNull { line ->
    val i=line.lastIndexOf('|'); if(i>0 && line.substring(0,i).trim().equals(pkg,true)) line.substring(i+1).trim().toIntOrNull() else null
}

private fun indicatorPackages(state: PrefsState): Set<String> = state.string(Prefs.Keys.FILTER_PACKAGES).split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

@Composable
private fun AppListPage(title: String, state: PrefsState, marked: (String) -> Boolean, onAppClick: (String) -> Unit, onBack: () -> Unit) {
 val appList=rememberAppList(); var query by remember { mutableStateOf("") }; var showSystem by remember { mutableStateOf(false) }
 val apps=remember(appList.apps,query,showSystem,state.string(Prefs.Keys.FILTER_PACKAGES),state.string(Prefs.Keys.SB_RULES),marked){
  filterApps(appList.apps,query,showSystem,marked)
}
 DetailPage(title=title,onBack=onBack){
  item { SectionCard { SwitchPreference(title=stringResource(R.string.show_system_apps),summary=stringResource(R.string.show_system_apps_summary),checked=showSystem,onCheckedChange={showSystem=it}) } }
  item { TextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),label=stringResource(R.string.search_apps),useLabelAsPlaceholder=true,singleLine=true) }
  items(apps,key={it.packageName}) { app ->
   Card(onClick={onAppClick(app.packageName)},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)) {
    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
     AppIconMarked(app.packageName,marked(app.packageName),Modifier.size(52.dp))
     Column(Modifier.weight(1f).padding(start=14.dp)){Text(app.label,fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=if(marked(app.packageName)) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface);Text(app.packageName,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)}
     Text("›",fontSize=28.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
   }
  }
 }
}

@Composable
private fun IndicatorAppDetailPage(state: PrefsState, packageName: String, onBack: () -> Unit) {
    val app = rememberAppList().apps.firstOrNull { it.packageName == packageName }
    val mode = state.string(Prefs.Keys.FILTER_MODE)
    val custom = mode == Prefs.FILTER_PER_APP
    val bits = indicatorRuleOf(state.string(Prefs.Keys.INDICATOR_RULES), packageName) ?: 0
    val selected = if (custom) indicatorRuleOf(state.string(Prefs.Keys.INDICATOR_RULES), packageName) != null else indicatorPackages(state).contains(packageName)
    val allowed = custom && selected
    val enableAllowed = custom || mode == Prefs.FILTER_WHITELIST || mode == Prefs.FILTER_BLACKLIST
    DetailPage(title=app?.label ?: packageName, onBack=onBack) { item { SectionCard {
        SwitchPreference(title="启用此应用规则", summary=if(custom) "仅按此应用单独设置的指示器类型隐藏" else "加入白名单或黑名单", checked=selected, modifier=Modifier.alpha(if(enableAllowed) 1f else .45f), onCheckedChange={ value ->
            if (custom) {
                val initial=(if(state.bool(Prefs.Keys.HIDE_CAMERA)) 1 else 0) or (if(state.bool(Prefs.Keys.HIDE_MIC)) 2 else 0) or (if(state.bool(Prefs.Keys.HIDE_LOCATION)) 4 else 0) or (if(state.bool(Prefs.Keys.HIDE_MEDIA_PROJECTION)) 8 else 0)
                state.setIntRule(Prefs.Keys.INDICATOR_RULES,packageName,if(value) initial.takeIf{it!=0}?:15 else null)
            } else if(enableAllowed) state.togglePackage(Prefs.Keys.FILTER_PACKAGES,packageName,value)
        })
        listOf("摄像头","麦克风","定位","投屏").forEachIndexed { index,label ->
            val checked=bits and (1 shl index)!=0
            SwitchPreference(title=label,summary=if(index==3) "隐藏屏幕共享与投屏的指示器" else null,checked=checked,modifier=Modifier.alpha(if(allowed) 1f else .45f),onCheckedChange={ value -> if(allowed) state.setIntRule(Prefs.Keys.INDICATOR_RULES,packageName,if(value) bits or (1 shl index) else bits and (1 shl index).inv()) })
        }
    } } }
}

@Composable
private fun StatusBarAppDetailPage(state: PrefsState, packageName: String, onBack: () -> Unit) {
    val app=rememberAppList().apps.firstOrNull{it.packageName==packageName}
    val mode=state.string(Prefs.Keys.SB_MODE); val custom=mode==Prefs.SB_MODE_PER_APP
    val rule=sbRuleOf(state.string(Prefs.Keys.SB_RULES),packageName)
    val selected=rule!=null
    val enableAllowed = custom
    val controls = custom && rule != null && rule != Prefs.SB_NONE
    DetailPage(title=app?.label?:packageName,onBack=onBack){item{SectionCard{
        SwitchPreference(title="启用此应用规则",summary=if(custom) "仅按此应用单独设置的状态栏区域隐藏" else if(mode==Prefs.SB_MODE_LISTED) "加入白名单：应用按下方区域规则隐藏" else "加入黑名单：应用在列表内不隐藏",checked=selected,modifier=Modifier.alpha(if(enableAllowed) 1f else .45f),onCheckedChange={if(enableAllowed) if(it) state.setRule(Prefs.Keys.SB_RULES,packageName,Prefs.SB_BOTH) else state.removeRule(Prefs.Keys.SB_RULES,packageName)})
        OverlayDropdownPreference(title="隐藏区域",items=listOf("隐藏左区","隐藏右区","左右都隐藏","不隐藏"),selectedIndex=when(rule){Prefs.SB_LEFT->0;Prefs.SB_RIGHT->1;Prefs.SB_BOTH->2;else->3},enabled=controls,onSelectedIndexChange={i->if(controls) state.setRule(Prefs.Keys.SB_RULES,packageName,when(i){0->Prefs.SB_LEFT;1->Prefs.SB_RIGHT;2->Prefs.SB_BOTH;else->Prefs.SB_NONE})})
    }}}
}
