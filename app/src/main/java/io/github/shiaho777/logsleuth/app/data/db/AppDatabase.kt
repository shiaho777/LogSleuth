package io.github.shiaho777.logsleuth.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        FilterEntity::class,
        CrashEventEntity::class,
        BookmarkEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun filterDao(): FilterDao
    abstract fun crashEventDao(): CrashEventDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE filters ADD COLUMN enabled INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE filters ADD COLUMN including INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL("ALTER TABLE filters ADD COLUMN pid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE filters ADD COLUMN tid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE filters ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
