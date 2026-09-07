package io.github.shiaho777.logsleuth.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.shiaho777.logsleuth.app.core.logcat.AccessChecker
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.data.db.AppDatabase
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.NotificationHelper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "logsleuth.db").build()

    @Provides fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()
    @Provides fun provideFilterDao(db: AppDatabase): FilterDao = db.filterDao()
    @Provides fun provideCrashEventDao(db: AppDatabase): CrashEventDao = db.crashEventDao()
    @Provides fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideShizukuManager(@ApplicationContext context: Context): ShizukuManager =
        ShizukuManager(context)

    @Provides
    @Singleton
    fun provideAccessChecker(
        @ApplicationContext context: Context,
        shizukuManager: ShizukuManager,
    ): AccessChecker = AccessChecker(context, shizukuManager)

    @Provides
    @Singleton
    fun provideNotificationHelper(@ApplicationContext context: Context): NotificationHelper =
        NotificationHelper(context).also { it.createChannels() }
}
