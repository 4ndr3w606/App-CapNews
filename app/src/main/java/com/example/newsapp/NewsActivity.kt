package com.example.newsapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.data.api.InstanciaRetrofit
import com.example.newsapp.data.toDomain
import com.example.newsapp.data.toEntity
import com.example.newsapp.databinding.ActivityNewsBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.local.NoticiaDao
import com.example.newsapp.utils.LectorWeb
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsBinding
    private lateinit var newsAdapter: NewsAdapter


    private var trabajoBusqueda: Job? = null
    private var categoriaActual: String = "world"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apiCategory = intent.getStringExtra("EXTRA_SELECTED_CATEGORY") ?: "world"
        val displayCategory = intent.getStringExtra("EXTRA_DISPLAY_CATEGORY") ?: "Mundo"
        categoriaActual = apiCategory

        binding.tvHeaderCategory.text = "Noticias de $displayCategory"
        supportActionBar?.title = "Noticias: $displayCategory"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        newsAdapter = NewsAdapter(emptyList()) { article ->
            LectorWeb.abrirNoticia(this, article.url)
        }

        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity)
            adapter = newsAdapter
        }

        cargarNoticiasPorCategoria(categoriaActual)

        configurarBuscador()
    }

    private fun configurarBuscador() {
        binding.svBuscador.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    buscarNoticiasEnApi(query.trim())
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                trabajoBusqueda?.cancel()

                trabajoBusqueda = lifecycleScope.launch {
                    delay(500)
                    val texto = newText?.trim() ?: ""

                    if (texto.isEmpty()) {

                        cargarNoticiasPorCategoria(categoriaActual)
                    } else if (texto.length >= 3) {

                        buscarNoticiasEnApi(texto)
                    }
                }
                return true
            }
        })
    }

    private fun cargarNoticiasPorCategoria(categoria: String) {
        val db = AppDatabase.getDatabase(this)
        val noticiaDao = db.articleDao()

        lifecycleScope.launch {
            try {
                val response = InstanciaRetrofit.api.getTopHeadlines(
                    category = categoria,
                    apiKey = InstanciaRetrofit.API_KEY
                )

                if (response.isSuccessful) {
                    val articles = response.body()?.articles ?: emptyList()
                    val validArticles = articles.filter { !it.url.isNullOrEmpty() && !it.title.isNullOrEmpty() }

                    val entities = validArticles.map { it.toEntity(categoria) }
                    noticiaDao.insertArticles(entities)

                    newsAdapter.actualizarNews(validArticles)
                } else {
                    loadFromDatabase(noticiaDao, categoria)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@NewsActivity,
                    "Modo sin conexión",
                    Toast.LENGTH_SHORT
                ).show()
                loadFromDatabase(noticiaDao, categoria)
            }
        }
    }

    private fun buscarNoticiasEnApi(terminoBusqueda: String) {
        lifecycleScope.launch {
            try {
                val response = InstanciaRetrofit.api.buscarNoticias(
                    consulta = terminoBusqueda,
                    apiKey = InstanciaRetrofit.API_KEY
                )

                if (response.isSuccessful) {
                    val articles = response.body()?.articles ?: emptyList()
                    val validArticles = articles.filter { !it.url.isNullOrEmpty() && !it.title.isNullOrEmpty() }

                    if (validArticles.isEmpty()) {
                        Toast.makeText(
                            this@NewsActivity,
                            "No se encontraron noticias para: $terminoBusqueda",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    newsAdapter.actualizarNews(validArticles)
                } else {
                    Toast.makeText(
                        this@NewsActivity,
                        "Error al buscar: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@NewsActivity,
                    "Error de conexión al buscar",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun loadFromDatabase(
        noticiaDao: NoticiaDao,
        category: String
    ) {
        val cachedEntities = noticiaDao.getArticlesByCategory(category)
        if (cachedEntities.isNotEmpty()) {
            val cachedArticles = cachedEntities.map { it.toDomain() }
            newsAdapter.actualizarNews(cachedArticles)
        } else {
            Toast.makeText(
                this,
                "No hay noticias guardadas para esta categoría offline.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}