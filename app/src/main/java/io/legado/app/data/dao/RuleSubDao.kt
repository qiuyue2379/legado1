package io.legado.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.legado.app.data.entities.RuleSub

@Dao
interface RuleSubDao {

    @get:Query("select * from ruleSubs order by customOrder")
    val all: List<RuleSub>

    @Query("select * from ruleSubs order by customOrder")
    fun observeAll(): LiveData<List<RuleSub>>

    @get:Query("select customOrder from ruleSubs order by customOrder limit 0,1")
    val maxOrder: Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg ruleSub: RuleSub)

    @Delete
    fun delete(vararg ruleSub: RuleSub)

    @Update
    fun update(vararg ruleSub: RuleSub)
}