package com.mobilealarm.ui.theme

import androidx.compose.ui.graphics.Color

// 「纸上时辰」：暖纸、墨色、朱砂。颜色只用来标记“现在”和“被改变的时间”，
// 大面积留给纸本身的质感，避免界面变成一块块彩色卡片。
val Paper = Color(0xFFF2EDE1)
val PaperRaised = Color(0xFFF8F4E9)
val PaperSunken = Color(0xFFE8E0CF)
val Ink = Color(0xFF1D201A)
val Ink2 = Color(0xFF5F6357)
val Ink3 = Color(0xFF686C60)
val Ink4 = Color(0xFF96998C)
val Vermilion = Color(0xFFB4452F)
val VermilionWash = Color(0xFFEAD8D0)
val Moss = Color(0xFF5A6B55)
val MossWash = Color(0xFFDDE3D6)
val Hairline = Color(0x291D201A)

// 响铃页单独一套，避免夜里被大面积米色照亮。
val Night = Color(0xFF121511)
val NightRaised = Color(0xFF1B1E19)
val NightInk = Color(0xFFEDE7D8)
val NightInk2 = Color(0xFFB9B3A4)
val NightHairline = Color(0x33EDE7D8)

// 兼容旧命名，日历/设置等页面仍在引用。
val Brand = Vermilion
val BrandDark = Color(0xFF7F2D1E)
val BrandLight = Color(0xFFD9A294)
val Sky = Color(0xFF6F8790)
val Amber = Color(0xFFB07B3E)

val LightBackground = Paper
val LightSurface = PaperRaised
val LightSurfaceVariant = PaperSunken
val LightOnSurface = Ink
val LightOnSurfaceVariant = Ink3

val DarkBackground = Night
val DarkSurface = NightRaised
val DarkSurfaceVariant = Color(0xFF262A23)
val DarkOnSurface = NightInk
val DarkOnSurfaceVariant = NightInk2
