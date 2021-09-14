package io.legado.app.utils

import android.util.Base64
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Suppress("unused")
object EncoderUtils {

    fun escape(src: String): String {
        val tmp = StringBuilder()
        for (char in src) {
            val charCode = char.code
            if (charCode in 48..57 || charCode in 65..90 || charCode in 97..122) {
                tmp.append(char)
                continue
            }

            val prefix = when {
                charCode < 16 -> "%0"
                charCode < 256 -> "%"
                else -> "%u"
            }
            tmp.append(prefix).append(charCode.toString(16))
        }
        return tmp.toString()
    }

    @JvmOverloads
    fun base64Decode(str: String, flags: Int = Base64.DEFAULT): String {
        val bytes = Base64.decode(str, flags)
        return String(bytes)
    }

    @JvmOverloads
    fun base64Encode(str: String, flags: Int = Base64.NO_WRAP): String? {
        return Base64.encodeToString(str.toByteArray(), flags)
    }

    //////////AES Start

    /**
     * Return the Base64-encode bytes of AES encryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param transformation The name of the transformation,
     * 加密算法/加密模式/填充类型, *DES/CBC/PKCS5Padding*.
     * @param iv             The buffer with the IV. The contents of the
     * buffer are copied to protect against subsequent modification.
     * @return the Base64-encode bytes of AES encryption
     */
    @Throws(Exception::class)
    fun encryptAES2Base64(
        data: ByteArray?,
        key: ByteArray?,
        transformation: String? = "DES/ECB/PKCS5Padding",
        iv: ByteArray? = null
    ): ByteArray? {
        return Base64.encode(encryptAES(data, key, transformation, iv), Base64.NO_WRAP)
    }

    /**
     * Return the bytes of AES encryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param transformation The name of the transformation,
     * 加密算法/加密模式/填充类型, *DES/CBC/PKCS5Padding*.
     * @param iv             The buffer with the IV. The contents of the
     * buffer are copied to protect against subsequent modification.
     * @return the bytes of AES encryption
     */
    @Throws(Exception::class)
    fun encryptAES(
        data: ByteArray?,
        key: ByteArray?,
        transformation: String? = "DES/ECB/PKCS5Padding",
        iv: ByteArray? = null
    ): ByteArray? {
        return symmetricTemplate(data, key, "AES", transformation!!, iv, true)
    }


    /**
     * Return the bytes of AES decryption for Base64-encode bytes.
     *
     * @param data           The data.
     * @param key            The key.
     * @param transformation The name of the transformation,
     * 加密算法/加密模式/填充类型, *DES/CBC/PKCS5Padding*.
     * @param iv             The buffer with the IV. The contents of the
     * buffer are copied to protect against subsequent modification.
     * @return the bytes of AES decryption for Base64-encode bytes
     */
    @Throws(Exception::class)
    fun decryptBase64AES(
        data: ByteArray?,
        key: ByteArray?,
        transformation: String = "DES/ECB/PKCS5Padding",
        iv: ByteArray? = null
    ): ByteArray? {
        return decryptAES(Base64.decode(data, Base64.NO_WRAP), key, transformation, iv)
    }

    /**
     * Return the bytes of AES decryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param transformation The name of the transformation,
     * 加密算法/加密模式/填充类型, *DES/CBC/PKCS5Padding*.
     * @param iv             The buffer with the IV. The contents of the
     * buffer are copied to protect against subsequent modification.
     * @return the bytes of AES decryption
     */
    @Throws(Exception::class)
    fun decryptAES(
        data: ByteArray?,
        key: ByteArray?,
        transformation: String = "DES/ECB/PKCS5Padding",
        iv: ByteArray? = null
    ): ByteArray? {
        return symmetricTemplate(data, key, "AES", transformation, iv, false)
    }


    /**
     * Return the bytes of symmetric encryption or decryption.
     *
     * @param data           The data.
     * @param key            The key.
     * @param algorithm      The name of algorithm.
     * @param transformation The name of the transformation,
     * 加密算法/加密模式/填充类型, <i>DES/CBC/PKCS5Padding</i>.
     * @param iv             The buffer with the IV. The contents of the
     * buffer are copied to protect against subsequent modification.
     * @param isEncrypt      True to encrypt, false otherwise.
     * @return the bytes of symmetric encryption or decryption
     */
    @Suppress("SameParameterValue")
    @Throws(Exception::class)
    private fun symmetricTemplate(
        data: ByteArray?,
        key: ByteArray?,
        algorithm: String,
        transformation: String,
        iv: ByteArray?,
        isEncrypt: Boolean
    ): ByteArray? {
        return if (data == null || data.isEmpty() || key == null || key.isEmpty()) null
        else {
            val keySpec = SecretKeySpec(key, algorithm)
            val cipher = Cipher.getInstance(transformation)
            val mode = if (isEncrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
            if (iv == null || iv.isEmpty()) {
                cipher.init(mode, keySpec)
            } else {
                val params: AlgorithmParameterSpec = IvParameterSpec(iv)
                cipher.init(mode, keySpec, params)
            }
            cipher.doFinal(data)
        }
    }


}