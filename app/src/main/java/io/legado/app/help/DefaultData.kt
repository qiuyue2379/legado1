package io.legado.app.help

import io.legado.app.App
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import java.io.File

object DefaultData {

    const val httpTtsFileName = "httpTTS.json"
    const val txtTocRuleFileName = "txtTocRule.json"

    val httpTTS by lazy {
        val json =
            String(
                App.INSTANCE.assets.open("defaultData${File.separator}$httpTtsFileName")
                    .readBytes()
            )
        GSON.fromJsonArray<HttpTTS>(json)!!
    }

    val readConfigs by lazy {
        val json = String(
            App.INSTANCE.assets.open("defaultData${File.separator}${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json)!!
    }

    val txtTocRules by lazy {
        val json = String(
            App.INSTANCE.assets.open("defaultData${File.separator}$txtTocRuleFileName")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json)!!
    }

    val themeConfigs by lazy {
        val json = String(
            App.INSTANCE.assets.open("defaultData${File.separator}${ThemeConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfig.Config>(json)!!
    }

    val rssSources by lazy {
        val json = String(
            App.INSTANCE.assets.open("defaultData${File.separator}rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json)!!
    }

    fun importDefaultHttpTTS() {
        App.db.httpTTSDao.deleteDefault()
        httpTTS.let {
            App.db.httpTTSDao.insert(*it.toTypedArray())
        }
    }

    fun importDefaultTocRules() {
        App.db.txtTocRule.deleteDefault()
        txtTocRules.let {
            App.db.txtTocRule.insert(*it.toTypedArray())
        }
    }

    fun importDefaultRssSources() {
        App.db.rssSourceDao.insert(*rssSources.toTypedArray())
    }
}