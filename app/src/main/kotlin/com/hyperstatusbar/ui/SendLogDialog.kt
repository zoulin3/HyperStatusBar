package com.hyperstatusbar.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hyperstatusbar.R
import com.hyperstatusbar.core.LogCollector
import com.hyperstatusbar.core.LogSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「发送日志」对话框。
 *
 * 结构和 KernelSU 的 SendLogDialog 一致：一个说明 + 若干操作项 + 取消。
 * 这里只保留「保存日志」一条路径 —— 选目录这一步交给系统的文件选择器
 * （CreateDocument），用户在那儿既能换目录也能改文件名，不需要我们自己实现目录浏览。
 */
@Composable
fun SendLogDialog(
    show: Boolean,
    snapshotProvider: () -> LogSnapshot,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var collecting by remember { mutableStateOf(false) }

    // 报告先收集好再弹选择器：选择器一关就该直接写盘，不该让用户在系统界面里等。
    var pending by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val report = pending
        pending = null
        if (uri == null || report == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(report.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.log_saved else R.string.log_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.send_log),
        summary = stringResource(R.string.send_log_summary),
        insideMargin = DpSize(0.dp, 0.dp),
        onDismissRequest = onDismissRequest,
    ) {
        ArrowPreference(
            title = stringResource(
                if (collecting) R.string.log_collecting else R.string.save_log
            ),
            summary = stringResource(R.string.save_log_summary),
            enabled = !collecting,
            startAction = {
                Icon(
                    imageVector = MiuixIcons.File,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            },
            onClick = {
                if (!collecting) {
                    collecting = true
                    scope.launch {
                        val report = LogCollector.collect(context, snapshotProvider())
                        pending = report
                        collecting = false
                        onDismissRequest()
                        saveLauncher.launch(LogCollector.defaultFileName())
                    }
                }
            },
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        )
        TextButton(
            text = stringResource(android.R.string.cancel),
            onClick = onDismissRequest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp)
                .padding(horizontal = 24.dp),
        )
    }
}
