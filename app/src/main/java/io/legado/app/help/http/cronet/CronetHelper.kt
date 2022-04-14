package io.legado.app.help.http.cronet

import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.DebugLog
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okio.Buffer
import org.chromium.net.CronetEngine.Builder.HTTP_CACHE_DISK
import org.chromium.net.ExperimentalCronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import splitties.init.appCtx


val cronetEngine: ExperimentalCronetEngine? by lazy {
    if (!AppConfig.isGooglePlay) {
        CronetLoader.preDownload()
    }
    val builder = ExperimentalCronetEngine.Builder(appCtx).apply {
        if (!AppConfig.isGooglePlay && CronetLoader.install()) {
            setLibraryLoader(CronetLoader)//设置自定义so库加载
        }
        setStoragePath(appCtx.externalCacheDir?.absolutePath)//设置缓存路径
        enableHttpCache(HTTP_CACHE_DISK, (1024 * 1024 * 50).toLong())//设置50M的磁盘缓存
        enableQuic(true)//设置支持http/3
        enableHttp2(true)  //设置支持http/2
        enablePublicKeyPinningBypassForLocalTrustAnchors(true)
        enableBrotli(true)//Brotli压缩
    }
    try {
        val engine = builder.build()
        DebugLog.d("Cronet Version:", engine.versionString)
        return@lazy engine
    } catch (e: UnsatisfiedLinkError) {
        AppLog.put("初始化cronetEngine出错", e)
        return@lazy null
    }
}

fun buildRequest(request: Request, callback: UrlRequest.Callback): UrlRequest? {
    val url = request.url.toString()
    val headers: Headers = request.headers
    val requestBody = request.body
    return cronetEngine?.newUrlRequestBuilder(
        url,
        callback,
        okHttpClient.dispatcher.executorService
    )?.apply {
        setHttpMethod(request.method)//设置
        allowDirectExecutor()
        headers.forEachIndexed { index, _ ->
            addHeader(headers.name(index), headers.value(index))
        }
        if (requestBody != null) {
            val contentType: MediaType? = requestBody.contentType()
            if (contentType != null) {
                addHeader("Content-Type", contentType.toString())
            } else {
                addHeader("Content-Type", "text/plain")
            }
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            setUploadDataProvider(
                UploadDataProviders.create(buffer.readByteArray()),
                okHttpClient.dispatcher.executorService
            )

        }

    }?.build()

}

