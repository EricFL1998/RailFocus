package com.hsr.railfocus.data.local.dataaccess

import androidx.room.Dao
import androidx.room.Query
import com.hsr.railfocus.data.local.entity.EdgeEntity

@Dao
interface EdgeDataAccess {
    @Query("SELECT * FROM edges")
    suspend fun getAllEdges(): List<EdgeEntity>
}
