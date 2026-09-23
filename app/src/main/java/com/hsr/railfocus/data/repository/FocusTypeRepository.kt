package com.hsr.railfocus.data.repository

import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.domain.model.FocusType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专注类型仓库
 */
@Singleton
class FocusTypeRepository @Inject constructor(
    private val focusTypeDataAccess: FocusTypeDataAccess,
) {
    /**
     * 获取所有专注类型流，如果数据库为空则初始化默认值
     */
    fun getFocusTypesFlow(): Flow<List<FocusType>> {
        return focusTypeDataAccess.getAllFlow().map { entities ->
            if (entities.isEmpty()) {
                initializeDefaults()
                FocusType.DEFAULT_LIST
            } else {
                val dbList = entities.map { FocusType.fromEntity(it) }
                // 强制同步 DEFAULT_LIST 的中文显示名称（通过 ID 匹配）
                dbList.map { dbType ->
                    FocusType.DEFAULT_LIST.find { it.id == dbType.id }?.let { defType ->
                        dbType.copy(displayName = defType.displayName)
                    } ?: dbType
                }
            }
        }
    }

    /**
     * 初始化默认专注类型
     */
    private suspend fun initializeDefaults() {
        if (focusTypeDataAccess.getCount() == 0) {
            val entities = FocusType.DEFAULT_LIST.mapIndexed { index, focusType ->
                focusType.toEntity(order = index)
            }
            focusTypeDataAccess.insertAll(entities)
        }
    }

    /**
     * 添加或更新专注类型
     */
    suspend fun saveFocusType(focusType: FocusType, order: Int = 0) {
        focusTypeDataAccess.insert(focusType.toEntity(order))
    }

    /**
     * 删除专注类型
     */
    suspend fun deleteFocusType(id: String) {
        focusTypeDataAccess.deleteById(id)
    }

    /**
     * 清除所有专注类型
     */
    suspend fun clearAll() {
        focusTypeDataAccess.deleteAll()
        initializeDefaults()
    }
}
