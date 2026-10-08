package com.hyperstatusbar.hook

import com.hyperstatusbar.core.Prefs
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field

/**
 * HyperOS / MIUI 隐私指示器拦截。
 *
 * 目标类：`com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl`
 *
 * 该类的 `mPromptInfo.mMiuiType` 是 int 数组，下标语义与
 * `StatusBarUtils.PRIVACY_TYPE = {1, 2, 4, 8}` 一致：
 * `[0]=摄像头, [1]=麦克风, [2]=定位, [3]=投屏`，值为 1 表示该类型正在被使用。
 *
 * 之所以在 `updatePrompt()` / `updateDotVisibility()` 调用前改写数组，而不是像
 * 常见做法那样直接改 `setStatus(int, String, Bundle)` 的入参：
 *
 * - `setStatus` 在状态清零时（i == 0）允许 `bundle` 为 null，在拦截回调里直接
 *   解包会抛 NullPointerException；异常沿 CommandQueue 回调抛回 SystemUI 主线程，
 *   表现为 SystemUI 崩溃；
 * - `key_prompt_type` 并非每次出现，长度也不受模块控制；
 * - `updatePrompt()` 是所有隐私 UI（状态栏、桌面胶囊、控制中心、锁屏、下拉头部）
 *   的唯一分发出口，在此单点规范化可保证所有消费方看到一致的数据。
 *
 * 同时顺带做长度防御：`MiuiPrivacyContainerView.setPrivacyIcons` 用
 * `iArr[i2]` 索引自己的 ImageView 数组，`StatusBarUtils.PRIVACY_TYPE[i2]`
 * 同样隐含"长度不超过 4"的假设，上游一旦给出更长的数组就会越界崩溃。
 */
internal object PrivacyHooks {

    private const val TAG = "PrivacyDot"
    private const val CONTROLLER_CLASS =
        "com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl"

    /** 保存 controller 实例，用于配置变更后主动刷新已显示的提示。 */
    @Volatile
    private var controllerRef: WeakReference<Any>? = null

    /** 反射字段缓存：updatePrompt 调用频繁，避免每次重新查找。 */
    @Volatile
    private var fPromptInfo: Field? = null

    @Volatile
    private var fAndroidPromptInfo: Field? = null

    @Volatile
    private var fMiuiType: Field? = null

    @Volatile
    private var fAndroidType: Field? = null

    @Volatile
    private var fPackageName: Field? = null

