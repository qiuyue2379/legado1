package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import com.github.liuyueyi.quick.transfer.ChineseUtils
import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.help.AppConfig
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleDataInterface
import io.legado.app.utils.*
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import splitties.init.appCtx

@Suppress("unused")
@Parcelize
@Entity(
    tableName = "chapters",
    primaryKeys = ["url", "bookUrl"],
    indices = [(Index(value = ["bookUrl"], unique = false)),
        (Index(value = ["bookUrl", "index"], unique = true))],
    foreignKeys = [(ForeignKey(
        entity = Book::class,
        parentColumns = ["bookUrl"],
        childColumns = ["bookUrl"],
        onDelete = ForeignKey.CASCADE
    ))]
)    // 删除书籍时自动删除章节
data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购买
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            //
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null        //变量
) : Parcelable, RuleDataInterface {

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    override val variableMap by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable) ?: HashMap()
    }

    override fun putVariable(key: String, value: String?) {
        if (value != null) {
            variableMap[key] = value
        } else {
            variableMap.remove(key)
        }
        variable = GSON.toJson(variableMap)
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    @Suppress("unused")
    fun getDisplayTitle(
        replaceRules: Array<ReplaceRule>? = null,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
    ): String {
        var displayTitle = title.replace(AppPattern.rnRegex, "")
        if (useReplace && replaceRules != null) {
            replaceRules.forEach { item ->
                if (item.pattern.isNotEmpty()) {
                    try {
                        displayTitle = if (item.isRegex) {
                            displayTitle.replace(item.pattern.toRegex(), item.replacement)
                        } else {
                            displayTitle.replace(item.pattern, item.replacement)
                        }
                    } catch (e: Exception) {
                        appCtx.toastOnUi("${item.name}替换出错")
                    }
                }
            }
        }
        if (chineseConvert) {
            when (AppConfig.chineseConverterType) {
                1 -> displayTitle = ChineseUtils.t2s(displayTitle)
                2 -> displayTitle = ChineseUtils.s2t(displayTitle)
            }
        }
        return when {
            !isVip -> displayTitle
            isPay -> appCtx.getString(R.string.payed_title, displayTitle)
            else -> appCtx.getString(R.string.vip_title, displayTitle)
        }
    }

    fun getAbsoluteURL(): String {
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        val urlBefore = if (urlMatcher.find()) url.substring(0, urlMatcher.start()) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
        return if (urlBefore.length == url.length) {
            urlAbsoluteBefore
        } else {
            "$urlAbsoluteBefore," + url.substring(urlMatcher.end())
        }
    }

    fun getFileName(): String = String.format("%05d-%s.nb", index, MD5Utils.md5Encode16(title))

    fun getFontName(): String = String.format("%05d-%s.ttf", index, MD5Utils.md5Encode16(title))
}

