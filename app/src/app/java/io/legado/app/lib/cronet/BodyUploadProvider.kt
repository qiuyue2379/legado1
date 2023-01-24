package io.legado.app.lib.cronet

import androidx.annotation.Keep
import okhttp3.RequestBody
import okio.Buffer
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import java.io.IOException
import java.nio.ByteBuffer

@Keep
class BodyUploadProvider(private val body: RequestBody) : UploadDataProvider(), AutoCloseable {
    private val buffer = Buffer()

    init {
        try {
            body.writeTo(buffer)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    override fun getLength(): Long {
        return body.contentLength()
    }

    @Throws(IOException::class)
    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        check(byteBuffer.hasRemaining()) { "Cronet passed a buffer with no bytes remaining" }
        var read: Int
        var bytesRead = 0
        while (bytesRead == 0) {
            read = buffer.read(byteBuffer)
            bytesRead += read
        }
        uploadDataSink.onReadSucceeded(false)
    }

    @Throws(IOException::class)
    override fun rewind(uploadDataSink: UploadDataSink) {
        buffer.clear()
        body.writeTo(buffer)
        uploadDataSink.onRewindSucceeded()
    }

    @Throws(IOException::class)
    override fun close() {
        buffer.close()
        super.close()
    }
}
