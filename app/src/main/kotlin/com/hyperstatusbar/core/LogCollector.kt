package com.hyperstatusbar.core

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志报告里那一段「配置快照」。
 *
 * 让收集器只吃这份快照，而不是直接引用界面层的 PrefsState：
 * 一来收集逻辑可以脱离 Compose 单独调用，二来报告里出现哪些字段一眼可见。
 */
data class LogSnapshot(
    val serviceReady: Boolean,
    val masterEnabled: Boolean,
    val typeSwitches: List<Pair<String, Boolean>>,
    val filterMode: String,
    val filterCount: Int,
    val statusBarEnabled: Boolean,
    val statusBarMode: String,
    val statusBarRuleCount: Int,
    val hookCount: String,
    val hookTime: String,
)

/**
 * 日志收集。
 *
 * 报告分三块，和排查问题时真正要看的东西一一对应：
 *   1. 模块 Hook 日志 —— LSPosed 会把每个模块的 `module.log()` 落到
 *      `/data/adb/lspd/log/modules_*.log`，这里按模块名过滤后取尾部，
 *      能看到 Hook 是否装上、装了哪些入口、状态栏规则是否在生效；
 *   2. 系统崩溃与重启日志 —— logcat 的 crash 缓冲区、systemui / system_server
 *      的异常行、dropbox 里的 SYSTEM_TOMBSTONE / SYSTEM_BOOT 条目、/data/tombstones；
 *   3. 配置与设备快照 —— 由本进程直接读，不需要 Root。
 *
 * 全部读取都要 Root；没有 Root 时不静默失败，而是在报告里写明原因，
 * 免得用户拿到一份「看着正常但什么都没有」的日志。
 */
object LogCollector {

    private const val LSPD_LOG_DIR = "/data/adb/lspd/log"

    /** 单条命令的超时。诊断命令都是 tail/head 级别的，超过这个时间基本就是卡住了。 */
    private const val CMD_TIMEOUT_MS = 15_000L

    private const val SECTION_LIMIT = 200

    suspend fun collect(context: Context, snapshot: LogSnapshot): String = withContext(Dispatchers.IO) {
        val builder = StringBuilder(64 * 1024)

        builder.appendLine("===== 隐私指示器 (PrivacyDot) 诊断日志 =====")
        builder.appendLine("生成时间: ${stamp("yyyy-MM-dd HH:mm:ss", Date())}")
        builder.appendLine()

        builder.appendLine("----- [1] 设备与配置 -----")
        builder.appendLine(deviceInfo(context, snapshot))
        builder.appendLine()

        val rootOk = rootAvailable()
        builder.appendLine("----- [2] Root 状态 -----")
        builder.appendLine(if (rootOk) "su 可用，以下系统日志为实际读取结果。" else "su 不可用或未授权：系统日志无法读取，请先授予 Root 权限后重试。")
        builder.appendLine()

        if (rootOk) {
            builder.appendLine("----- [3] 模块 Hook 日志（LSPosed） -----")
            builder.appendLine(moduleHookLog())
            builder.appendLine()

            builder.appendLine("----- [4] Hook 运行时日志（logcat: PrivacyDot） -----")
            builder.appendLine(grepLogcat("PrivacyDot|privacydot"))
            builder.appendLine()

            builder.appendLine("----- [5] 系统崩溃日志（logcat -b crash） -----")
            builder.appendLine(root("logcat -b crash -d -v time 2>/dev/null | tail -n $SECTION_LIMIT"))
            builder.appendLine()

            builder.appendLine("----- [6] SystemUI / system_server 异常与重启 -----")
            builder.appendLine(systemAnomalies())
            builder.appendLine()

            builder.appendLine("----- [7] Dropbox 条目（SystemUI 崩溃、开机记录） -----")
            builder.appendLine(dropboxInfo())
            builder.appendLine()

            builder.appendLine("----- [8] Tombstones -----")
            builder.appendLine(tombstonesInfo())
            builder.appendLine()
        }

        builder.appendLine("===== 日志结束 =====")
        builder.toString()
    }

    /** 默认文件名，和 KernelSU 的 bugreport 命名风格保持一致。 */
    fun defaultFileName(): String =
        "PrivacyDot_log_${stamp("yyyy-MM-dd_HH-mm", Date())}.txt"

    // —— 进程内可读的部分 ——

