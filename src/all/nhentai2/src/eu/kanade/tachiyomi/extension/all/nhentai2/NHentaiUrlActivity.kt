package eu.kanade.tachiyomi.extension.all.nhentai2

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Handles nhentai.net/g/{id}/ deep links.
 *
 * NOTE: Do NOT use Kotlin Intrinsics (==, let, also, etc.) in Activity classes.
 * Use Java equivalents such as String.equals(), explicit null checks, etc.
 */
class NHentaiUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData = intent?.data?.toString()
        if (intentData != null) {
            // Extract the gallery ID from the URL path: /g/{id}/
            val path = intent.data?.pathSegments
            val galleryId = if (path != null && path.size >= 2 && path[0].equals("g")) {
                path[1]
            } else {
                null
            }

            if (galleryId != null) {
                val mainIntent = Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", "${NHentai.PREFIX_ID_SEARCH}$galleryId")
                    putExtra("filter", packageName)
                }
                try {
                    startActivity(mainIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("NHentaiUrlActivity", e.toString())
                }
            } else {
                Log.e("NHentaiUrlActivity", "Could not extract gallery id from: $intentData")
            }
        } else {
            Log.e("NHentaiUrlActivity", "Could not parse uri from intent $intent")
        }

        finish()
    }
}