    fun install(module: XposedModule, classLoader: ClassLoader): Int {
        var installed = 0
        val controllerClass = runCatching { classLoader.loadClass(CONTROLLER_CLASS) }.getOrNull()
        if (controllerClass == null) {
            info(module, "未找到 $CONTROLLER_CLASS，跳过")
            return 0
        }
        cacheFields(controllerClass)

        // 1) updatePrompt()：所有隐私 UI 的唯一分发出口。
        runCatching {
            val method = controllerClass.getDeclaredMethod("updatePrompt")
            method.isAccessible = true
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    runCatching { normalize(chain.thisObject) }
                    return chain.proceed()
                }
            })
            installed++
            info(module, "已挂钩 $CONTROLLER_CLASS.updatePrompt()")
        }.onFailure {
            info(module, "挂钩 updatePrompt 失败: ${it.javaClass.simpleName}: ${it.message}")
        }

        // 2) updateDotVisibility()：状态栏/桌面圆点的可见性决策同样读取 mMiuiType。
        runCatching {
            val method = controllerClass.getDeclaredMethod("updateDotVisibility")
            method.isAccessible = true
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    runCatching { normalize(chain.thisObject) }
                    return chain.proceed()
                }
            })
            installed++
            info(module, "已挂钩 $CONTROLLER_CLASS.updateDotVisibility()")
        }.onFailure {
            info(module, "挂钩 updateDotVisibility 失败: ${it.javaClass.simpleName}: ${it.message}")
        }

        // 3) setStatus(...)：只用于捕获 controller 实例。
        //    不解包 bundle、不修改参数，异常全部吞掉，绝不向调用方抛出。
        runCatching {
            val method = controllerClass.getDeclaredMethod(
                "setStatus",
                Int::class.javaPrimitiveType,
                String::class.java,
                android.os.Bundle::class.java
            )
            method.isAccessible = true
            module.hook(method).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    runCatching {
                        val self = chain.thisObject ?: return@runCatching
                        if (controllerRef?.get() !== self) {
                            controllerRef = WeakReference(self)
                        }
                        normalize(self)
                    }
                    return chain.proceed()
                }
            })
            installed++
            info(module, "已挂钩 $CONTROLLER_CLASS.setStatus(int, String, Bundle)")
        }.onFailure {
            info(module, "挂钩 setStatus 失败: ${it.javaClass.simpleName}: ${it.message}")
        }

        // 4) AOSP 模式（MIUI 优化关闭）下的隐私列表过滤。
        installed += installAospPath(module, classLoader)

        return installed
    }

    private fun cacheFields(controllerClass: Class<*>) {
        runCatching {
            fPromptInfo = controllerClass.getDeclaredField("mPromptInfo").also { it.isAccessible = true }
            fAndroidPromptInfo =
                controllerClass.getDeclaredField("mAndroidPromptInfo").also { it.isAccessible = true }
        }

        val promptClass = runCatching {
            controllerClass.declaredFields
                .firstOrNull { it.name == "mPromptInfo" }
                ?.type
        }.getOrNull()

        if (promptClass != null) {
            runCatching {
                fMiuiType = promptClass.getDeclaredField("mMiuiType").also { it.isAccessible = true }
                fPackageName = promptClass.getDeclaredField("mPackageName").also { it.isAccessible = true }
            }
        }

        val androidClass = runCatching {
            controllerClass.declaredFields
                .firstOrNull { it.name == "mAndroidPromptInfo" }
                ?.type
        }.getOrNull()

        if (androidClass != null) {
            runCatching {
                fAndroidType = androidClass.getDeclaredField("mAndroidType").also { it.isAccessible = true }
            }
        }
    }

    /**
     * 配置变更后主动刷新：已显示的提示需要立刻消失，而不是等到下一次隐私状态变化。
     * 整个过程静默失败，绝不影响 SystemUI。
     */
    fun refreshVisibleState() {
        val controller = controllerRef?.get() ?: return
        runCatching {
            normalize(controller)
            runCatching {
                controller.javaClass.getDeclaredMethod("updatePrompt").let {
                    it.isAccessible = true
                    it.invoke(controller)
                }
            }
            runCatching {
                controller.javaClass.getDeclaredMethod("updateDotVisibility").let {
                    it.isAccessible = true
                    it.invoke(controller)
                }
            }
        }
    }

    /**
     * 规范化 `mPromptInfo` / `mAndroidPromptInfo`。
     *
     * 运行在 SystemUI 主线程，每步独立保护，任何异常都不允许逃逸。
     */
    private fun normalize(controller: Any?) {
        if (controller == null) return

        val promptInfo = readField(fPromptInfo, controller)
        if (promptInfo != null) {
            val packageName = readField(fPackageName, promptInfo) as? String
            run {
                val types = readField(fMiuiType, promptInfo) as? IntArray
                if (types != null) {
                    val normalized = applyPolicy(types, packageName)
                    if (!normalized.contentEquals(types)) {
                        writeField(fMiuiType, promptInfo, normalized)
                    }
                    if (allZero(normalized)) {
                        // 全部类型都被隐藏：把提示信息整体置空。
                        // 所有消费方收到 null 后会自行隐藏，不会残留空胶囊。
                        writeField(fPromptInfo, controller, null)
                    }
                }
            }
        }

        // AOSP 模式下的类型数组。
        val androidInfo = readField(fAndroidPromptInfo, controller)
        if (androidInfo != null && Prefs.boolean(Prefs.Keys.MASTER_ENABLE)) {
            val types = readField(fAndroidType, androidInfo) as? IntArray
            if (types != null) {
                val androidPackage = runCatching { androidInfo.javaClass.getDeclaredField("packageName").let { it.isAccessible = true; it.get(androidInfo) as? String } }.getOrNull()
                val normalized = applyPolicy(types, androidPackage)
                if (!normalized.contentEquals(types)) {
                    writeField(fAndroidType, androidInfo, normalized)
                }
            }
        }
    }

    /**
     * 按配置清零应隐藏的类型位，并把数组统一规范为长度 4。
     *
     * 长度处理的原因：`PRIVACY_TYPE` 与各消费方的图标数组都按 4 个类型布局，
     * 上游给出更长数组会让 `PRIVACY_TYPE[i2]` / `imageViewArr[i2]` 直接越界。
     *
     * @return 处理后的数组；与原数组内容一致时调用方可跳过写回
     */
    private fun applyPolicy(types: IntArray, packageName: String?): IntArray {
        val normalized = IntArray(Prefs.TYPE_COUNT)
        val limit = minOf(types.size, Prefs.TYPE_COUNT)
        for (index in 0 until limit) {
            normalized[index] = types[index]
        }
        for (index in 0 until Prefs.TYPE_COUNT) {
            if (Prefs.shouldHideType(index, packageName)) {
                normalized[index] = 0
            }
        }
        return normalized
    }

    private fun allZero(types: IntArray): Boolean {
        for (value in types) {
            if (value != 0) return false
        }
        return true
    }

    private fun info(module: XposedModule, message: String) {
        runCatching { module.log(android.util.Log.INFO, TAG, message) }
    }

    private fun readField(field: Field?, owner: Any): Any? {
        val target = field ?: return null
        return runCatching { target.get(owner) }.getOrNull()
    }

    private fun writeField(field: Field?, owner: Any, value: Any?) {
        val target = field ?: return
        runCatching { target.set(owner, value) }
    }

    /**
     * AOSP 路径：`PrivacyItemController` 维护的隐私项列表。
     *
     * 只在 MIUI 优化关闭时被 SystemUI 使用，做尽力而为的过滤；目标方法名在不同
     * Android 版本上不同（Kotlin 内部函数名会被混淆），因此逐个尝试，全部失败
     * 也不影响主路径。
     */
    private fun installAospPath(module: XposedModule, classLoader: ClassLoader): Int {
        val targetClass = runCatching {
            classLoader.loadClass("com.android.systemui.privacy.PrivacyItemController")
        }.getOrNull() ?: return 0

        val candidates = listOf(
            "getPrivacyList\$frameworks__base__packages__SystemUI__android_common__SystemUI_core",
            "getPrivacyList",
            "updatePrivacyList"
        )

        var installed = 0
        for (name in candidates) {
            val method = runCatching {
                targetClass.declaredMethods.firstOrNull { it.name == name }
            }.getOrNull() ?: continue
            runCatching {
                method.isAccessible = true
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        runCatching { filterPrivacyList(result) }
                        return result
                    }
                })
                installed++
                info(module, "已挂钩 PrivacyItemController.$name")
            }
        }
        return installed
    }

    private fun filterPrivacyList(result: Any?) {
        if (!Prefs.boolean(Prefs.Keys.MASTER_ENABLE)) return
        val list = result as? MutableList<*> ?: return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next() ?: continue
            val typeName = runCatching {
                item.javaClass.getDeclaredField("privacyType").let {
                    it.isAccessible = true
                    it.get(item)?.toString()
                }
            }.getOrNull() ?: continue
            val packageName = runCatching { item.javaClass.getDeclaredField("packageName").let { it.isAccessible=true; it.get(item) as? String } }.getOrNull()
            val remove = when (typeName) {
                "TYPE_CAMERA" -> Prefs.shouldHideType(Prefs.TYPE_CAMERA, packageName)
                "TYPE_MICROPHONE" -> Prefs.shouldHideType(Prefs.TYPE_MIC, packageName)
                "TYPE_LOCATION" -> Prefs.shouldHideType(Prefs.TYPE_LOCATION, packageName)
                "TYPE_MEDIA_PROJECTION" -> Prefs.shouldHideType(Prefs.TYPE_MEDIA_PROJECTION, packageName)
                else -> false
            }
            if (remove) {
                runCatching { iterator.remove() }
            }
        }
    }
}
