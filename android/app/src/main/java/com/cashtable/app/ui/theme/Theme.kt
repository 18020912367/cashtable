package com.cashtable.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 设计稿配色（邮政绿主题）：主色邮政绿、收入绿、支出深灰、浅灰背景
val PrimaryGreen = Color(0xFF00965E)
val IncomeGreen = Color(0xFF00B578)
val ExpenseGray = Color(0xFF333333)
val BgGray = Color(0xFFF5F6F8)
val TextGray = Color(0xFF999999)

private val LightColors = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFE2),
    onPrimaryContainer = Color(0xFF0A3D27),
    secondary = IncomeGreen,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = BgGray,
    onSurface = ExpenseGray,
    onSurfaceVariant = TextGray,
)

@Composable
fun CashtableTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
