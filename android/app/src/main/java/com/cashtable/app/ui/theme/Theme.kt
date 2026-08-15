package com.cashtable.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 设计稿配色（钱迹风格）：主色蓝、收入绿、支出深灰、浅灰背景
val PrimaryBlue = Color(0xFF2E6FF2)
val IncomeGreen = Color(0xFF00B578)
val ExpenseGray = Color(0xFF333333)
val BgGray = Color(0xFFF5F6F8)
val TextGray = Color(0xFF999999)

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FE),
    onPrimaryContainer = Color(0xFF123D8C),
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
