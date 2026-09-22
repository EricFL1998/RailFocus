package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hsr.railfocus.data.local.entity.FocusTypeEntity
import kotlinx.coroutines.flow.Flow

/**
 * 专注类型数据访问接口
 */
@Dao
interface FocusTypeDataAccess {
    @Query("SELECT * FROM focus_types ORDER BY `order` ASC")
    fun getAllFlow(): Flow<List<FocusTypeEntity>>

    @Query("SELECT * FROM focus_types ORDER BY `order` ASC")
    suspend fun getAllList(): List<FocusTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(focusType: FocusTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(focusTypes: List<FocusTypeEntity>)

    @Query("DELETE FROM focus_types WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM focus_types")
    suspend fun getCount(): Int

    @Query("DELETE FROM focus_types")
    suspend fun deleteAll()
}
