package com.hsr.railfocus.di

import com.hsr.railfocus.data.repository.PermissionRepositoryImpl
import com.hsr.railfocus.domain.repository.PermissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindPermissionRepository(
        impl: PermissionRepositoryImpl,
    ): PermissionRepository
}
