package io.github.shiaho777.logsleuth.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        FilterEntity::class,
        CrashEventEntity::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun filterDao(): FilterDao
    abstract fun crashEventDao(): CrashEventDao
    abstract fun bookmarkDao(): BookmarkDao
}
