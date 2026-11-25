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
        AutomationRuleEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun filterDao(): FilterDao
    abstract fun crashEventDao(): CrashEventDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun automationRuleDao(): AutomationRuleDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN remoteEndpoint TEXT")
                db.execSQL("ALTER TABLE crash_events ADD COLUMN remoteEndpoint TEXT")
            }
        }

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE crash_events ADD COLUMN appName TEXT")
                db.execSQL("ALTER TABLE crash_events ADD COLUMN versionName TEXT")
                db.execSQL("ALTER TABLE crash_events ADD COLUMN versionCode INTEGER")
                db.execSQL("ALTER TABLE crash_events ADD COLUMN contextPath TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE filters ADD COLUMN allowedLevels TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE filters ADD COLUMN tagExact INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS automation_rules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL, " +
                        "packageName TEXT, " +
                        "query TEXT NOT NULL, " +
                        "useRegex INTEGER NOT NULL, " +
                        "tagQuery TEXT NOT NULL, " +
                        "targetPackage TEXT NOT NULL, " +
                        "action TEXT NOT NULL, " +
                        "cooldownSeconds INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
            }
        }
    }
}
