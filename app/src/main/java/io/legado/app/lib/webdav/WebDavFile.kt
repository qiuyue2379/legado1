package io.legado.app.lib.webdav

/**
 * webDavFile
 */
@Suppress("unused")
class WebDavFile(
    urlStr: String,
    authorization: Authorization,
    val displayName: String,
    val urlName: String,
    val size: Long,
    val contentType: String,
    val lastModify: Long
) : WebDav(urlStr, authorization)