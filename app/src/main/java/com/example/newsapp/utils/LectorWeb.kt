package com.example.newsapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

object LectorWeb {

    fun abrirNoticia(context: Context, url: String?) {

        if (url.isNullOrEmpty()) {
            Toast.makeText(context, "URL de noticia no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(url)

            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(context, com.google.android.material.R.color.design_default_color_primary))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .setStartAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .build()

            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(fallbackIntent)
        }
    }
}