package dev.xiaoxin.testgrounds.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.xiaoxin.testgrounds.ml.YoloDetector
import dev.xiaoxin.testgrounds.repository.MediaRepository
import dev.xiaoxin.testgrounds.settings.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideYoloDetector(@ApplicationContext context: Context): YoloDetector {
        return YoloDetector(context)
    }

    @Provides
    @Singleton
    fun provideMediaRepository(@ApplicationContext context: Context): MediaRepository {
        return MediaRepository(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository = SettingsRepository(context)
}