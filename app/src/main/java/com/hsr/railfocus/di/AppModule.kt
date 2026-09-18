package com.hsr.railfocus.di

import android.content.Context
import com.hsr.railfocus.data.local.RailDatabase
import com.hsr.railfocus.data.local.UserDatabase
import com.hsr.railfocus.data.local.dataaccess.EdgeDataAccess
import com.hsr.railfocus.data.local.dataaccess.FocusTypeDataAccess
import com.hsr.railfocus.data.local.dataaccess.JourneyDataAccess
import com.hsr.railfocus.data.local.dataaccess.StationDataAccess
import com.hsr.railfocus.data.local.dataaccess.VisitedStationDataAccess
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRailDatabase(
        @ApplicationContext context: Context,
    ): RailDatabase {
        // 静态资源数据库（车站、线路）
        return RailDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideUserDatabase(
        @ApplicationContext context: Context,
    ): UserDatabase {
        // 用户数据数据库（历史记录、设置）
        return UserDatabase.getInstance(context)
    }

    @Provides
    fun provideStationDataAccess(database: RailDatabase): StationDataAccess {
        return database.stationDataAccess()
    }

    @Provides
    fun provideEdgeDataAccess(database: RailDatabase): EdgeDataAccess {
        return database.edgeDataAccess()
    }

    @Provides
    fun provideJourneyDataAccess(database: UserDatabase): JourneyDataAccess {
        return database.journeyDataAccess()
    }

    @Provides
    fun provideVisitedStationDataAccess(database: UserDatabase): VisitedStationDataAccess {
        return database.visitedStationDataAccess()
    }

    @Provides
    fun provideFocusTypeDataAccess(database: UserDatabase): FocusTypeDataAccess {
        return database.focusTypeDataAccess()
    }
}
