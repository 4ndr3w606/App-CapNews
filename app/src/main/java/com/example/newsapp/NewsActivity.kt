package com.example.newsapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newsapp.data.api.InstanciaRetrofit
import com.example.newsapp.data.toDomain
import com.example.newsapp.data.toEntity
import com.example.newsapp.databinding.ActivityNewsBinding
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.local.NoticiaDao
import kotlinx.coroutines.launch
class NewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsBinding
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val categoria = intent.getStringExtra("EXTRA_SELECTED_CATEGORY") ?: "general"
        binding.tvHeaderCategory.text = "Noticias de ${categoria.uppercase()}"

        supportActionBar?.title = "Noticias: $categoria"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        newsAdapter = NewsAdapter(emptyList()) { article ->
            Toast.makeText(this, "Seleccionado: ${article.title}", Toast.LENGTH_SHORT).show()
        }

        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(this@NewsActivity)
            adapter = newsAdapter
        }

        //Instancia del DAO de Room
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

                    val validArticles =  articles.filter{!it.url.isNullOrEmpty() && !it.title.isNullOrEmpty()}

                    val entities = validArticles.map{it.toEntity(categoria)}
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

    private suspend fun loadFromDatabase(
        noticiaDao: NoticiaDao,
        category: String
    ){
        val cachedEntities = noticiaDao.getArticlesByCategory(category)
        if(cachedEntities.isNotEmpty()){
            val cachedArticles = cachedEntities.map{it.toDomain()}
            newsAdapter.actualizarNews(cachedArticles)
        }else{
            Toast.makeText(this,"No hay noticias guardadas para esta categoría offline.", Toast.LENGTH_SHORT).show()

        }
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
