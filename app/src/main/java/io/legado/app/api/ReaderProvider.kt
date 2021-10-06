/*
 * Copyright (C) 2020 w568w
 */
package io.legado.app.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.google.gson.Gson
import io.legado.app.api.controller.BookController
import io.legado.app.api.controller.BookSourceController
import java.util.*

/**
 * Export book data to other app.
 */
class ReaderProvider : ContentProvider() {
    private enum class RequestCode {
        SaveSource, SaveSources, SaveBook, DeleteSources, GetSource, GetSources,
        GetBookshelf, RefreshToc, GetChapterList, GetBookContent, GetBookCover
    }

    private val postBodyKey = "json"
    private val sMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            "${context?.applicationInfo?.packageName}.readerProvider".also { authority ->
                addURI(authority, "bookSource/insert", RequestCode.SaveSource.ordinal)
                addURI(authority, "sources/insert", RequestCode.SaveSources.ordinal)
                addURI(authority, "book/insert", RequestCode.SaveBook.ordinal)
                addURI(authority, "sources/delete", RequestCode.DeleteSources.ordinal)
                addURI(authority, "bookSource/query", RequestCode.GetSource.ordinal)
                addURI(authority, "sources/query", RequestCode.GetSources.ordinal)
                addURI(authority, "books/query", RequestCode.GetBookshelf.ordinal)
                addURI(authority, "book/refreshToc/query", RequestCode.RefreshToc.ordinal)
                addURI(authority, "book/chapter/query", RequestCode.GetChapterList.ordinal)
                addURI(authority, "book/content/query", RequestCode.GetBookContent.ordinal)
                addURI(authority, "book/cover/query", RequestCode.GetBookCover.ordinal)
            }
        }
    }

    override fun onCreate() = false

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        if (sMatcher.match(uri) < 0) return -1
        when (RequestCode.values()[sMatcher.match(uri)]) {
            RequestCode.DeleteSources -> BookSourceController.deleteSources(selection)
            else -> throw IllegalStateException(
                "Unexpected value: " + RequestCode.values()[sMatcher.match(uri)].name
            )
        }
        return 0
    }

    override fun getType(uri: Uri) = throw UnsupportedOperationException("Not yet implemented")

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (sMatcher.match(uri) < 0) return null
        when (RequestCode.values()[sMatcher.match(uri)]) {
            RequestCode.SaveSource -> values?.let {
                BookSourceController.saveSource(values.getAsString(postBodyKey))
            }
            RequestCode.SaveBook -> values?.let {
                BookController.saveBook(values.getAsString(postBodyKey))
            }
            RequestCode.SaveSources -> values?.let {
                BookSourceController.saveSources(values.getAsString(postBodyKey))
            }
            else -> throw IllegalStateException(
                "Unexpected value: " + RequestCode.values()[sMatcher.match(uri)].name
            )
        }
        return null
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val map: MutableMap<String, ArrayList<String>> = HashMap()
        uri.getQueryParameter("url")?.let {
            map["url"] = arrayListOf(it)
        }
        uri.getQueryParameter("index")?.let {
            map["index"] = arrayListOf(it)
        }
        uri.getQueryParameter("path")?.let {
            map["path"] = arrayListOf(it)
        }
        return if (sMatcher.match(uri) < 0) null else when (RequestCode.values()[sMatcher.match(uri)]) {
            RequestCode.GetSource -> SimpleCursor(BookSourceController.getSource(map))
            RequestCode.GetSources -> SimpleCursor(BookSourceController.sources)
            RequestCode.GetBookshelf -> SimpleCursor(BookController.bookshelf)
            RequestCode.GetBookContent -> SimpleCursor(BookController.getBookContent(map))
            RequestCode.RefreshToc -> SimpleCursor(BookController.refreshToc(map))
            RequestCode.GetChapterList -> SimpleCursor(BookController.getChapterList(map))
            RequestCode.GetBookCover -> SimpleCursor(BookController.getCover(map))
            else -> throw IllegalStateException(
                "Unexpected value: " + RequestCode.values()[sMatcher.match(uri)].name
            )
        }
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ) = throw UnsupportedOperationException("Not yet implemented")


    /**
     * Simple inner class to deliver json callback data.
     *
     * Only getString() makes sense.
     */
    private class SimpleCursor(data: ReturnData?) : MatrixCursor(arrayOf("result"), 1) {

        private val mData: String = Gson().toJson(data)

        init {
            addRow(arrayOf(mData))
        }

    }
}