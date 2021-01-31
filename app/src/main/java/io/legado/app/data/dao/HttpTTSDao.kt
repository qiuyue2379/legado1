package io.legado.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.legado.app.data.entities.HttpTTS

@Dao
interface HttpTTSDao {

    @get:Query("select * from httpTTS order by name")
    val all: List<HttpTTS>

    @Query("select * from httpTTS order by name")
    fun observeAll(): LiveData<List<HttpTTS>>

    @get:Query("select count(*) from httpTTS")
    val count: Int

    @Query("select * from httpTTS where id = :id")
    fun get(id: Long): HttpTTS?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg httpTTS: HttpTTS)

    @Delete
    fun delete(vararg httpTTS: HttpTTS)

    @Update
    fun update(vararg httpTTS: HttpTTS)

    @Query("delete from httpTTS where id < 0")
    fun deleteDefault()
}