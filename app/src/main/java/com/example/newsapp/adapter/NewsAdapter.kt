package com.example.newsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.newsapp.R
import com.example.newsapp.data.api.Article
import com.example.newsapp.databinding.ItemNewsBinding

class NewsAdapter(
    private var articles: List<Article>,
    private val onItemClick: (Article) -> Unit,
    private val onFavoritoClick: (Article) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {
    private var favoriteUrls = emptySet<String>()

    inner class NewsViewHolder(private val binding: ItemNewsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            binding.tvTitle.text = article.title ?: "Sin título"
            binding.tvDescription.text = article.description ?: "Sin descripción disponible."
            binding.tvSource.text = article.source?.name ?: "Fuente desconocida"
            binding.tvPublishedAt.text = article.publishedAt?.take(10) ?: ""

            binding.ivNewsImage.isVisible = !article.urlToImage.isNullOrBlank()
            Glide.with(binding.root.context)
                .load(article.urlToImage)
                .centerCrop()
                .into(binding.ivNewsImage)

            binding.root.setOnClickListener {
                onItemClick(article)
            }


            bindFavorite(article.url in favoriteUrls)
            binding.btnFavorito.setOnClickListener {
                onFavoritoClick(article)
            }
        }

        fun bindFavorite(saved: Boolean) {
            val button = binding.btnFavorito
            button.isActivated = saved
            button.contentDescription = button.context.getString(
                if (saved) R.string.news_remove_favorite else R.string.news_add_favorite
            )
            ViewCompat.setStateDescription(
                button,
                button.context.getString(if (saved) R.string.news_favorite_saved else R.string.news_favorite_unsaved)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int, payloads: MutableList<Any>) {
        if (FavoriteChanged in payloads) {
            holder.bindFavorite(articles[position].url in favoriteUrls)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun actualizarNews(newArticles: List<Article>) {
        if (articles == newArticles) return
        articles = newArticles
        notifyDataSetChanged()
    }

    fun actualizarFavoritos(urls: Set<String>) {
        if (favoriteUrls == urls) return
        val previous = favoriteUrls
        favoriteUrls = urls
        articles.forEachIndexed { index, article ->
            if ((article.url in previous) != (article.url in urls)) {
                notifyItemChanged(index, FavoriteChanged)
            }
        }
    }

    private object FavoriteChanged
}
