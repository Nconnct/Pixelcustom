package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.model.DefaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class OnlineAppEnrichmentResult(
    val packageName: String,
    val categoryId: String,
    val secondaryCategoryId: String?,
    val confidence: Float,
    val storeGenre: String?,
    val storeSummary: String?,
    val source: String
)

/**
 * Hybrid Online Store Metadata + AI Semantic App Intelligence Service.
 *
 * When internet is available:
 * 1. Queries public Google Play Store metadata (`https://play.google.com/store/apps/details?id=<pkg>&hl=en`)
 *    to extract official `applicationCategory` / `genre`, title, and description snippets.
 * 2. Combines store metadata with our on-device ML & semantic token reasoner to accurately classify
 *    apps into Productivity, Social, Banking, Media, Utilities, Shopping, Games, etc.
 * 3. Falls back gracefully to 100% offline TFLite + heuristic classification when offline.
 */
class OnlineAppIntelligenceService(
    private val context: Context,
    private val offlineClassifier: OfflineAiClassifier
) {

    fun isInternetAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enriches a batch of installed apps using online Play Store metadata + AI semantic reasoning
     * when internet is available.
     */
    suspend fun enrichAppsWhenOnline(
        appsToEnrich: List<Triple<String, String, Boolean>> // (packageName, appName, isSystemApp)
    ): List<OnlineAppEnrichmentResult> = withContext(Dispatchers.IO) {
        if (!isInternetAvailable() || appsToEnrich.isEmpty()) {
            return@withContext emptyList()
        }

        val results = mutableListOf<OnlineAppEnrichmentResult>()
        // Limit per-pass network lookups to keep startup fast and battery-friendly
        for ((pkg, appName, isSystemApp) in appsToEnrich.take(18)) {
            val enriched = enrichSingleAppOnline(pkg, appName, isSystemApp)
            if (enriched != null) {
                results.add(enriched)
            }
        }
        results
    }

    suspend fun enrichSingleAppOnline(
        packageName: String,
        appName: String,
        isSystemApp: Boolean
    ): OnlineAppEnrichmentResult? = withContext(Dispatchers.IO) {
        if (!isInternetAvailable()) return@withContext null

        val storeMeta = fetchPlayStoreMetadata(packageName)
        val combinedText = buildString {
            append(appName).append(" ")
            append(packageName.replace('.', ' ')).append(" ")
            if (storeMeta != null) {
                append(storeMeta.genre.orEmpty()).append(" ")
                append(storeMeta.title.orEmpty()).append(" ")
                append(storeMeta.descriptionSnippet.orEmpty())
            }
        }

        // Map official store genre + semantic AI keywords to our DefaultCategory taxonomy
        val genreCategory = storeMeta?.genre?.let { mapStoreGenreToCategory(it, combinedText) }
        val semanticCategory = inferSemanticCategoryFromRichText(combinedText, isSystemApp)

        val offlinePred = offlineClassifier.classifyApp(
            packageName = packageName,
            appName = "$appName ${storeMeta?.genre.orEmpty()} ${storeMeta?.descriptionSnippet.orEmpty()}",
            isSystemApp = isSystemApp
        )

        val chosenCategory = when {
            genreCategory != null && genreCategory != DefaultCategory.UNCATEGORIZED.id -> genreCategory
            semanticCategory != null && semanticCategory != DefaultCategory.UNCATEGORIZED.id -> semanticCategory
            else -> offlinePred.categoryId
        }

        val secondaryCat = when {
            semanticCategory != null && semanticCategory != chosenCategory -> semanticCategory
            offlinePred.categoryId != chosenCategory &&
                offlinePred.categoryId != DefaultCategory.UNCATEGORIZED.id -> offlinePred.categoryId
            else -> offlinePred.suggestedCategoryId
        }

        val finalConfidence = when {
            genreCategory != null -> 0.96f
            semanticCategory != null -> maxOf(0.91f, offlinePred.confidence)
            else -> offlinePred.confidence
        }

        OnlineAppEnrichmentResult(
            packageName = packageName,
            categoryId = chosenCategory,
            secondaryCategoryId = secondaryCat,
            confidence = finalConfidence,
            storeGenre = storeMeta?.genre,
            storeSummary = storeMeta?.descriptionSnippet?.take(120),
            source = if (storeMeta != null) "ONLINE_STORE_AI_ML" else "HYBRID_SEMANTIC_ML"
        )
    }

    private data class StorePageMetadata(
        val title: String?,
        val genre: String?,
        val descriptionSnippet: String?
    )

    private fun fetchPlayStoreMetadata(packageName: String): StorePageMetadata? {
        // Skip internal Android system packages that aren't on the public Play Store
        if (packageName.startsWith("com.android.internal") ||
            packageName.startsWith("android.") ||
            packageName == "com.rajendra.pixelcategorylauncher"
        ) {
            return null
        }

        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://play.google.com/store/apps/details?id=$packageName&hl=en&gl=US")
            conn = (url.openConnection() as? HttpURLConnection)?.apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            } ?: return null

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val htmlBuilder = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                var line: String?
                var charsRead = 0
                while (reader.readLine().also { line = it } != null && charsRead < 65_000) {
                    htmlBuilder.append(line).append('\n')
                    charsRead += (line?.length ?: 0)
                }
            }
            val html = htmlBuilder.toString()

            val genreRegex = Regex(
                """itemprop="applicationCategory"\s+content="([^"]+)"|"genre"\s*:\s*"([^"]+)"""",
                RegexOption.IGNORE_CASE
            )
            val descRegex = Regex(
                """name="description"\s+content="([^"]+)"|property="og:description"\s+content="([^"]+)"""",
                RegexOption.IGNORE_CASE
            )
            val titleRegex = Regex(
                """property="og:title"\s+content="([^"]+)"""",
                RegexOption.IGNORE_CASE
            )

            val genreMatch = genreRegex.find(html)
            val genre = genreMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }

            val descMatch = descRegex.find(html)
            val desc = descMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }

            val titleMatch = titleRegex.find(html)
            val title = titleMatch?.groupValues?.getOrNull(1)

            if (genre != null || desc != null || title != null) {
                StorePageMetadata(
                    title = title,
                    genre = genre,
                    descriptionSnippet = desc
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun mapStoreGenreToCategory(rawGenre: String, fullContext: String): String? {
        val g = rawGenre.uppercase(Locale.US)
        val ctx = fullContext.lowercase(Locale.US)
        return when {
            g.contains("GAME") -> DefaultCategory.GAMES.id
            g.contains("FINANCE") -> {
                if (ctx.contains("upi") || ctx.contains("wallet") || ctx.contains("pay")) {
                    DefaultCategory.UPI_PAYMENTS.id
                } else {
                    DefaultCategory.BANKING.id
                }
            }
            g.contains("SOCIAL") -> DefaultCategory.SOCIAL.id
            g.contains("COMMUNICATION") -> {
                if (ctx.contains("browser") || ctx.contains("web")) {
                    DefaultCategory.BROWSERS.id
                } else {
                    DefaultCategory.MESSAGING.id
                }
            }
            g.contains("PRODUCTIVITY") || g.contains("BUSINESS") -> DefaultCategory.PRODUCTIVITY.id
            g.contains("SHOPPING") || g.contains("FOOD") -> DefaultCategory.SHOPPING.id
            g.contains("VIDEO") || g.contains("ENTERTAINMENT") -> DefaultCategory.VIDEO_STREAMING.id
            g.contains("MUSIC") || g.contains("AUDIO") -> DefaultCategory.MUSIC_AUDIO.id
            g.contains("PHOTOGRAPHY") -> DefaultCategory.PHOTOGRAPHY.id
            g.contains("EDUCATION") || g.contains("BOOKS") -> DefaultCategory.EDUCATION.id
            g.contains("TOOLS") || g.contains("PERSONALIZATION") -> DefaultCategory.UTILITIES.id
            else -> null
        }
    }

    private fun inferSemanticCategoryFromRichText(text: String, isSystemApp: Boolean): String? {
        val lower = text.lowercase(Locale.US)
        return when {
            containsAny(lower, listOf("bank", "banking", "netbanking", "credit card", "loan", "mutual fund", "stock", "trading", "hdfc", "icici", "sbi", "axis", "chase", "wells fargo", "revolut", "paypal")) ->
                DefaultCategory.BANKING.id
            containsAny(lower, listOf("upi", "gpay", "phonepe", "paytm", "bhim", "razorpay", "cashapp", "venmo", "send money", "qr pay")) ->
                DefaultCategory.UPI_PAYMENTS.id
            containsAny(lower, listOf("instagram", "facebook", "twitter", "threads", "reddit", "linkedin", "snapchat", "tiktok", "pinterest", "social network", "mastodon", "bluesky")) ->
                DefaultCategory.SOCIAL.id
            containsAny(lower, listOf("whatsapp", "telegram", "signal", "messenger", "messages", "sms", "chat", "discord", "slack", "dialer", "phone call", "contacts")) ->
                DefaultCategory.MESSAGING.id
            containsAny(lower, listOf("docs", "sheets", "slides", "notion", "evernote", "todo", "task", "calendar", "gmail", "outlook", "email", "office", "pdf", "word", "excel", "notes", "keep", "workspace", "productivity")) ->
                DefaultCategory.PRODUCTIVITY.id
            containsAny(lower, listOf("chrome", "firefox", "brave", "opera", "edge", "vivaldi", "duckduckgo", "web browser", "internet browser")) ->
                DefaultCategory.BROWSERS.id
            containsAny(lower, listOf("youtube", "netflix", "prime video", "hotstar", "disney", "twitch", "vlc", "mx player", "plex", "streaming", "movies", "tv shows")) ->
                DefaultCategory.VIDEO_STREAMING.id
            containsAny(lower, listOf("spotify", "music", "podcast", "audible", "wynk", "saavn", "gaana", "apple music", "soundcloud", "equalizer", "radio")) ->
                DefaultCategory.MUSIC_AUDIO.id
            containsAny(lower, listOf("camera", "photos", "gallery", "lightroom", "snapseed", "vsco", "photo editor", "canva", "lens")) ->
                DefaultCategory.PHOTOGRAPHY.id
            containsAny(lower, listOf("amazon", "flipkart", "myntra", "meesho", "ebay", "walmart", "swiggy", "zomato", "uber", "ola", "shopping", "delivery", "store")) ->
                DefaultCategory.SHOPPING.id
            containsAny(lower, listOf("calculator", "clock", "alarm", "files", "file manager", "weather", "flashlight", "scanner", "vpn", "authenticator", "cleaner", "compass", "recorder")) ->
                DefaultCategory.UTILITIES.id
            isSystemApp && containsAny(lower, listOf("settings", "system", "setup", "package installer", "bluetooth", "sim")) ->
                DefaultCategory.SYSTEM_APPS.id
            else -> null
        }
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean {
        for (needle in needles) {
            if (haystack.contains(needle)) return true
        }
        return false
    }
}
