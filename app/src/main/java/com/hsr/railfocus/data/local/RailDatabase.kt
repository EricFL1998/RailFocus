package com.hsr.railfocus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hsr.railfocus.data.local.dataaccess.EdgeDataAccess
import com.hsr.railfocus.data.local.dataaccess.StationDataAccess
import com.hsr.railfocus.data.local.entity.EdgeEntity
import com.hsr.railfocus.data.local.entity.StationEntity

/**
 * Rail Focus 静态资源数据库
 *
 * 预置数据库方案：包含车站和线路等只读数据。
 * 更新策略：当 versionCode 增加时，强制从 assets 重新复制文件，确保数据最新。
 *
 * 注意：不使用 fallbackToDestructiveMigration()。这是只读预置库，
 * 如果实体结构与 assets 中的数据库文件不一致（例如升级了表结构却忘了
 * 重新生成 rail_focus.db），应该在开发阶段直接崩溃暴露问题，
 * 而不是静默删表导致线上数据"看似正常实则全空"。
 */
@Database(
    entities = [
        StationEntity::class,
        EdgeEntity::class,
    ],
    version = 1, // 独立版本号
    exportSchema = false,
)
abstract class RailDatabase : RoomDatabase() {
    abstract fun stationDataAccess(): StationDataAccess
    abstract fun edgeDataAccess(): EdgeDataAccess

    companion object {
        const val DATABASE_NAME = "rail_static.db"
        private const val ASSET_PATH = "databases/rail_focus.db"

        @Volatile
        private var INSTANCE: RailDatabase? = null

        fun getInstance(context: Context): RailDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): RailDatabase {
            val appContext = context.applicationContext
            
            // 强制更新逻辑：比较应用版本号
            val prefs = appContext.getSharedPreferences("rail_db_prefs", Context.MODE_PRIVATE)
            val lastCopiedVersion = prefs.getLong("last_asset_version", 0L)
            val currentAppVersion = try {
                val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    appContext.packageManager.getPackageInfo(
                        appContext.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            } catch (_: Exception) {
                0L
            }

            if (currentAppVersion > lastCopiedVersion) {
                // 删除旧的静态数据库文件，强制 Room 重新从 assets 复制
                appContext.deleteDatabase(DATABASE_NAME)
                prefs.edit().putLong("last_asset_version", currentAppVersion).apply()
            }

            return Room.databaseBuilder(
                appContext,
                RailDatabase::class.java,
                DATABASE_NAME
            )
                .createFromAsset(ASSET_PATH)
                .build()
        }
    }
}
