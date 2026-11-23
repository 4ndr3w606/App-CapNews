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

@Database(entities = [EntidadNoticia::class, FavoritoEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleDao(): NoticiaDao
    abstract fun favoritoDao(): FavoritoDao

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
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
