package io.legado.app.lib.webdav

import android.annotation.SuppressLint
import io.legado.app.constant.AppLog
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.printOnDebug
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class WebDav(urlStr: String, val authorization: Authorization) {
    companion object {

        @SuppressLint("SimpleDateFormat")
        private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZ")

        // 指定返回哪些属性
        @Language("xml")
        private const val DIR =
            """<?xml version="1.0"?>
            <a:propfind xmlns:a="DAV:">
                <a:prop>
                    <a:displayname/>
                    <a:resourcetype/>
                    <a:getcontentlength/>
                    <a:creationdate/>
                    <a:getlastmodified/>
                    %s
                </a:prop>
            </a:propfind>"""
    }

    private val url: URL = URL(urlStr)
    private val httpUrl: String? by lazy {
        val raw = url.toString().replace("davs://", "https://").replace("dav://", "http://")
        return@lazy kotlin.runCatching {
            URLEncoder.encode(raw, "UTF-8")
                .replace("\\+".toRegex(), "%20")
                .replace("%3A".toRegex(), ":")
                .replace("%2F".toRegex(), "/")
        }.getOrNull()
    }
    val host: String? get() = url.host
    val path get() = url.toString()


    /**
     * 填充文件信息。实例化WebDAVFile对象时，并没有将远程文件的信息填充到实例中。需要手动填充！
     * @return 远程文件是否存在
     */
    suspend fun indexFileInfo(): Boolean {
        return !propFindResponse(ArrayList()).isNullOrEmpty()
    }

    /**
     * 列出当前路径下的文件
     *
     * @return 文件列表
     */
    @Throws(WebDavException::class)
    suspend fun listFiles(): List<WebDavFile> {
        propFindResponse()?.let { body ->
            return parseDir(body)
        }
        return ArrayList()
    }

    /**
     * @param propsList 指定列出文件的哪些属性
     */
    @Throws(WebDavException::class)
    private suspend fun propFindResponse(
        propsList: List<String> = emptyList(),
        depth: Int = 1
    ): String? {
        val requestProps = StringBuilder()
        for (p in propsList) {
            requestProps.append("<a:").append(p).append("/>\n")
        }
        val requestPropsStr: String = if (requestProps.toString().isEmpty()) {
            DIR.replace("%s", "")
        } else {
            String.format(DIR, requestProps.toString() + "\n")
        }
        val url = httpUrl ?: return null
        return okHttpClient.newCallResponse {
            url(url)
            addHeader(authorization.name, authorization.data)
            addHeader("Depth", depth.toString())
            // 添加RequestBody对象，可以只返回的属性。如果设为null，则会返回全部属性
            // 注意：尽量手动指定需要返回的属性。若返回全部属性，可能后由于Prop.java里没有该属性名，而崩溃。
            val requestBody = requestPropsStr.toRequestBody("text/plain".toMediaType())
            method("PROPFIND", requestBody)
        }.apply {
            checkResult(this)
        }.body?.text()
    }

    private fun parseDir(s: String): List<WebDavFile> {
        val list = ArrayList<WebDavFile>()
        val document = Jsoup.parse(s)
        val elements = document.getElementsByTag("d:response")
        httpUrl?.let { urlStr ->
            val baseUrl = if (urlStr.endsWith("/")) urlStr else "$urlStr/"
            for (element in elements) {
                val href = element.getElementsByTag("d:href")[0].text()
                if (!href.endsWith("/")) {
                    val fileName = href.substring(href.lastIndexOf("/") + 1)
                    val webDavFile: WebDav
                    try {
                        val urlName = href.ifEmpty {
                            url.file.replace("/", "")
                        }
                        val contentType = element
                            .getElementsByTag("d:getcontenttype")
                            .firstOrNull()?.text().orEmpty()
                        val size = kotlin.runCatching {
                            element.getElementsByTag("d:getcontentlength")
                                .firstOrNull()?.text()?.toLong() ?: 0
                        }.getOrDefault(0)
                        val lastModify: Long = kotlin.runCatching {
                            element.getElementsByTag("d:getcontentlength")
                                .firstOrNull()?.text()?.let {
                                    dateFormat.parse(it)
                                }
                        }.getOrNull()?.time ?: 0
                        webDavFile = WebDavFile(
                            baseUrl + fileName,
                            authorization,
                            displayName = fileName,
                            urlName = urlName,
                            size = size,
                            contentType = contentType,
                            lastModify = lastModify
                        )
                        list.add(webDavFile)
                    } catch (e: MalformedURLException) {
                        e.printOnDebug()
                    }
                }
            }
        }
        return list
    }

    /**
     * 文件是否存在
     */
    suspend fun exists(): Boolean {
        return kotlin.runCatching {
            val response = propFindResponse(depth = 0) ?: return false
            val document = Jsoup.parse(response)
            val elements = document.getElementsByTag("d:response")
            return elements.isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * 根据自己的URL，在远程处创建对应的文件夹
     * @return 是否创建成功
     */
    suspend fun makeAsDir(): Boolean {
        val url = httpUrl ?: return false
        //防止报错
        return kotlin.runCatching {
            if (!exists()) {
                okHttpClient.newCallResponse {
                    url(url)
                    method("MKCOL", null)
                    addHeader(authorization.name, authorization.data)
                }.let {
                    checkResult(it)
                }
            }
        }.onFailure {
            AppLog.put("WebDav创建目录失败\n${it.localizedMessage}")
        }.isSuccess
    }

    /**
     * 下载到本地
     * @param savedPath       本地的完整路径，包括最后的文件名
     * @param replaceExisting 是否替换本地的同名文件
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(WebDavException::class)
    suspend fun downloadTo(savedPath: String, replaceExisting: Boolean) {
        val file = File(savedPath)
        if (file.exists() && !replaceExisting) {
            return
        }
        downloadInputStream().use { byteStream ->
            FileOutputStream(file).use {
                byteStream.copyTo(it)
            }
        }
    }

    /**
     * 下载文件,返回ByteArray
     */
    @Throws(WebDavException::class)
    suspend fun download(): ByteArray {
        return downloadInputStream().use {
            it.readBytes()
        }
    }

    /**
     * 上传文件
     */
    @Throws(WebDavException::class)
    suspend fun upload(
        localPath: String,
        contentType: String = "application/octet-stream"
    ) {
        kotlin.runCatching {
            val file = File(localPath)
            if (!file.exists()) throw WebDavException("文件不存在")
            // 务必注意RequestBody不要嵌套，不然上传时内容可能会被追加多余的文件信息
            val fileBody = file.asRequestBody(contentType.toMediaType())
            val url = httpUrl ?: throw WebDavException("url不能为空")
            okHttpClient.newCallResponse {
                url(url)
                put(fileBody)
                addHeader(authorization.name, authorization.data)
            }.let {
                checkResult(it)
            }
        }.onFailure {
            throw WebDavException("WebDav上传失败\n${it.localizedMessage}")
        }
    }

    @Throws(WebDavException::class)
    suspend fun upload(byteArray: ByteArray, contentType: String) {
        // 务必注意RequestBody不要嵌套，不然上传时内容可能会被追加多余的文件信息
        kotlin.runCatching {
            val fileBody = byteArray.toRequestBody(contentType.toMediaType())
            val url = httpUrl ?: throw NoStackTraceException("url不能为空")
            okHttpClient.newCallResponse {
                url(url)
                put(fileBody)
                addHeader(authorization.name, authorization.data)
            }.let {
                checkResult(it)
            }
        }.onFailure {
            throw WebDavException("WebDav上传失败\n${it.localizedMessage}")
        }
    }

    @Throws(WebDavException::class)
    private suspend fun downloadInputStream(): InputStream {
        val url = httpUrl ?: throw WebDavException("WebDav下载出错\nurl为空")
        val byteStream = okHttpClient.newCallResponse {
            url(url)
            addHeader(authorization.name, authorization.data)
        }.apply {
            checkResult(this)
        }.body?.byteStream()
        return byteStream ?: throw WebDavException("WebDav下载出错\nNull Exception")
    }

    private fun checkResult(response: Response) {
        if (!response.isSuccessful) {
            throw WebDavException("${url}\n${response.code}:${response.message}")
        }
    }

}