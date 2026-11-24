package com.example.newsapp.utils

import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.newsapp.R
import com.google.android.material.snackbar.Snackbar

internal fun View.mostrarAviso(@StringRes message: Int, error: Boolean = false, long: Boolean = false) {
    val duration = if (error || long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
    val background = if (error) R.color.news_notice_error_surface else R.color.news_section_toolbar
    val foreground = if (error) R.color.news_notice_error_text else R.color.news_control_text
    Snackbar.make(this, message, duration)
        .setBackgroundTint(ContextCompat.getColor(context, background))
        .setTextColor(ContextCompat.getColor(context, foreground))
        .show()
}
