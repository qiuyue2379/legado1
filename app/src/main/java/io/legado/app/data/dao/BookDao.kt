package io.legado.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress

@Dao
interface BookDao {

    @Query("SELECT * FROM books order by durChapterTime desc")
    fun observeAll(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE type = ${BookType.audio} order by durChapterTime desc")
    fun observeAudio(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE origin = '${BookType.local}' order by durChapterTime desc")
    fun observeLocal(): LiveData<List<Book>>

    @Query("SELECT bookUrl FROM books WHERE origin = '${BookType.local}' order by durChapterTime desc")
    fun observeLocalUri(): LiveData<List<String>>

    @Query("SELECT * FROM books WHERE origin <> '${BookType.local}' and type = 0 order by durChapterTime desc")
    fun observeDownload(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun observeByGroup(group: Int): LiveData<List<Book>>

    @Query("select * from books where (SELECT sum(groupId) FROM book_groups) & `group` = 0")
    fun observeNoGroup(): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE name like '%'||:key||'%' or author like '%'||:key||'%'")
    fun liveDataSearch(key: String): LiveData<List<Book>>

    @Query("SELECT * FROM books WHERE `name` in (:names)")
    fun findByName(vararg names: String): List<Book>

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun getBook(bookUrl: String): Book?

    @get:Query("SELECT * FROM books where origin <> '${BookType.local}' and type = 0")
    val webBooks: List<Book>

    @get:Query("SELECT * FROM books where origin <> '${BookType.local}' and canUpdate = 1")
    val hasUpdateBooks: List<Book>

    @get:Query("SELECT * FROM books")
    val all: List<Book>

    @get:Query("SELECT * FROM books where type = 0 ORDER BY durChapterTime DESC limit 1")
    val lastReadBook: Book?

    @get:Query("SELECT bookUrl FROM books")
    val allBookUrls: List<String>

    @get:Query("SELECT COUNT(*) FROM books")
    val allBookCount: Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg book: Book)

    @Update
    fun update(vararg book: Book)

    @Delete
    fun delete(vararg book: Book)

    @Query("update books set durChapterPos = :pos where bookUrl = :bookUrl")
    fun upProgress(bookUrl: String, pos: Int)

    @Query("update books set `group` = :newGroupId where `group` = :oldGroupId")
    fun upGroup(oldGroupId: Int, newGroupId: Int)

    @get:Query("select bookUrl, durChapterIndex, durChapterPos, durChapterTime, durChapterTitle from books")
    val allBookProgress: List<BookProgress>

    @Query(
        """
        update books set 
        durChapterIndex = :durChapterIndex, durChapterPos = :durChapterPos, 
        durChapterTime = :durChapterTime, durChapterTitle = :durChapterTitle
        where bookUrl = :bookUrl and durChapterTime < :durChapterTime
    """
    )
    fun upBookProgress(
        bookUrl: String,
        durChapterIndex: Int,
        durChapterPos: Int,
        durChapterTime: Long,
        durChapterTitle: String?
    )
}