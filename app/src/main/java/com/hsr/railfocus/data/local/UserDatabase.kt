package com.hsr.railfocus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.JournalDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import com.hsr.railfocus.data.local.dataaccess.WeatherDataAccess
import com.hsr.railfocus.data.local.entity.JourneyJournalEntity
import com.hsr.railfocus.data.local.entity.FocusTypeEntity
import com.hsr.railfocus.data.local.entity.JourneyRecordEntity
import com.hsr.railfocus.data.local.entity.VisitedStationRecordEntity
import com.hsr.railfocus.data.local.entity.WeatherCacheEntity

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
        JourneyJournalEntity::class,
        WeatherCacheEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun journeyDataAccess(): JourneyDataAccess
    abstract fun visitedStationDataAccess(): VisitedStationDataAccess
    abstract fun focusTypeDataAccess(): FocusTypeDataAccess
    abstract fun journalDataAccess(): JournalDataAccess
    abstract fun weatherDataAccess(): WeatherDataAccess

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

        /**
         * 4 -> 5：新增 journey_journals 手账表
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `journey_journals` (
                        `id` TEXT NOT NULL,
                        `journeyId` TEXT NOT NULL,
                        `stationId` TEXT NOT NULL,
                        `stationName` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `imagePathsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`journeyId`) REFERENCES `journey_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_journey_journals_journeyId` ON `journey_journals` (`journeyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journey_journals_stationId` ON `journey_journals` (`stationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journey_journals_createdAt` ON `journey_journals` (`createdAt`)")
            }
        }

        /**
         * 5 -> 6：手账表新增 audioPath 与 audioDurationSec 列（语音录音支持）
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journey_journals ADD COLUMN audioPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE journey_journals ADD COLUMN audioDurationSec INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 6 -> 7：新增 weather_cache 天气与归属地缓存表（按地理网格存储）
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `weather_cache` (
                        `gridKey` TEXT NOT NULL,
                        `locationKey` TEXT NOT NULL,
                        `areaName` TEXT NOT NULL,
                        `cityName` TEXT NOT NULL,
                        `provinceName` TEXT NOT NULL,
                        `weatherCode` INTEGER NOT NULL,
                        `temperatureC` REAL NOT NULL,
                        `geoFetchedAt` INTEGER NOT NULL DEFAULT 0,
                        `weatherFetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`gridKey`)
                    )
                """.trimIndent())
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
        }
    }
}
