package com.hsr.railfocus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 专注类型实体
 */
@Entity(tableName = "focus_types")
data class FocusTypeEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val iconName: String,
    val colorHex: Int,
    val containerColorHex: Int,
    val isRemovable: Boolean = true,
    val order: Int = 0,
)
