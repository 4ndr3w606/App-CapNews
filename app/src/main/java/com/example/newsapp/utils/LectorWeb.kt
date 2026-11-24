package com.example.newsapp.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.example.newsapp.R

object LectorWeb {

    fun abrirNoticia(context: Activity, url: String?) {

        if (url.isNullOrEmpty()) {
            context.findViewById<View>(android.R.id.content).mostrarAviso(R.string.news_link_unavailable, error = true)
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
