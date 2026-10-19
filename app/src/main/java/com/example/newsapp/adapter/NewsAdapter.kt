package com.example.newsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.newsapp.data.api.Article
import com.example.newsapp.databinding.ItemNewsBinding

class NewsAdapter(
    private var articles: List<Article>,
    private val onItemClick: (Article) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    inner class NewsViewHolder(private val binding: ItemNewsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(article: Article) {
            binding.tvTitle.text = article.title ?: "Sin título"
            binding.tvDescription.text = article.description ?: "Sin descripción disponible."
            binding.tvSource.text = article.source?.name ?: "Fuente desconocida"
            binding.tvPublishedAt.text = article.publishedAt?.take(10) ?: ""

            Glide.with(binding.root.context)
                .load(article.urlToImage)
                .centerCrop()
                .into(binding.ivNewsImage)

            binding.root.setOnClickListener {
                onItemClick(article)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    fun actualizarNews(newArticles: List<Article>) {
        articles = newArticles
        notifyDataSetChanged()
    }
}
