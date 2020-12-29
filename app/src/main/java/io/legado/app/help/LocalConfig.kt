package io.legado.app.help

import android.content.Context
import androidx.core.content.edit
import io.legado.app.App

object LocalConfig {
    private const val versionCodeKey = "versionCode"

    private val localConfig =
        App.INSTANCE.getSharedPreferences("local", Context.MODE_PRIVATE)

    var versionCode
        get() = localConfig.getInt(versionCodeKey, 0)
        set(value) {
            localConfig.edit {
                putInt(versionCodeKey, value)
            }
        }

    val isFirstOpenApp: Boolean
        get() {
            val value = localConfig.getBoolean("firstOpen", true)
            if (value) {
                localConfig.edit { putBoolean("firstOpen", false) }
            }
            return value
        }

    @Suppress("SameParameterValue")
    private fun isLastVersion(
        lastVersion: Int,
        versionKey: String,
        firstOpenKey: String? = null
    ): Boolean {
        var version = localConfig.getInt(versionKey, 0)
        if (version == 0 && firstOpenKey != null) {
            if (!localConfig.getBoolean(firstOpenKey, true)) {
                version = 1
            }
        }
        if (version < lastVersion) {
            localConfig.edit { putInt(versionKey, lastVersion) }
            return false
        }
        return true
    }

    val readHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readHelpVersion", "firstRead")

    val backupHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "backupHelpVersion", "firstBackup")

    val readMenuHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readMenuHelpVersion", "firstReadMenu")

    val bookSourcesHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "bookSourceHelpVersion", "firstOpenBookSources")

    val debugHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "debugHelpVersion")

    val ruleHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "ruleHelpVersion")

    val hasUpHttpTTS: Boolean
        get() = isLastVersion(1, "httpTtsVersion")

    val hasUpTxtTocRule: Boolean
        get() = isLastVersion(1, "txtTocRuleVersion")
}