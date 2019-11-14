package io.legado.app.constant

import io.legado.app.help.AppConfig

enum class Theme {
    Dark, Light, Auto;

    companion object {
        fun getTheme(): Theme {
            return if (AppConfig.isNightTheme) {
                Dark
            } else Light
        }
    }
}