    private fun deviceInfo(context: Context, snapshot: LogSnapshot): String {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        return buildString {
            appendLine("应用版本: $versionName")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("指纹: ${Build.FINGERPRINT}")
            appendLine("架构: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("LSPosed 已连接: ${if (snapshot.serviceReady) "是" else "否"}")
            appendLine("总开关: ${if (snapshot.masterEnabled) "开" else "关"}")
            snapshot.typeSwitches.forEach { (name, on) ->
                appendLine("  隐私类型 $name: ${if (on) "隐藏" else "显示"}")
            }
            appendLine("指示器生效范围: ${snapshot.filterMode}（已选 ${snapshot.filterCount} 个应用）")
            appendLine("状态栏管控: ${if (snapshot.statusBarEnabled) "开" else "关"}")
            appendLine("状态栏生效范围: ${snapshot.statusBarMode}（单独规则 ${snapshot.statusBarRuleCount} 条）")
            appendLine("Hook 安装次数: ${snapshot.hookCount.ifBlank { "未上报" }}")
            appendLine("Hook 安装时间: ${snapshot.hookTime.ifBlank { "未上报" }}")
        }
    }

    // —— 需要 Root 的部分 ——

    /**
     * 模块 Hook 日志。
     *
     * LSPosed 的 modules 日志按启动时间分文件，这里取最新两个文件里属于本模块的行。
     * 文件是 0640 root:root，普通应用读不到，所以必须走 su。
     */
    private fun moduleHookLog(): String {
        val files = root("ls -1t $LSPD_LOG_DIR/modules_*.log 2>/dev/null | head -n 2")
            .lines()
            .map { it.trim() }
            .filter { it.endsWith(".log") }
        if (files.isEmpty()) {
            return "未找到 $LSPD_LOG_DIR/modules_*.log（模块可能尚未被加载，或日志目录不同）。"
        }
        return files.joinToString("\n") { file ->
            "### $file\n" + root("grep -a PrivacyDot '$file' 2>/dev/null | tail -n $SECTION_LIMIT")
                .ifBlank { "（该文件里没有本模块的记录）" }
        }
    }

    /**
     * 只看和我们相关的 logcat 行。
     *
     * 不带 `-s` 过滤是因为要同时抓 tag（PrivacyDot）和进程名（com.hyperstatusbar），
     * 两者都可能在排查时出现。
     */
    private fun grepLogcat(pattern: String): String =
        root("logcat -d -v time 2>/dev/null | grep -aE '$pattern' | tail -n $SECTION_LIMIT")
            .ifBlank { "（当前 logcat 缓冲区里没有本模块的记录）" }

    /**
     * SystemUI / system_server 的异常与重启。
     *
     * 重点是「进程被杀、被重启、ANR、Fatal」这几类行：
     * 模块跑在 SystemUI 进程里，SystemUI 一旦反复重启，
     * 这些行就是判断「是不是模块把它搞崩了」的第一手证据。
     */
    private fun systemAnomalies(): String {
        val lines = root(
            "logcat -b system,main -d -v time 2>/dev/null " +
                "| grep -aE 'systemui|system_server' " +
                "| grep -aiE 'died|crash|fatal|anr|restart|force stop|killed' " +
                "| tail -n $SECTION_LIMIT"
        )
        return lines.ifBlank { "（缓冲区里没有 SystemUI / system_server 的异常行）" }
    }

    /** dropbox：SystemUI 崩溃、开机记录都在这里，最新三条直接展开内容。 */
    private fun dropboxInfo(): String {
        val listing = root("ls -lt /data/system/dropbox 2>/dev/null | head -n 25")
        if (listing.isBlank()) return "（无法读取 /data/system/dropbox）"

        val head = root(
            "for f in \$(ls -1t /data/system/dropbox 2>/dev/null | head -n 3); do " +
                "echo \"### \$f\"; " +
                "case \"\$f\" in " +
                "*.gz) zcat \"/data/system/dropbox/\$f\" 2>/dev/null | head -n 40 ;; " +
                "*) head -n 40 \"/data/system/dropbox/\$f\" 2>/dev/null ;; " +
                "esac; done"
        )
        return "条目列表:\n$listing\n\n最新条目内容:\n$head"
    }

    /** tombstones：native 崩溃现场，先列文件，再展开最新的一个。 */
    private fun tombstonesInfo(): String {
        val listing = root("ls -lt /data/tombstones 2>/dev/null | head -n 15")
        if (listing.isBlank()) return "（无法读取 /data/tombstones）"

        val newest = root(
            "f=\$(ls -1t /data/tombstones/tombstone_[0-9]* 2>/dev/null | head -n 1); " +
                "if [ -n \"\$f\" ]; then echo \"### \$f\"; head -n 60 \"\$f\"; fi"
        )
        return "文件列表:\n$listing\n\n最新文件:\n$newest"
    }

    /** su 是否可用：直接问一次 uid。授权弹窗会在这里出现，用户拒绝就返回 false。 */
    private fun rootAvailable(): Boolean =
        root("id", timeoutMs = 20_000L).contains("uid=0")

    /**
     * 跑一条 root 命令并收回它的标准输出。
     *
     * 用 ProcessBuilder 直接执行 `su -c <script>`，不经过系统 shell，
     * 免得脚本里的引号再被解析一层。
     * 读取放在看门狗线程里守着：命令卡住（比如 su 弹窗没响应）时强杀进程并返回已读到的内容，
     * 不能把收集日志这件事拖到永远。
     */
    private fun root(script: String, timeoutMs: Long = CMD_TIMEOUT_MS): String {
        return runCatching {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val watchdog = Thread {
                runCatching {
                    Thread.sleep(timeoutMs)
                    process.destroyForcibly()
                }
            }
            watchdog.isDaemon = true
            watchdog.start()

            val output = runCatching {
                process.inputStream.bufferedReader().use { it.readText() }
            }.getOrDefault("")
            runCatching { process.waitFor() }
            watchdog.interrupt()

            output.trim().ifBlank { "" }
        }.getOrElse { "（执行失败：${it.javaClass.simpleName}）" }
    }

    private fun stamp(pattern: String, date: Date): String =
        SimpleDateFormat(pattern, Locale.US).format(date)
}
