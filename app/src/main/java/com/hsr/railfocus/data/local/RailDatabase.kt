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
 * 更新策略：每次启动对 assets 中的 rail_focus.db 计算 SHA-256，
 * 与上次复制时保存的哈希比较；不一致说明数据文件有更新，
 * 删除本地副本让 Room 重新从 assets 复制，无需手动提升 versionCode。
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
        private const val PREFS_NAME = "rail_db_prefs"
        private const val KEY_ASSET_HASH = "last_asset_hash"

        @Volatile
        private var INSTANCE: RailDatabase? = null

        fun getInstance(context: Context): RailDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): RailDatabase {
            val appContext = context.applicationContext

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val assetHash = computeAssetHash(appContext)
            val assetChanged = assetHash != null &&
                assetHash != prefs.getString(KEY_ASSET_HASH, null)

            if (assetChanged) {
                // 数据文件有更新：删除旧的本地副本，强制 Room 重新从 assets 复制
                appContext.deleteDatabase(DATABASE_NAME)
            }

            val database = Room.databaseBuilder(
                appContext,
                RailDatabase::class.java,
                DATABASE_NAME
            )
                .createFromAsset(ASSET_PATH)
                .build()

            // 复制成功后再记录哈希；若中途崩溃，下次启动会因哈希不一致再次重拷
            if (assetChanged && assetHash != null) {
                prefs.edit().putString(KEY_ASSET_HASH, assetHash).apply()
            }

            return database
        }

        /** 计算 assets 中预置数据库文件的 SHA-256；读取失败返回 null（不动现有数据库）。 */
        private fun computeAssetHash(context: Context): String? {
            return try {
                context.assets.open(ASSET_PATH).use { input ->
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        md.update(buffer, 0, read)
                    }
                    md.digest().joinToString("") { "%02x".format(it) }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
