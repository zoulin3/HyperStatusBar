// 单个矢量图标，路径数据取自 Material Icons Round / check_circle_outline。
//
// 为什么不用 androidx.compose.material:material-icons-extended：
// 那个 AAR 把几千个图标打包进 dex，实测给 APK 加了 15 MB 以上，
// 而这里只需要其中一条路径。手写成一个 ImageVector 效果完全一致。

package com.hyperstatusbar.ui.ksu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CheckCircleOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CheckCircleOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(6.48f, 22f, 12f, 22f)
            reflectiveCurveTo(22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
            reflectiveCurveTo(7.59f, 4f, 12f, 4f)
            reflectiveCurveTo(20f, 7.59f, 20f, 12f)
            reflectiveCurveTo(16.41f, 20f, 12f, 20f)
            close()
            moveTo(16.59f, 7.58f)
            lineTo(10f, 14.17f)
            lineTo(7.41f, 11.59f)
            lineTo(6f, 13f)
            lineTo(10f, 17f)
            lineTo(18f, 9f)
            close()
        }
    }.build()
}
