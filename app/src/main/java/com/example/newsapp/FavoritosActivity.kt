package com.example.newsapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.data.toDomain
import com.example.newsapp.data.toFavoritoEntity
import com.example.newsapp.databinding.ActivityFavoritosBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.utils.LectorWeb
import kotlinx.coroutines.launch

class FavoritosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Noticias Favoritas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val db = AppDatabase.getDatabase(this)
        val favoritoDao = db.favoritoDao()

        newsAdapter = NewsAdapter(
            articles = emptyList(),
            onItemClick = { article ->
                LectorWeb.abrirNoticia(this, article.url)
            },
            onFavoritoClick = { article ->
                lifecycleScope.launch {
                    favoritoDao.eliminarFavorito(article.toFavoritoEntity())
                    Toast.makeText(this@FavoritosActivity, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                    cargarFavoritos()
                }
            }
        )

        binding.rvFavoritos.apply {
            layoutManager = LinearLayoutManager(this@FavoritosActivity)
            adapter = newsAdapter
        }

        cargarFavoritos()
    }

    private fun cargarFavoritos() {
        val db = AppDatabase.getDatabase(this)
        val favoritoDao = db.favoritoDao()

        lifecycleScope.launch {
            val listaEntities = favoritoDao.obtenerTodosLosFavoritos()
            val listaArticulos = listaEntities.map { it.toDomain() }

            if (listaArticulos.isEmpty()) {
                Toast.makeText(this@FavoritosActivity, "No tienes favoritos guardados", Toast.LENGTH_SHORT).show()
            }

            newsAdapter.actualizarNews(listaArticulos)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
