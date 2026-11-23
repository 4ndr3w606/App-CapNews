package com.example.newsapp.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Los favoritos anteriores se conservan en su tabla, pues no tienen cuenta asociada.
        db.execSQL(
            """
            CREATE TABLE favoritos_por_cuenta (
                cuentaId INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT,
                description TEXT,
                urlToImage TEXT,
                publishedAt TEXT,
                sourceName TEXT,
                PRIMARY KEY (cuentaId, url)
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE local_news (scopeKey TEXT NOT NULL PRIMARY KEY, articlesJson TEXT NOT NULL)"
        )
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE downloaded_news (
                accountId INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                sourceName TEXT,
                publishedAt TEXT,
                body TEXT NOT NULL,
                downloadedAt INTEGER NOT NULL,
                PRIMARY KEY (accountId, url)
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloaded_news ADD COLUMN coverImage BLOB")
    }
}

@Database(
    entities = [EntidadNoticia::class, FavoritoEntity::class, LocalNewsEntity::class, DownloadedNews::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleDao(): NoticiaDao
    abstract fun favoritoDao(): FavoritoDao
    abstract fun localNewsDao(): LocalNewsDao
    abstract fun downloadedNewsDao(): DownloadedNewsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "news_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
