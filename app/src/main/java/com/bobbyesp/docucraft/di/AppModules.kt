package com.bobbyesp.docucraft.di

import android.app.Application
import android.content.Context
import com.google.android.datatransport.runtime.dagger.Module
import com.google.android.datatransport.runtime.dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModules {
    @Provides
    fun provideContext(application: Application): Context {
        return application.applicationContext
    }
}