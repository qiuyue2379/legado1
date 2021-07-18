package io.legado.app.help.http.cronet

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class CronetInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return proceedWithCronet(chain.request(), chain.call())
    }

    @Throws(IOException::class)
    private fun proceedWithCronet(request: Request, call: Call): Response {
        val callback = CronetUrlRequestCallback(request, call)
        val urlRequest = buildRequest(request, callback)
        urlRequest.start()
        return callback.waitForDone()
    }

}