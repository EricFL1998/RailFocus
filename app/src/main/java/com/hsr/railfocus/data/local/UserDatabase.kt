package com.hsr.railfocus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.data.local.entity.FocusTypeEntity
import com.hsr.railfocus.data.local.entity.JourneyRecordEntity
import com.hsr.railfocus.data.local.entity.VisitedStationRecordEntity

/**
 * 用户数据数据库
 * 存储用户生成的历史记录、专注设置等需要长期保留的数据。
 *
 * 注意：不要在这里使用 fallbackToDestructiveMigration()。
 * 升级 [version] 时必须提供正式的 Migration（见 androidx.room.migration.Migration），
 * 否则 Room 会在打开数据库时直接抛出异常，而不是静默清空用户数据。
 */
@Database(
    entities = [
        JourneyRecordEntity::class,
        VisitedStationRecordEntity::class,
        FocusTypeEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun journeyDataAccess(): JourneyDataAccess
    abstract fun visitedStationDataAccess(): VisitedStationDataAccess
    abstract fun focusTypeDataAccess(): FocusTypeDataAccess

    companion object {
        private const val DATABASE_NAME = "user_data.db"

        /**
         * 1 -> 2：旅程表新增 remainingSec 列（进行中旅程的检查点剩余秒数）。
         * 历史记录无该值，保持 NULL 即可。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journey_records ADD COLUMN remainingSec INTEGER DEFAULT NULL")
            }
        }

        /**
         * 2 -> 3：旅程表新增 delayMinutes 列（列车晚点时间，分钟）。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journey_records ADD COLUMN delayMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 3 -> 4：旅程表新增 earnedTier 列（完成该次旅程时用户所处的常客等级）。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journey_records ADD COLUMN earnedTier TEXT DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): UserDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UserDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        }
    }
}
