package com.example.newsapp

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.local.FavoritoEntity
import com.example.newsapp.local.MIGRATION_2_3
import com.example.newsapp.local.MIGRATION_3_4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritosPorCuentaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migracionConservaDatosYSeparaFavoritosPorCuenta() = runBlocking {
        val databaseName = "favoritos-test-${UUID.randomUUID()}"
        val url = "https://example.com/noticia"
        try {
            crearBaseVersion2(databaseName, url)
            val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
            try {
                val dao = database.favoritoDao()
                val cuentaA = FavoritoEntity(1, url, "Titular", null, null, null, null)
                val cuentaB = cuentaA.copy(cuentaId = 2)

                assertTrue(dao.obtenerFavoritos(1).isEmpty())
                assertTrue(dao.obtenerFavoritos(2).isEmpty())
                assertEquals(url, database.articleDao().getArticlesByCategory("world").single().url)
                assertTrue(dao.alternarFavorito(cuentaA))
                assertTrue(dao.obtenerFavoritos(2).isEmpty())
                assertTrue(dao.alternarFavorito(cuentaB))
                assertEquals(listOf(cuentaA), dao.obtenerFavoritos(1))
                assertEquals(listOf(cuentaB), dao.obtenerFavoritos(2))
                assertFalse(dao.alternarFavorito(cuentaA))
                assertTrue(dao.obtenerFavoritos(1).isEmpty())
                assertEquals(listOf(cuentaB), dao.obtenerFavoritos(2))
            } finally {
                database.close()
            }

            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
                db.rawQuery("SELECT url FROM favoritos", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(url, cursor.getString(0))
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
        Unit
    }

    private fun crearBaseVersion2(databaseName: String, url: String) {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE articles (
                    url TEXT NOT NULL PRIMARY KEY, title TEXT, description TEXT,
                    urlToImage TEXT, publishedAt TEXT, sourceName TEXT, category TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE favoritos (
                    url TEXT NOT NULL PRIMARY KEY, title TEXT, description TEXT,
                    urlToImage TEXT, publishedAt TEXT, sourceName TEXT
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO articles (url, category) VALUES (?, 'world')", arrayOf(url))
            db.execSQL("INSERT INTO favoritos (url) VALUES (?)", arrayOf(url))
            db.version = 2
        }
    }
}
