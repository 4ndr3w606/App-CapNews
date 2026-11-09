package com.example.newsapp.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoritoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarFavorito(favorito: FavoritoEntity)

    @Delete
    suspend fun eliminarFavorito(favorito: FavoritoEntity)

    @Query("SELECT * FROM favoritos")
    suspend fun obtenerTodosLosFavoritos(): List<FavoritoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE url = :url LIMIT 1)")
    suspend fun esFavorito(url: String): Boolean
}