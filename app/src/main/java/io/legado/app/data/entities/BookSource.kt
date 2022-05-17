package io.legado.app.data.entities

import android.os.Parcelable
import android.text.TextUtils
import androidx.room.*
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.data.entities.rule.*
import io.legado.app.help.SourceAnalyzer
import io.legado.app.utils.*
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import splitties.init.appCtx
import java.io.InputStream

@Suppress("unused")
@Parcelize
@TypeConverters(BookSource.Converters::class)
@Entity(
    tableName = "book_sources",
    indices = [(Index(value = ["bookSourceUrl"], unique = false))]
)
data class BookSource(
    // 地址，包括 http/https
    @PrimaryKey
    var bookSourceUrl: String = "",
    // 名称
    var bookSourceName: String = "",
    // 分组
    var bookSourceGroup: String? = null,
    // 类型，0 文本，1 音频, 2 图片, 3 文件（指的是类似知轩藏书只提供下载的网站）
    @BookType.Type
    var bookSourceType: Int = 0,
    // 详情页url正则
    var bookUrlPattern: String? = null,
    // 手动排序编号
    var customOrder: Int = 0,
    // 是否启用
    var enabled: Boolean = true,
    // 启用发现
    var enabledExplore: Boolean = true,
    // 启用okhttp CookieJAr 自动保存每次请求的cookie
    @ColumnInfo(defaultValue = "0")
    override var enabledCookieJar: Boolean? = false,
    // 并发率
    override var concurrentRate: String? = null,
    // 请求头
    override var header: String? = null,
    // 登录地址
    override var loginUrl: String? = null,
    // 登录UI
    override var loginUi: String? = null,
    // 登录检测js
    var loginCheckJs: String? = null,
    // 注释
    var bookSourceComment: String? = null,
    // 最后更新时间，用于排序
    var lastUpdateTime: Long = 0,
    // 响应时间，用于排序
    var respondTime: Long = 180000L,
    // 智能排序的权重
    var weight: Int = 0,
    // 发现url
    var exploreUrl: String? = null,
    // 发现规则
    var ruleExplore: ExploreRule? = null,
    // 搜索url
    var searchUrl: String? = null,
    // 搜索规则
    var ruleSearch: SearchRule? = null,
    // 书籍信息页规则
    var ruleBookInfo: BookInfoRule? = null,
    // 目录页规则
    var ruleToc: TocRule? = null,
    // 正文页规则
    var ruleContent: ContentRule? = null
) : Parcelable, BaseSource {

    override fun getTag(): String {
        return bookSourceName
    }

    override fun getKey(): String {
        return bookSourceUrl
    }

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    val exploreKinds: List<ExploreKind> by lazy {
        val exploreUrl = exploreUrl ?: return@lazy emptyList()
        val kinds = arrayListOf<ExploreKind>()
        var ruleStr = exploreUrl
        if (ruleStr.isNotBlank()) {
            kotlin.runCatching {
                if (exploreUrl.startsWith("<js>", false)
                    || exploreUrl.startsWith("@js:", false)
                ) {
                    val aCache = ACache.get(appCtx, "explore")
                    ruleStr = aCache.getAsString(bookSourceUrl) ?: ""
                    if (ruleStr.isBlank()) {
                        val jsStr = if (exploreUrl.startsWith("@")) {
                            exploreUrl.substring(4)
                        } else {
                            exploreUrl.substring(4, exploreUrl.lastIndexOf("<"))
                        }
                        ruleStr = evalJS(jsStr).toString().trim()
                        aCache.put(bookSourceUrl, ruleStr)
                    }
                }
                if (ruleStr.isJsonArray()) {
                    GSON.fromJsonArray<ExploreKind>(ruleStr).getOrThrow()?.let {
                        kinds.addAll(it)
                    }
                } else {
                    ruleStr.split("(&&|\n)+".toRegex()).forEach { kindStr ->
                        val kindCfg = kindStr.split("::")
                        kinds.add(ExploreKind(kindCfg.first(), kindCfg.getOrNull(1)))
                    }
                }
            }.onFailure {
                kinds.add(ExploreKind("ERROR:${it.localizedMessage}", it.stackTraceToString()))
                it.printOnDebug()
            }
        }
        return@lazy kinds
    }

    override fun hashCode(): Int {
        return bookSourceUrl.hashCode()
    }

    override fun equals(other: Any?) =
        if (other is BookSource) other.bookSourceUrl == bookSourceUrl else false

    fun getSearchRule() = ruleSearch ?: SearchRule()

    fun getExploreRule() = ruleExplore ?: ExploreRule()

    fun getBookInfoRule() = ruleBookInfo ?: BookInfoRule()

    fun getTocRule() = ruleToc ?: TocRule()

    fun getContentRule() = ruleContent ?: ContentRule()

    fun getDisPlayNameGroup(): String {
        return if (bookSourceGroup.isNullOrBlank()) {
            bookSourceName
        } else {
            String.format("%s (%s)", bookSourceName, bookSourceGroup)
        }
    }

    fun addGroup(groups: String): BookSource {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.let {
            it.addAll(groups.splitNotBlank(AppPattern.splitGroupRegex))
            bookSourceGroup = TextUtils.join(",", it)
        }
        if (bookSourceGroup.isNullOrBlank()) bookSourceGroup = groups
        return this
    }

    fun removeGroup(groups: String): BookSource {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.let {
            it.removeAll(groups.splitNotBlank(AppPattern.splitGroupRegex).toSet())
            bookSourceGroup = TextUtils.join(",", it)
        }
        return this
    }

    fun hasGroup(group: String): Boolean {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.let {
            return it.indexOf(group) != -1
        }
        return false
    }

    fun removeInvalidGroups() {
        removeGroup(getInvalidGroupNames())
    }

    fun getInvalidGroupNames(): String {
        return bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.filter {
            "失效" in it
        }?.joinToString() ?: ""
    }

    fun equal(source: BookSource) =
        equal(bookSourceName, source.bookSourceName)
                && equal(bookSourceUrl, source.bookSourceUrl)
                && equal(bookSourceGroup, source.bookSourceGroup)
                && bookSourceType == source.bookSourceType
                && equal(bookUrlPattern, source.bookUrlPattern)
                && equal(bookSourceComment, source.bookSourceComment)
                && enabled == source.enabled
                && enabledExplore == source.enabledExplore
                && enabledCookieJar == source.enabledCookieJar
                && equal(header, source.header)
                && loginUrl == source.loginUrl
                && equal(exploreUrl, source.exploreUrl)
                && equal(searchUrl, source.searchUrl)
                && getSearchRule() == source.getSearchRule()
                && getExploreRule() == source.getExploreRule()
                && getBookInfoRule() == source.getBookInfoRule()
                && getTocRule() == source.getTocRule()
                && getContentRule() == source.getContentRule()

    private fun equal(a: String?, b: String?) = a == b || (a.isNullOrEmpty() && b.isNullOrEmpty())

    companion object {

        fun fromJson(json: String): Result<BookSource> {
            return SourceAnalyzer.jsonToBookSource(json)
        }

        fun fromJsonArray(json: String): Result<MutableList<BookSource>> {
            return SourceAnalyzer.jsonToBookSources(json)
        }

        fun fromJsonArray(inputStream: InputStream): Result<MutableList<BookSource>> {
            return SourceAnalyzer.jsonToBookSources(inputStream)
        }
    }

    class Converters {

        @TypeConverter
        fun exploreRuleToString(exploreRule: ExploreRule?): String =
            GSON.toJson(exploreRule)

        @TypeConverter
        fun stringToExploreRule(json: String?) =
            GSON.fromJsonObject<ExploreRule>(json).getOrNull()

        @TypeConverter
        fun searchRuleToString(searchRule: SearchRule?): String =
            GSON.toJson(searchRule)

        @TypeConverter
        fun stringToSearchRule(json: String?) =
            GSON.fromJsonObject<SearchRule>(json).getOrNull()

        @TypeConverter
        fun bookInfoRuleToString(bookInfoRule: BookInfoRule?): String =
            GSON.toJson(bookInfoRule)

        @TypeConverter
        fun stringToBookInfoRule(json: String?) =
            GSON.fromJsonObject<BookInfoRule>(json).getOrNull()

        @TypeConverter
        fun tocRuleToString(tocRule: TocRule?): String =
            GSON.toJson(tocRule)

        @TypeConverter
        fun stringToTocRule(json: String?) =
            GSON.fromJsonObject<TocRule>(json).getOrNull()

        @TypeConverter
        fun contentRuleToString(contentRule: ContentRule?): String =
            GSON.toJson(contentRule)

        @TypeConverter
        fun stringToContentRule(json: String?) =
            GSON.fromJsonObject<ContentRule>(json).getOrNull()

    }
}