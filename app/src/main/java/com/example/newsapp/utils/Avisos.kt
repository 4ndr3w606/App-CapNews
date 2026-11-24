package com.example.newsapp.utils

import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

internal fun View.mostrarAviso(@StringRes message: Int, error: Boolean = false, long: Boolean = false) {
    val duration = if (error || long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
    val background = if (error) MaterialR.attr.colorErrorContainer else MaterialR.attr.colorSurfaceInverse
    val foreground = if (error) MaterialR.attr.colorOnErrorContainer else MaterialR.attr.colorOnSurfaceInverse
    Snackbar.make(this, message, duration)
        .setBackgroundTint(MaterialColors.getColor(this, background))
        .setTextColor(MaterialColors.getColor(this, foreground))
        .show()
}
