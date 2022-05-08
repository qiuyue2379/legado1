package io.legado.app.ui.book.read.page.provider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.collection.LruCache
import io.legado.app.R
import io.legado.app.constant.AppLog.putDebug
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.BookHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.localBook.EpubFile
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageProvider {

    private val errorBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.image_loading_error)
    }

    /**
     *缓存bitmap LruCache实现
     */
    private const val M = 1024 * 1024
    private val cacheSize =
        max(50 * M, min(100 * M, (Runtime.getRuntime().maxMemory() / 8).toInt()))
    val bitmapLruCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldBitmap: Bitmap,
            newBitmap: Bitmap?
        ) {
            oldBitmap.recycle()
            putDebug("ImageProvider: trigger bitmap recycle. URI: $key")
            putDebug("ImageProvider : cacheUsage ${size()}bytes / ${maxSize()}bytes")
        }
    }

    /**
     *缓存网络图片和epub图片
     */
    suspend fun cacheImage(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): File {
        return withContext(IO) {
            val vFile = BookHelp.getImage(book, src)
            if (!vFile.exists()) {
                if (book.isEpub()) {
                    EpubFile.getImage(book, src)?.use { input ->
                        val newFile = FileUtils.createFileIfNotExist(vFile.absolutePath)
                        @Suppress("BlockingMethodInNonBlockingContext")
                        FileOutputStream(newFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    BookHelp.saveImage(bookSource, book, src)
                }
            }
            return@withContext vFile
        }
    }

    /**
     *获取图片宽度高度信息
     */
    suspend fun getImageSize(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): Size {
        val file = cacheImage(book, src, bookSource)
        val op = BitmapFactory.Options()
        // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
        op.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, op)
        if (op.outWidth < 1 && op.outHeight < 1) {
            Coroutine.async {
                putDebug("ImageProvider: delete file due to image size ${op.outHeight}*${op.outWidth}. path: ${file.absolutePath}")
                file.delete()
            }
            return Size(errorBitmap.width, errorBitmap.height)
        }
        return Size(op.outWidth, op.outHeight)
    }

    /**
     *获取bitmap 使用LruCache缓存
     */
    fun getImage(
        book: Book,
        src: String,
        width: Int,
        height: Int? = null
    ): Bitmap {
        val cacheBitmap = bitmapLruCache.get(src)
        if (cacheBitmap != null) return cacheBitmap
        val vFile = BookHelp.getImage(book, src)
        if (!vFile.exists()) return errorBitmap
        @Suppress("BlockingMethodInNonBlockingContext")
        return kotlin.runCatching {
            val bitmap = BitmapUtils.decodeBitmap(vFile.absolutePath, width, height)
                ?: throw NoStackTraceException("解析图片失败")
            bitmapLruCache.put(src, bitmap)
            bitmap
        }.onFailure {
            putDebug(
                "ImageProvider: decode bitmap failed. path: ${vFile.absolutePath}\n$it",
                it
            )
        }.getOrDefault(errorBitmap)
    }

}
