package com.example.newsapp.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarFavorito(favorito: FavoritoEntity)

    @Query("DELETE FROM favoritos_por_cuenta WHERE cuentaId = :cuentaId AND url = :url")
    suspend fun eliminarFavorito(cuentaId: Long, url: String): Int

    @Query("SELECT * FROM favoritos_por_cuenta WHERE cuentaId = :cuentaId")
    suspend fun obtenerFavoritos(cuentaId: Long): List<FavoritoEntity>

    @Query("SELECT * FROM favoritos_por_cuenta WHERE cuentaId = :cuentaId")
    fun observarFavoritos(cuentaId: Long): Flow<List<FavoritoEntity>>

    @Query("SELECT url FROM favoritos_por_cuenta WHERE cuentaId = :cuentaId")
    fun observarUrlsFavoritas(cuentaId: Long): Flow<List<String>>

    @Transaction
    suspend fun alternarFavorito(favorito: FavoritoEntity): Boolean {
        val eliminados = eliminarFavorito(favorito.cuentaId, favorito.url)
        if (eliminados > 0) return false

        insertarFavorito(favorito)
        return true
    }
}
