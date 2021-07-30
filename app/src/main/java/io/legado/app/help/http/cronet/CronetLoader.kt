package io.legado.app.help.http.cronet

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.text.TextUtils
import android.util.Log
import org.chromium.net.CronetEngine
import org.chromium.net.impl.ImplVersion
import splitties.init.appCtx
import java.io.*
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object CronetLoader : CronetEngine.Builder.LibraryLoader() {
    //https://storage.googleapis.com/chromium-cronet/android/92.0.4515.127/Release/cronet/libs/arm64-v8a/libcronet.92.0.4515.127.so
    //https://cdn.jsdelivr.net/gh/ag2s20150909/cronet-repo@92.0.4515.127/cronet/92.0.4515.127/arm64-v8a/libcronet.92.0.4515.127.so.js
    private const val TAG = "CronetLoader"
    private val soName = "libcronet." + ImplVersion.getCronetVersion() + ".so"
    private val soUrl: String
    private val md5Url: String
    private val soFile: File
    private val downloadFile: File
    private var cpuAbi: String? = null
    private var md5: String? = null
    var download = false
    private var executor: Executor = Executors.newSingleThreadExecutor()


    init {
        soUrl = ("https://storage.googleapis.com/chromium-cronet/android/"
                + ImplVersion.getCronetVersion() + "/Release/cronet/libs/"
                + getCpuAbi(appCtx) + "/" + soName)
        md5Url = ("https://cdn.jsdelivr.net/gh/ag2s20150909/cronet-repo@" +
                ImplVersion.getCronetVersion() + "/cronet/" + ImplVersion.getCronetVersion() + "/"
                + getCpuAbi(appCtx) + "/" + soName + ".js")
        val dir = appCtx.getDir("lib", Context.MODE_PRIVATE)
        soFile = File(dir.toString() + "/" + getCpuAbi(appCtx), soName)
        downloadFile = File(appCtx.cacheDir.toString() + "/so_download", soName)
        Log.e(TAG, "soName+:$soName")
        Log.e(TAG, "destSuccessFile:$soFile")
        Log.e(TAG, "tempFile:$downloadFile")
        Log.e(TAG, "soUrl:$soUrl")
    }

    fun install(): Boolean {
        return soFile.exists()
    }

    fun preDownload() {
        Thread {
            md5 = getUrlMd5(md5Url)
            if (soFile.exists() && md5 == getFileMD5(soFile)) {
                Log.e(TAG, "So 库已存在")
            } else {
                download(soUrl, md5, downloadFile, soFile)
            }
            Log.e(TAG, soName)
        }.start()
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    override fun loadLibrary(libName: String) {
        Log.e(TAG, "libName:$libName")
        val start = System.currentTimeMillis()
        @Suppress("SameParameterValue")
        try {
            //非cronet的so调用系统方法加载
            if (!libName.contains("cronet")) {
                System.loadLibrary(libName)
                return
            }
            //以下逻辑为cronet加载，优先加载本地，否则从远程加载
            //首先调用系统行为进行加载
            System.loadLibrary(libName)
            Log.i(TAG, "load from system")
        } catch (e: Throwable) {
            //如果找不到，则从远程下载
            //删除历史文件
            deleteHistoryFile(Objects.requireNonNull(soFile.parentFile), soFile)
            md5 = getUrlMd5(md5Url)
            Log.i(TAG, "soMD5:$md5")
            if (md5 == null || md5!!.length != 32 || soUrl.isEmpty()) {
                //如果md5或下载的url为空，则调用系统行为进行加载
                System.loadLibrary(libName)
                return
            }
            if (!soFile.exists() || !soFile.isFile) {
                soFile.delete()
                download(soUrl, md5, downloadFile, soFile)
                //如果文件不存在或不是文件，则调用系统行为进行加载
                System.loadLibrary(libName)
                return
            }
            if (soFile.exists()) {
                //如果文件存在，则校验md5值
                val fileMD5 = getFileMD5(soFile)
                if (fileMD5 != null && fileMD5.equals(md5, ignoreCase = true)) {
                    //md5值一样，则加载
                    System.load(soFile.absolutePath)
                    Log.e(TAG, "load from:$soFile")
                    return
                }
                //md5不一样则删除
                soFile.delete()
            }
            //不存在则下载
            download(soUrl, md5, downloadFile, soFile)
            //使用系统加载方法
            System.loadLibrary(libName)
        } finally {
            Log.e(TAG, "time:" + (System.currentTimeMillis() - start))
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getCpuAbi(context: Context): String? {
        if (cpuAbi != null) {
            return cpuAbi
        }
        // 5.0以上Application才有primaryCpuAbi字段
        try {
            val appInfo = context.applicationInfo
            val abiField = ApplicationInfo::class.java.getDeclaredField("primaryCpuAbi")
            abiField.isAccessible = true
            cpuAbi = abiField[appInfo] as String
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (TextUtils.isEmpty(cpuAbi)) {
            cpuAbi = Build.SUPPORTED_ABIS[0]
        }

        //貌似只有这个过时了的API能获取当前APP使用的ABI
        return cpuAbi
    }

    @Suppress("SameParameterValue")
    private fun getUrlMd5(url: String): String? {
        if (md5 != null && md5!!.length == 32) {
            return md5
        }
        val inputStream: InputStream
        val outputStream: OutputStream
        return try {
            outputStream = ByteArrayOutputStream()
            val connection = URL(url).openConnection() as HttpURLConnection
            inputStream = connection.inputStream
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                outputStream.flush()
            }
            outputStream.toString()
        } catch (e: IOException) {
            null
        }
    }

    /**
     * 删除历史文件
     */
    private fun deleteHistoryFile(dir: File, currentFile: File?) {
        val files = dir.listFiles()
        @Suppress("SameParameterValue")
        if (files != null && files.isNotEmpty()) {
            for (f in files) {
                if (f.exists() && (currentFile == null || f.absolutePath != currentFile.absolutePath)) {
                    val delete = f.delete()
                    Log.e(TAG, "delete file: $f result: $delete")
                    if (!delete) {
                        f.deleteOnExit()
                    }
                }
            }
        }
    }

    /**
     * 下载文件
     */
    private fun downloadFileIfNotExist(url: String, destFile: File): Boolean {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            inputStream = connection.inputStream
            if (destFile.exists()) {
                return true
            }
            destFile.parentFile!!.mkdirs()
            destFile.createNewFile()
            outputStream = FileOutputStream(destFile)
            val buffer = ByteArray(32768)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                outputStream.flush()
            }
            return true
        } catch (e: Throwable) {
            e.printStackTrace()
            if (destFile.exists() && !destFile.delete()) {
                destFile.deleteOnExit()
            }
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return false
    }

    /**
     * 下载并拷贝文件
     */
    @Suppress("SameParameterValue")
    @Synchronized
    private fun download(
        url: String,
        md5: String?,
        downloadTempFile: File,
        destSuccessFile: File
    ) {
        if (download) {
            return
        }
        download = true
        executor.execute {
            val result = downloadFileIfNotExist(url, downloadTempFile)
            Log.e(TAG, "download result:$result")
            //文件md5再次校验
            val fileMD5 = getFileMD5(downloadTempFile)
            if (md5 != null && !md5.equals(fileMD5, ignoreCase = true)) {
                val delete = downloadTempFile.delete()
                if (!delete) {
                    downloadTempFile.deleteOnExit()
                }
                download = false
                return@execute
            }
            Log.e(TAG, "download success, copy to $destSuccessFile")
            //下载成功拷贝文件
            copyFile(downloadTempFile, destSuccessFile)
            val parentFile = downloadTempFile.parentFile
            @Suppress("SameParameterValue")
            deleteHistoryFile(parentFile!!, null)
        }
    }

    /**
     * 拷贝文件
     */
    private fun copyFile(source: File?, dest: File?): Boolean {
        if (source == null || !source.exists() || !source.isFile || dest == null) {
            return false
        }
        if (source.absolutePath == dest.absolutePath) {
            return true
        }
        var fileInputStream: FileInputStream? = null
        var os: FileOutputStream? = null
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) {
            val mkdirs = parent.mkdirs()
            if (!mkdirs) {
                parent.mkdirs()
            }
        }
        try {
            fileInputStream = FileInputStream(source)
            os = FileOutputStream(dest, false)
            val buffer = ByteArray(1024 * 512)
            var length: Int
            while (fileInputStream.read(buffer).also { length = it } > 0) {
                os.write(buffer, 0, length)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (os != null) {
                try {
                    os.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return false
    }

    /**
     * 获得文件md5
     */
    private fun getFileMD5(file: File): String? {
        var fileInputStream: FileInputStream? = null
        try {
            fileInputStream = FileInputStream(file)
            val md5 = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(1024)
            var numRead: Int
            while (fileInputStream.read(buffer).also { numRead = it } > 0) {
                md5.update(buffer, 0, numRead)
            }
            return String.format("%032x", BigInteger(1, md5.digest())).lowercase()
        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return null
    }

}