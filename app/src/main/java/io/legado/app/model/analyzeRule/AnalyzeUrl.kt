package io.legado.app.model.analyzeRule

import android.annotation.SuppressLint
import android.text.TextUtils
import androidx.annotation.Keep
import io.legado.app.constant.AppConst.SCRIPT_ENGINE
import io.legado.app.constant.AppPattern.EXP_PATTERN
import io.legado.app.constant.AppPattern.JS_PATTERN
import io.legado.app.data.entities.BaseBook
import io.legado.app.help.JsExtensions
import io.legado.app.help.http.*
import io.legado.app.help.http.api.HttpGetApi
import io.legado.app.help.http.api.HttpPostApi
import io.legado.app.utils.*
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.Call
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
    key: String? = null,
    page: Int? = null,
    headerMapF: Map<String, String>? = null,
    baseUrl: String? = null,
    book: BaseBook? = null,
    var useWebView: Boolean = false
) : JsExtensions {
    companion object {
        private val pagePattern = Pattern.compile("<(.*?)>")
        private val jsonType = MediaType.parse("application/json; charset=utf-8")
    }

    private var baseUrl: String = ""
    lateinit var url: String
        private set
    var path: String? = null
        private set
    val headerMap = HashMap<String, String>()
    private var queryStr: String? = null
    private val fieldMap = LinkedHashMap<String, String>()
    private var charset: String? = null
    private var bodyTxt: String? = null
    private var body: RequestBody? = null
    private var method = RequestMethod.GET

    init {
        baseUrl?.let {
            this.baseUrl = it.split(",[^\\{]*".toRegex(), 1)[0]
        }
        headerMapF?.let { headerMap.putAll(it) }
        //替换参数
        analyzeJs(key, page, book)
        replaceKeyPageJs(key, page, book)
        //处理URL
        initUrl()
    }

    private fun analyzeJs(key: String?, page: Int?, book: BaseBook?) {
        val ruleList = arrayListOf<String>()
        var start = 0
        var tmp: String
        val jsMatcher = JS_PATTERN.matcher(ruleUrl)
        while (jsMatcher.find()) {
            if (jsMatcher.start() > start) {
                tmp =
                    ruleUrl.substring(start, jsMatcher.start()).replace("\n", "").trim { it <= ' ' }
                if (!TextUtils.isEmpty(tmp)) {
                    ruleList.add(tmp)
                }
            }
            ruleList.add(jsMatcher.group())
            start = jsMatcher.end()
        }
        if (ruleUrl.length > start) {
            tmp = ruleUrl.substring(start).replace("\n", "").trim { it <= ' ' }
            if (!TextUtils.isEmpty(tmp)) {
                ruleList.add(tmp)
            }
        }
        for (rule in ruleList) {
            var ruleStr = rule
            when {
                ruleStr.startsWith("<js>") -> {
                    ruleStr = ruleStr.substring(4, ruleStr.lastIndexOf("<"))
                    ruleUrl = evalJS(ruleStr, ruleUrl, page, key, book) as String
                }
                ruleStr.startsWith("@js", true) -> {
                    ruleStr = ruleStr.substring(4)
                    ruleUrl = evalJS(ruleStr, ruleUrl, page, key, book) as String
                }
                else -> ruleUrl = ruleStr.replace("@result", ruleUrl)
            }
        }
    }

    /**
     * 替换关键字,页数,JS
     */
    private fun replaceKeyPageJs(key: String?, page: Int?, book: BaseBook?) {
        //page
        page?.let {
            val matcher = pagePattern.matcher(ruleUrl)
            while (matcher.find()) {
                val pages = matcher.group(1)!!.split(",")
                ruleUrl = if (page <= pages.size) {
                    ruleUrl.replace(matcher.group(), pages[page - 1].trim { it <= ' ' })
                } else {
                    ruleUrl.replace(matcher.group(), pages.last().trim { it <= ' ' })
                }
            }
        }
        //js
        if (ruleUrl.contains("{{") && ruleUrl.contains("}}")) {
            var jsEval: Any
            val sb = StringBuffer(ruleUrl.length)
            val simpleBindings = SimpleBindings()
            simpleBindings["java"] = this
            simpleBindings["baseUrl"] = baseUrl
            simpleBindings["page"] = page
            simpleBindings["key"] = key
            simpleBindings["book"] = book
            val expMatcher = EXP_PATTERN.matcher(ruleUrl)
            while (expMatcher.find()) {
                jsEval = SCRIPT_ENGINE.eval(expMatcher.group(1), simpleBindings)
                if (jsEval is String) {
                    expMatcher.appendReplacement(sb, jsEval)
                } else if (jsEval is Double && jsEval % 1.0 == 0.0) {
                    expMatcher.appendReplacement(sb, String.format("%.0f", jsEval))
                } else {
                    expMatcher.appendReplacement(sb, jsEval.toString())
                }
            }
            expMatcher.appendTail(sb)
            ruleUrl = sb.toString()
        }
    }

    /**
     * 处理URL
     */
    private fun initUrl() {
        var urlArray = ruleUrl.split(",[^\\{]*".toRegex(), 2)
        url = urlArray[0]
        NetworkUtils.getBaseUrl(url)?.let {
            baseUrl = it
        }
        if (urlArray.size > 1) {
            val options = GSON.fromJsonObject<Map<String, String>>(urlArray[1])
            options?.let { _ ->
                options["method"]?.let { if (it.equals("POST", true)) method = RequestMethod.POST }
                options["headers"]?.let { headers ->
                    GSON.fromJsonObject<Map<String, String>>(headers)?.let { headerMap.putAll(it) }
                }
                options["body"]?.let { bodyTxt = it }
                options["charset"]?.let { charset = it }
                options["webView"]?.let { if (it.isNotEmpty()) useWebView = true }
            }
        }
        when (method) {
            RequestMethod.GET -> {
                if (!useWebView) {
                    urlArray = url.split("?")
                    url = urlArray[0]
                    if (urlArray.size > 1) {
                        analyzeFields(urlArray[1])
                    }
                }
            }
            RequestMethod.POST -> {
                bodyTxt?.let {
                    if (it.isJson()) {
                        body = RequestBody.create(jsonType, it)
                    } else {
                        analyzeFields(it)
                    }
                } ?: let {
                    body = FormBody.Builder().build()
                }
            }
        }
    }


    /**
     * 解析QueryMap
     */
    @Throws(Exception::class)
    private fun analyzeFields(fieldsTxt: String) {
        queryStr = fieldsTxt
        val queryS = fieldsTxt.splitNotBlank("&")
        for (query in queryS) {
            val queryM = query.splitNotBlank("=")
            val value = if (queryM.size > 1) queryM[1] else ""
            if (TextUtils.isEmpty(charset)) {
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
    @Throws(Exception::class)
    private fun evalJS(
        jsStr: String,
        result: Any?,
        page: Int?,
        key: String?,
        book: BaseBook?
    ): Any {
        val bindings = SimpleBindings()
        bindings["java"] = this
        bindings["page"] = page
        bindings["key"] = key
        bindings["book"] = book
        bindings["result"] = result
        bindings["baseUrl"] = baseUrl
        return SCRIPT_ENGINE.eval(jsStr, bindings)
    }

    @Throws(Exception::class)
    fun getResponse(tag: String): Call<String> {
        val cookie = CookieStore.getCookie(tag)
        if (cookie.isNotEmpty()) {
            headerMap["Cookie"] = cookie
        }
        return when {
            method == RequestMethod.POST -> {
                if (fieldMap.isNotEmpty()) {
                    HttpHelper
                        .getApiService<HttpPostApi>(baseUrl, charset)
                        .postMap(url, fieldMap, headerMap)
                } else {
                    HttpHelper
                        .getApiService<HttpPostApi>(baseUrl, charset)
                        .postBody(url, body!!, headerMap)
                }
            }
            fieldMap.isEmpty() -> HttpHelper
                .getApiService<HttpGetApi>(baseUrl, charset)
                .get(url, headerMap)
            else -> HttpHelper
                .getApiService<HttpGetApi>(baseUrl, charset)
                .getMap(url, fieldMap, headerMap)
        }
    }

    @Throws(Exception::class)
    suspend fun getResponseAwait(
        tag: String,
        jsStr: String? = null,
        sourceRegex: String? = null
    ): Res {
        if (useWebView) {
            val params = AjaxWebView.AjaxParams(url)
            params.headerMap = headerMap
            params.requestMethod = method
            params.javaScript = jsStr
            params.sourceRegex = sourceRegex
            params.postData = bodyTxt?.toByteArray()
            params.tag = tag
            return HttpHelper.ajax(params)
        }
        val cookie = CookieStore.getCookie(tag)
        if (cookie.isNotEmpty()) {
            headerMap["Cookie"] = cookie
        }
        val res = when {
            method == RequestMethod.POST -> {
                if (fieldMap.isNotEmpty()) {
                    HttpHelper
                        .getApiService<HttpPostApi>(baseUrl, charset)
                        .postMapAsync(url, fieldMap, headerMap)
                } else {
                    HttpHelper
                        .getApiService<HttpPostApi>(baseUrl, charset)
                        .postBodyAsync(url, body!!, headerMap)
                }
            }
            fieldMap.isEmpty() -> HttpHelper
                .getApiService<HttpGetApi>(baseUrl, charset)
                .getAsync(url, headerMap)
            else -> HttpHelper
                .getApiService<HttpGetApi>(baseUrl, charset)
                .getMapAsync(url, fieldMap, headerMap)
        }
        return Res(NetworkUtils.getUrl(res), res.body())
    }

}
