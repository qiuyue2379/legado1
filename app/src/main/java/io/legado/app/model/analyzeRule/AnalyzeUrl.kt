package io.legado.app.model.analyzeRule

import android.annotation.SuppressLint
import androidx.annotation.Keep
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import io.legado.app.constant.AppConst.SCRIPT_ENGINE
import io.legado.app.constant.AppConst.UA_NAME
import io.legado.app.constant.AppPattern.JS_PATTERN
import io.legado.app.data.entities.BaseBook
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.AppConfig
import io.legado.app.help.CacheManager
import io.legado.app.help.JsExtensions
import io.legado.app.help.http.*
import io.legado.app.utils.*
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern
import javax.script.SimpleBindings

/**
 * Created by GKF on 2018/1/24.
 * 搜索URL规则解析
 */
@Keep
@SuppressLint("DefaultLocale")
class AnalyzeUrl(
    var ruleUrl: String,
    val key: String? = null,
    val page: Int? = null,
    val speakText: String? = null,
    val speakSpeed: Int? = null,
    var baseUrl: String = "",
    var useWebView: Boolean = false,
    val book: BaseBook? = null,
    val chapter: BookChapter? = null,
    private val ruleData: RuleDataInterface? = null,
    headerMapF: Map<String, String>? = null
) : JsExtensions {
    companion object {
        val paramPattern: Pattern = Pattern.compile("\\s*,\\s*(?=\\{)")
        private val pagePattern = Pattern.compile("<(.*?)>")
    }

    var url: String = ""
    val headerMap = HashMap<String, String>()
    var body: String? = null
    var type: String? = null
    private lateinit var urlHasQuery: String
    private var queryStr: String? = null
    private val fieldMap = LinkedHashMap<String, String>()
    private var charset: String? = null
    private var method = RequestMethod.GET
    private var proxy: String? = null
    private var retry: Int = 0

    init {
        val urlMatcher = paramPattern.matcher(baseUrl)
        if (urlMatcher.find()) baseUrl = baseUrl.substring(0, urlMatcher.start())
        headerMapF?.let {
            headerMap.putAll(it)
            if (it.containsKey("proxy")) {
                proxy = it["proxy"]
                headerMap.remove("proxy")
            }
        }
        //替换参数
        analyzeJs()
        replaceKeyPageJs()
        //处理URL
        initUrl()
    }

    private fun analyzeJs() {
        var start = 0
        var tmp: String
        val jsMatcher = JS_PATTERN.matcher(ruleUrl)
        while (jsMatcher.find()) {
            if (jsMatcher.start() > start) {
                tmp =
                    ruleUrl.substring(start, jsMatcher.start()).trim { it <= ' ' }
                if (tmp.isNotEmpty()) {
                    ruleUrl = tmp.replace("@result", ruleUrl)
                }
            }
            ruleUrl = evalJS(jsMatcher.group(2) ?: jsMatcher.group(1), ruleUrl) as String
            start = jsMatcher.end()
        }
        if (ruleUrl.length > start) {
            tmp = ruleUrl.substring(start).trim { it <= ' ' }
            if (tmp.isNotEmpty()) {
                ruleUrl = tmp.replace("@result", ruleUrl)
            }
        }
    }

    /**
     * 替换关键字,页数,JS
     */
    private fun replaceKeyPageJs() { //先替换内嵌规则再替换页数规则，避免内嵌规则中存在大于小于号时，规则被切错
        //js
        if (ruleUrl.contains("{{") && ruleUrl.contains("}}")) {

            val analyze = RuleAnalyzer(ruleUrl) //创建解析

            val bindings = SimpleBindings()
            bindings["java"] = this
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings["baseUrl"] = baseUrl
            bindings["page"] = page
            bindings["key"] = key
            bindings["speakText"] = speakText
            bindings["speakSpeed"] = speakSpeed
            bindings["book"] = book

            //替换所有内嵌{{js}}
            val url = analyze.innerRule("{{", "}}") {
                when (val jsEval = SCRIPT_ENGINE.eval(it, bindings)) {
                    is String -> jsEval
                    jsEval is Double && jsEval % 1.0 == 0.0 -> String.format("%.0f", jsEval)
                    else -> jsEval.toString()
                }
            }
            if (url.isNotEmpty()) ruleUrl = url
        }
        //page
        page?.let {
            val matcher = pagePattern.matcher(ruleUrl)
            while (matcher.find()) {
                val pages = matcher.group(1)!!.split(",")
                ruleUrl = if (page < pages.size) { //pages[pages.size - 1]等同于pages.last()
                    ruleUrl.replace(matcher.group(), pages[page - 1].trim { it <= ' ' })
                } else {
                    ruleUrl.replace(matcher.group(), pages.last().trim { it <= ' ' })
                }
            }
        }
    }

    /**
     * 处理URL
     */
    private fun initUrl() { //replaceKeyPageJs已经替换掉额外内容，此处url是基础形式，可以直接切首个‘,’之前字符串。
        val urlMatcher = paramPattern.matcher(ruleUrl)
        urlHasQuery = if (urlMatcher.find()) ruleUrl.substring(0, urlMatcher.start()) else ruleUrl
        url = NetworkUtils.getAbsoluteURL(baseUrl, urlHasQuery)
        NetworkUtils.getBaseUrl(url)?.let {
            baseUrl = it
        }
        if (urlHasQuery.length != ruleUrl.length) {
            GSON.fromJsonObject<UrlOption>(ruleUrl.substring(urlMatcher.end()))?.let { option ->
                option.method?.let {
                    if (it.equals("POST", true)) method = RequestMethod.POST
                }
                option.type?.let { type = it }
                option.headers?.let { headers ->
                    if (headers is Map<*, *>) {
                        headers.forEach { entry ->
                            headerMap[entry.key.toString()] = entry.value.toString()
                        }
                    } else if (headers is String) {
                        GSON.fromJsonObject<Map<String, String>>(headers)
                            ?.let { headerMap.putAll(it) }
                    }
                }
                option.charset?.let { charset = it }
                option.body?.let {
                    body = if (it is String) it else GSON.toJson(it)
                }
                option.webView?.let {
                    if (it.toString().isNotEmpty()) {
                        useWebView = true
                    }
                }
                option.js?.let {
                    evalJS(it)
                }
                retry = option.retry
            }
        }

        headerMap[UA_NAME] ?: let {
            headerMap[UA_NAME] = AppConfig.userAgent
        }
        when (method) {
            RequestMethod.GET -> {
                if (!useWebView) {
                    val pos = url.indexOf('?')
                    if (pos != -1) {
                        analyzeFields(url.substring(pos + 1))
                        url = url.substring(0, pos)
                    }
                }
            }
            RequestMethod.POST -> {
                body?.let {
                    if (!it.isJson()) {
                        analyzeFields(it)
                    }
                }
            }
        }
    }

    /**
     * 解析QueryMap
     */
    private fun analyzeFields(fieldsTxt: String) {
        queryStr = fieldsTxt
        val queryS = fieldsTxt.splitNotBlank("&")
        for (query in queryS) {
            val queryM = query.splitNotBlank("=")
            val value = if (queryM.size > 1) queryM[1] else ""
            if (charset.isNullOrEmpty()) {
                if (NetworkUtils.hasUrlEncoded(value)) {
                    fieldMap[queryM[0]] = value
                } else {
                    fieldMap[queryM[0]] = URLEncoder.encode(value, "UTF-8")
                }
            } else if (charset == "escape") {
                fieldMap[queryM[0]] = EncoderUtils.escape(value)
            } else {
                fieldMap[queryM[0]] = URLEncoder.encode(value, charset)
            }
        }
    }

    /**
     * 执行JS
     */
    private fun evalJS(jsStr: String, result: Any? = null): Any? {
        val bindings = SimpleBindings()
        bindings["java"] = this
        bindings["cookie"] = CookieStore
        bindings["cache"] = CacheManager
        bindings["page"] = page
        bindings["key"] = key
        bindings["speakText"] = speakText
        bindings["speakSpeed"] = speakSpeed
        bindings["book"] = book
        bindings["result"] = result
        bindings["baseUrl"] = baseUrl
        return SCRIPT_ENGINE.eval(jsStr, bindings)
    }

    fun put(key: String, value: String): String {
        chapter?.putVariable(key, value)
            ?: book?.putVariable(key, value)
            ?: ruleData?.putVariable(key, value)
        return value
    }

    fun get(key: String): String {
        when (key) {
            "bookName" -> book?.let {
                return it.name
            }
            "title" -> chapter?.let {
                return it.title
            }
        }
        return chapter?.variableMap?.get(key)
            ?: book?.variableMap?.get(key)
            ?: ruleData?.variableMap?.get(key)
            ?: ""
    }

    suspend fun getStrResponse(
        tag: String,
        jsStr: String? = null,
        sourceRegex: String? = null,
    ): StrResponse {
        if (type != null) {
            return StrResponse(url, StringUtils.byteToHexString(getByteArray(tag)))
        }
        setCookie(tag)
        if (useWebView) {
            val params = AjaxWebView.AjaxParams(url)
            params.headerMap = headerMap
            params.requestMethod = method
            params.javaScript = jsStr
            params.sourceRegex = sourceRegex
            params.postData = body?.toByteArray()
            params.tag = tag
            return getWebViewSrc(params)
        }
        return getProxyClient(proxy).newCallStrResponse(retry) {
            removeHeader(UA_NAME)
            addHeaders(headerMap)
            when (method) {
                RequestMethod.POST -> {
                    url(url)
                    if (fieldMap.isNotEmpty() || body.isNullOrBlank()) {
                        postForm(fieldMap, true)
                    } else {
                        postJson(body)
                    }
                }
                else -> get(url, fieldMap, true)
            }
        }
    }

    suspend fun getByteArray(tag: String? = null): ByteArray {
        setCookie(tag)
        @Suppress("BlockingMethodInNonBlockingContext")
        return getProxyClient(proxy).newCall(retry) {
            removeHeader(UA_NAME)
            addHeaders(headerMap)
            when (method) {
                RequestMethod.POST -> {
                    url(url)
                    if (fieldMap.isNotEmpty() || body.isNullOrBlank()) {
                        postForm(fieldMap, true)
                    } else {
                        postJson(body)
                    }
                }
                else -> get(url, fieldMap, true)
            }
        }.bytes()
    }

    private fun setCookie(tag: String?) {
        if (tag != null) {
            val cookie = CookieStore.getCookie(tag)
            if (cookie.isNotEmpty()) {
                val cookieMap = CookieStore.cookieToMap(cookie)
                val customCookieMap = CookieStore.cookieToMap(headerMap["Cookie"] ?: "")
                cookieMap.putAll(customCookieMap)
                val newCookie = CookieStore.mapToCookie(cookieMap)
                newCookie?.let {
                    headerMap.put("Cookie", it)
                }
            }
        }
    }

    fun getGlideUrl(): GlideUrl {
        val headers = LazyHeaders.Builder()
        headerMap.forEach { (key, value) ->
            headers.addHeader(key, value)
        }
        return GlideUrl(urlHasQuery, headers.build())
    }

    data class UrlOption(
        val method: String?,
        val charset: String?,
        val webView: Any?,
        val headers: Any?,
        val body: Any?,
        val type: String?,
        val js: String?,
        val retry: Int = 0
    )

}
