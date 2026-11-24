package com.example.newsapp

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.newsapp.data.LocalAuth
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.local.DownloadedNews
import com.example.newsapp.local.FavoritoEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAccountsTest {
    @Test
    fun switchingAccountsKeepsPersonalDataSeparate() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "local-accounts-test-${UUID.randomUUID()}"
        val prefsNames = mutableSetOf<String>()
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val testName = "$prefix-$name"
                prefsNames.add(testName)
                return target.getSharedPreferences(testName, mode)
            }
        }
        val prefsName = "session"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.localAccountDao()
            val auth = LocalAuth(dao, prefs)
            assertTrue(auth.register("Ana", "ana@example.com", "Lectura123", 2))
            assertTrue(auth.register("Luis", "luis@example.com", "Lectura456", 2))
            assertFalse(auth.register("Otra Ana", "ANA@example.com", "Lectura789", 2))
            assertFalse(auth.login("ana@example.com", "Incorrecta"))
            assertNull(auth.current())

            assertTrue(auth.login("ANA@example.com", "Lectura123"))
            val ana = requireNotNull(LocalAuth(dao, prefs).current())
            val url = "https://example.com/noticia"
            val favorites = database.favoritoDao()
            val downloads = database.downloadedNewsDao()
            val favorite = FavoritoEntity(ana.userId, url, "Titular", null, null, null, null)
            val download = DownloadedNews(ana.userId, url, "Titular", null, null, "Texto", 1)
            favorites.alternarFavorito(favorite)
            downloads.insert(download)
            PreferencesManager(context, ana.userId).saveCategory("science")

            auth.logout()
            assertNull(LocalAuth(dao, prefs).current())
            assertTrue(auth.login("luis@example.com", "Lectura456"))
            val luis = requireNotNull(auth.current())
            assertNotEquals(ana.userId, luis.userId)
            assertTrue(favorites.obtenerFavoritos(luis.userId).isEmpty())
            assertNull(downloads.find(luis.userId, url))
            assertEquals("", PreferencesManager(context, luis.userId).getCategory())

            auth.logout()
            assertTrue(auth.login("ana@example.com", "Lectura123"))
            assertEquals(ana, auth.current())
            assertEquals(listOf(favorite), favorites.obtenerFavoritos(ana.userId))
            assertEquals(download, downloads.find(ana.userId, url))
            assertEquals("science", PreferencesManager(context, ana.userId).getCategory())
        } finally {
            database.close()
            prefsNames.forEach(target::deleteSharedPreferences)
        }
        Unit
    }
}
