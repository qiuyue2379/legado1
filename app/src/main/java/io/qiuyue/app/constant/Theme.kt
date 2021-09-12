package io.qiuyue.app.constant

import io.qiuyue.app.help.AppConfig
import io.qiuyue.app.utils.ColorUtils

enum class Theme {
    Dark, Light, Auto, Transparent, EInk;

    companion object {
        fun getTheme() = when {
            AppConfig.isEInkMode -> EInk
            AppConfig.isNightTheme -> Dark
            else -> Light
        }

        fun getTheme(backgroundColor: Int) =
            if (ColorUtils.isColorLight(backgroundColor)) Light
            else Dark

    }
}