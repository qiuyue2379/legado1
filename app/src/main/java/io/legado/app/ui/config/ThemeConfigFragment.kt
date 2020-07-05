package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.legado.app.App
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppConfig
import io.legado.app.help.LauncherIconHelp
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.noButton
import io.legado.app.lib.dialogs.yesButton
import io.legado.app.lib.theme.ATH
import io.legado.app.lib.theme.ColorUtils
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.prefs.IconListPreference
import io.legado.app.utils.*


@Suppress("SameParameterValue")
class ThemeConfigFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    val items = arrayListOf("极简", "曜夜", "经典", "黑白", "A屏黑")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_theme)
        if (Build.VERSION.SDK_INT < 26) {
            findPreference<IconListPreference>(PreferKey.launcherIcon)?.let {
                preferenceScreen.removePreference(it)
            }
        }
        upPreferenceSummary(PreferKey.barElevation, AppConfig.elevation.toString())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ATH.applyEdgeEffectColor(listView)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.theme_config, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_theme_mode -> {
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                App.INSTANCE.applyDayNight()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        when (key) {
            PreferKey.launcherIcon -> LauncherIconHelp.changeIcon(getPrefString(key))
            PreferKey.transparentStatusBar -> recreateActivities()
            PreferKey.cPrimary,
            PreferKey.cAccent,
            PreferKey.cBackground,
            PreferKey.cBBackground -> {
                if (backgroundIsDark(sharedPreferences)) {
                    alert {
                        title = "白天背景太暗"
                        message = "将会恢复默认背景？"
                        yesButton {
                            upTheme(false)
                        }
                        noButton {
                            upTheme(false)
                        }
                    }.show().applyTint()
                } else {
                    upTheme(false)
                }
            }
            PreferKey.cNPrimary,
            PreferKey.cNAccent,
            PreferKey.cNBackground,
            PreferKey.cNBBackground -> {
                if (backgroundIsLight(sharedPreferences)) {
                    alert {
                        title = "夜间背景太亮"
                        message = "将会恢复默认背景？"
                        yesButton {
                            upTheme(true)
                        }
                        noButton {
                            upTheme(true)
                        }
                    }.show().applyTint()
                } else {
                    upTheme(true)
                }
            }
        }

    }

    @SuppressLint("PrivateResource")
    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "defaultTheme" -> changeTheme()
            PreferKey.barElevation -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.bar_elevation))
                .setMaxValue(32)
                .setMinValue(0)
                .setValue(AppConfig.elevation)
                .setCustomButton((R.string.btn_default_s)) {
                    AppConfig.elevation =
                        App.INSTANCE.resources.getDimension(R.dimen.design_appbar_elevation).toInt()
                    recreateActivities()
                }
                .show {
                    AppConfig.elevation = it
                    recreateActivities()
                }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun changeTheme() {
        alert(title = "切换默认主题") {
            items(items) { _, which ->
                when (which) {
                    0 -> {
                        putPrefInt(PreferKey.cPrimary, getCompatColor(R.color.md_grey_100))
                        putPrefInt(PreferKey.cAccent, getCompatColor(R.color.lightBlue_color))
                        putPrefInt(PreferKey.cBackground, getCompatColor(R.color.md_grey_100))
                        putPrefInt(PreferKey.cBBackground, getCompatColor(R.color.md_grey_200))
                        AppConfig.isNightTheme = false
                    }
                    1 -> {
                        putPrefInt(PreferKey.cNPrimary, getCompatColor(R.color.md_grey_900))
                        putPrefInt(PreferKey.cNAccent, getCompatColor(R.color.lightBlue_color))
                        putPrefInt(PreferKey.cNBackground, getCompatColor(R.color.md_grey_900))
                        putPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.md_grey_900))
                        AppConfig.isNightTheme = true
                    }
                    2 -> {
                        putPrefInt(PreferKey.cPrimary, getCompatColor(R.color.md_light_blue_500))
                        putPrefInt(PreferKey.cAccent, getCompatColor(R.color.md_pink_800))
                        putPrefInt(PreferKey.cBackground, getCompatColor(R.color.md_grey_100))
                        putPrefInt(PreferKey.cBBackground, getCompatColor(R.color.md_grey_200))
                        AppConfig.isNightTheme = false
                    }
                    3 -> {
                        putPrefInt(PreferKey.cPrimary, getCompatColor(R.color.white))
                        putPrefInt(PreferKey.cAccent, getCompatColor(R.color.black))
                        putPrefInt(PreferKey.cBackground, getCompatColor(R.color.white))
                        putPrefInt(PreferKey.cBBackground, getCompatColor(R.color.white))
                        AppConfig.isNightTheme = false
                    }
                    4 -> {
                        putPrefInt(PreferKey.cNPrimary, getCompatColor(R.color.black))
                        putPrefInt(PreferKey.cNAccent, getCompatColor(R.color.md_grey_500))
                        putPrefInt(PreferKey.cNBackground, getCompatColor(R.color.black))
                        putPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.black))
                        AppConfig.isNightTheme = true
                    }
                }
                App.INSTANCE.applyDayNight()
                recreateActivities()
            }
        }.show().applyTint()
    }

    private fun backgroundIsDark(sharedPreferences: SharedPreferences): Boolean {
        return !ColorUtils.isColorLight(
            sharedPreferences.getInt(PreferKey.cBackground, getCompatColor(R.color.md_grey_100))
        ) && !ColorUtils.isColorLight(
            sharedPreferences.getInt(PreferKey.cBBackground, getCompatColor(R.color.md_grey_200))
        )
    }

    private fun backgroundIsLight(sharedPreferences: SharedPreferences): Boolean {
        return ColorUtils.isColorLight(
            sharedPreferences.getInt(PreferKey.cNBackground, getCompatColor(R.color.md_grey_800))
        ) && ColorUtils.isColorLight(
            sharedPreferences.getInt(PreferKey.cNBBackground, getCompatColor(R.color.md_grey_850))
        )
    }

    private fun upTheme(isNightTheme: Boolean) {
        if (AppConfig.isNightTheme == isNightTheme) {
            listView.post {
                App.INSTANCE.applyTheme()
                recreateActivities()
            }
        }
    }

    private fun recreateActivities() {
        postEvent(EventBus.RECREATE, "")
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.barElevation -> preference.summary =
                getString(R.string.bar_elevation_s, value)
        }
    }
}