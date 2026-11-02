package com.example.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.example.data.local.AIUserCorrectionEntity
import com.example.domain.model.DefaultCategory
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class ClassificationPrediction(
    val categoryId: String,
    val categoryName: String,
    val confidence: Float,
    val suggestedCategoryId: String? = null,
    val suggestedCategoryName: String? = null,
    val source: String // USER_OVERRIDE, KNOWN_DB, METADATA_HYBRID, NEURAL_TFLITE, FALLBACK_RULE
)

/**
 * 100% Offline Hybrid AI Categorization Engine.
 *
 * Combines:
 * 1. User Manual Corrections & Learned Token Overrides (Room AIUserCorrectionEntity)
 * 2. Local Known-Package Dictionary (500+ curated package signatures & prefixes)
 * 3. Android ApplicationInfo Metadata & Domain Keyword Heuristics
 * 4. Embedded 2-Layer Neural Softmax + TF-IDF Classifier loaded from
 *    assets/app_category_model.tflite (binary float32 tensor buffer) and
 *    assets/app_category_weights.json (vocabulary & token mappings).
 *
 * Strictly offline: zero network calls, zero API keys, zero cloud dependencies.
 */
class OfflineAiClassifier(private val context: Context? = null) {

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.56f
        const val MODEL_VERSION = "1.0.0-offline-hybrid"

        private val STOP_TOKENS = setOf(
            "com", "org", "net", "app", "apps", "android", "mobile", "client", "in", "io", "co"
        )
    }

    private var isLoaded = false
    private var categories: List<String> = DefaultCategory.entries.map { it.displayName }
    private var vocabIndex: Map<String, Int> = emptyMap()
    private var idf: FloatArray = FloatArray(0)
    private var hiddenDim: Int = 48
    private var w1: Array<FloatArray> = emptyArray()
    private var b1: FloatArray = FloatArray(0)
    private var w2: Array<FloatArray> = emptyArray()
    private var b2: FloatArray = FloatArray(0)
    private val knownPackages = mutableMapOf<String, String>()

    // Dynamic token weights learned from user manual corrections on-device
    private val userTokenBoosts = mutableMapOf<String, MutableMap<String, Float>>()
    private val userPackageOverrides = mutableMapOf<String, String>()

    init {
        loadEmbeddedModel()
    }

    @Synchronized
    fun loadEmbeddedModel() {
        if (isLoaded) return
        try {
            val assets = context?.assets
            if (assets != null) {
                val jsonText = assets.open("app_category_weights.json").bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)

                val catArr = root.getJSONArray("categories")
                categories = List(catArr.length()) { i -> catArr.getString(i) }

                val vocabArr = root.getJSONArray("vocab")
                val vMap = HashMap<String, Int>(vocabArr.length())
                for (i in 0 until vocabArr.length()) {
                    vMap[vocabArr.getString(i)] = i
                }
                vocabIndex = vMap
                hiddenDim = root.optInt("hiddenDim", 48)

                val knownObj = root.getJSONObject("knownPackages")
                val keys = knownObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    knownPackages[k.lowercase()] = knownObj.getString(k)
                }

                // Prefer loading float32 tensors directly from binary .tflite asset
                val loadedBinary = tryLoadBinaryTfliteTensors(
                    assets.open("app_category_model.tflite").readBytes(),
                    vocabArr.length(),
                    hiddenDim,
                    categories.size
                )

                if (!loadedBinary) {
                    val idfArr = root.getJSONArray("idf")
                    idf = FloatArray(idfArr.length()) { i -> idfArr.getDouble(i).toFloat() }

                    val w1Arr = root.getJSONArray("W1")
                    w1 = Array(w1Arr.length()) { r ->
                        val row = w1Arr.getJSONArray(r)
                        FloatArray(row.length()) { c -> row.getDouble(c).toFloat() }
                    }
                    val b1Arr = root.getJSONArray("b1")
                    b1 = FloatArray(b1Arr.length()) { i -> b1Arr.getDouble(i).toFloat() }

                    val w2Arr = root.getJSONArray("W2")
                    w2 = Array(w2Arr.length()) { r ->
                        val row = w2Arr.getJSONArray(r)
                        FloatArray(row.length()) { c -> row.getDouble(c).toFloat() }
                    }
                    val b2Arr = root.getJSONArray("b2")
                    b2 = FloatArray(b2Arr.length()) { i -> b2Arr.getDouble(i).toFloat() }
                }
            }
        } catch (_: Exception) {
            // Fallback if assets are unavailable in pure unit test without context
        } finally {
            seedBuiltInKnownPackages()
            isLoaded = true
        }
    }

    private fun tryLoadBinaryTfliteTensors(
        bytes: ByteArray,
        expectedFeatures: Int,
        expectedHidden: Int,
        expectedClasses: Int
    ): Boolean {
        if (bytes.size < 20) return false
        if (bytes[0] != 'T'.code.toByte() || bytes[1] != 'F'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != '3'.code.toByte()
        ) {
            return false
        }
        val buf = ByteBuffer.wrap(bytes, 4, bytes.size - 4).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.int
        val numFeat = buf.int
        val numHid = buf.int
        val numCls = buf.int
        if (version != 3 || numFeat != expectedFeatures || numHid != expectedHidden || numCls != expectedClasses) {
            return false
        }
        idf = FloatArray(numFeat) { buf.float }
        w1 = Array(numFeat) { FloatArray(numHid) { buf.float } }
        b1 = FloatArray(numHid) { buf.float }
        w2 = Array(numHid) { FloatArray(numCls) { buf.float } }
        b2 = FloatArray(numCls) { buf.float }
        return true
    }

    private fun seedBuiltInKnownPackages() {
        val defaults = mapOf(
            "com.android.chrome" to "Browsers",
            "org.mozilla.firefox" to "Browsers",
            "com.brave.browser" to "Browsers",
            "com.opera.browser" to "Browsers",
            "com.microsoft.emmx" to "Browsers",
            "com.duckduckgo.mobile.android" to "Browsers",
            "com.google.android.youtube" to "Video & Streaming",
            "com.netflix.mediaclient" to "Video & Streaming",
            "in.startv.hotstar" to "Video & Streaming",
            "org.videolan.vlc" to "Video & Streaming",
            "com.whatsapp" to "Messaging",
            "org.telegram.messenger" to "Messaging",
            "org.thoughtcrime.securesms" to "Messaging",
            "com.google.android.apps.messaging" to "Messaging",
            "com.discord" to "Messaging",
            "com.instagram.android" to "Social",
            "com.facebook.katana" to "Social",
            "com.twitter.android" to "Social",
            "com.reddit.frontpage" to "Social",
            "com.linkedin.android" to "Social",
            "com.google.android.apps.nbu.paisa.user" to "UPI & Payments",
            "com.phonepe.app" to "UPI & Payments",
            "net.one97.paytm" to "UPI & Payments",
            "in.org.npci.upiapp" to "UPI & Payments",
            "com.dreamplug.androidapp" to "UPI & Payments",
            "com.sbi.lotusintouch" to "Banking",
            "com.snapwork.hdfc" to "Banking",
            "com.csam.icici.bank.imobile" to "Banking",
            "com.axis.mobile" to "Banking",
            "com.zerodha.kite3" to "Banking",
            "in.amazon.mShop.android.shopping" to "Shopping",
            "com.amazon.mShop.android.shopping" to "Shopping",
            "com.flipkart.android" to "Shopping",
            "com.myntra.android" to "Shopping",
            "in.swiggy.android" to "Shopping",
            "com.application.zomato" to "Shopping",
            "com.google.android.gm" to "Productivity",
            "com.google.android.calendar" to "Productivity",
            "com.google.android.keep" to "Productivity",
            "notion.id" to "Productivity",
            "md.obsidian" to "Productivity",
            "com.duolingo" to "Education",
            "org.khanacademy.android" to "Education",
            "org.wikipedia" to "Education",
            "com.google.android.apps.photos" to "Photography",
            "com.google.android.GoogleCamera" to "Photography",
            "com.NikSoftware.Snapseed" to "Photography",
            "com.spotify.music" to "Music & Audio",
            "com.google.android.apps.youtube.music" to "Music & Audio",
            "com.github.android" to "Technology & Development",
            "com.termux" to "Technology & Development",
            "com.google.android.calculator" to "Utilities",
            "com.google.android.deskclock" to "Utilities",
            "com.google.android.apps.maps" to "Utilities",
            "com.android.settings" to "System Apps",
            "com.android.vending" to "System Apps",
            "com.google.android.dialer" to "System Apps",
            "com.android.contacts" to "System Apps"
        )
        for ((k, v) in defaults) {
            knownPackages.putIfAbsent(k.lowercase(), v)
        }
    }

    /**
     * Loads user corrections from Room so manual overrides and token weights are respected.
     */
    @Synchronized
    fun syncUserCorrections(corrections: List<AIUserCorrectionEntity>) {
        userPackageOverrides.clear()
        userTokenBoosts.clear()
        for (corr in corrections) {
            userPackageOverrides[corr.packageName.lowercase()] = corr.correctedCategoryId
            val tokens = corr.extractedTokensCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (tok in tokens) {
                val catMap = userTokenBoosts.getOrPut(tok) { mutableMapOf() }
                catMap[corr.correctedCategoryId] = (catMap[corr.correctedCategoryId] ?: 0f) + 0.35f
            }
        }
    }

    /**
     * Extracts word tokens for user correction learning and feature inspection.
     */
    fun extractCleanTokens(packageName: String, appName: String): List<String> {
        val combined = "${packageName.replace('.', ' ').replace('_', ' ')} $appName".lowercase()
        val sb = StringBuilder(combined.length)
        for (ch in combined) {
            sb.append(if (ch.isLetterOrDigit()) ch else ' ')
        }
        return sb.toString()
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_TOKENS }
            .distinct()
    }

    /**
     * Runs the complete 8-step hybrid offline classification pipeline.
     */
    fun classifyApp(
        packageName: String,
        appName: String,
        isSystemApp: Boolean = false,
        androidCategoryHint: Int = -1
    ): ClassificationPrediction {
        loadEmbeddedModel()
        val normalizedPkg = packageName.lowercase().trim()

        // Step 1A: User manual override
        val manualCatId = userPackageOverrides[normalizedPkg]
        if (manualCatId != null) {
            val cat = DefaultCategory.fromId(manualCatId)
            return ClassificationPrediction(
                categoryId = manualCatId,
                categoryName = cat?.displayName ?: manualCatId,
                confidence = 1.0f,
                source = "USER_OVERRIDE"
            )
        }

        // Step 1B: Known local package database exact match
        val knownCatName = knownPackages[normalizedPkg]
        if (knownCatName != null) {
            val cat = DefaultCategory.fromDisplayName(knownCatName)
            return ClassificationPrediction(
                categoryId = cat.id,
                categoryName = cat.displayName,
                confidence = 0.98f,
                source = "KNOWN_DB"
            )
        }

        // Step 2 & 3: Extract features, run Neural Softmax + TF-IDF + Metadata + Keyword Ensemble
        val scores = FloatArray(DefaultCategory.entries.size) { 0.01f }
        // Exclude ALL_APPS (index 0) and UNCATEGORIZED (index 17) from raw scoring
        scores[0] = -10f
        scores[17] = -10f

        val tokens = extractCleanTokens(packageName, appName)

        // 3A. Neural Softmax forward pass over trained .tflite / JSON tensors
        if (vocabIndex.isNotEmpty() && w1.isNotEmpty() && w2.isNotEmpty()) {
            val sparseFeatures = extractModelFeatures(packageName, appName, isSystemApp)
            val sparseVec = mutableMapOf<Int, Float>()
            var normSq = 0f
            for ((featKey, rawVal) in sparseFeatures) {
                val idx = vocabIndex[featKey]
                if (idx != null && idx < idf.size) {
                    val weighted = rawVal * idf[idx]
                    sparseVec[idx] = weighted
                    normSq += weighted * weighted
                }
            }
            if (sparseVec.isNotEmpty() && normSq > 0f) {
                val norm = sqrt(normSq)
                val hidden = FloatArray(hiddenDim) { b1.getOrElse(it) { 0f } }
                for ((fIdx, fVal) in sparseVec) {
                    val normalizedVal = fVal / norm
                    val row = w1[fIdx]
                    for (h in 0 until hiddenDim) {
                        hidden[h] += normalizedVal * row[h]
                    }
                }
                val logits = FloatArray(categories.size) { b2.getOrElse(it) { 0f } }
                for (h in 0 until hiddenDim) {
                    val relu = max(0f, hidden[h])
                    if (relu > 0f) {
                        val row = w2[h]
                        for (c in logits.indices) {
                            logits[c] += relu * row[c]
                        }
                    }
                }
                var maxLogit = Float.NEGATIVE_INFINITY
                for (c in 1 until minOf(logits.size, 17)) {
                    if (logits[c] > maxLogit) maxLogit = logits[c]
                }
                var sumExp = 0f
                val probs = FloatArray(logits.size)
                for (c in 1 until minOf(logits.size, 17)) {
                    val e = exp((logits[c] - maxLogit).toDouble()).toFloat()
                    probs[c] = e
                    sumExp += e
                }
                if (sumExp > 0f) {
                    for (c in 1 until minOf(probs.size, scores.size - 1)) {
                        scores[c] += (probs[c] / sumExp) * 1.45f
                    }
                }
            }
        }

        // 3B. Domain & Metadata Keyword Signals
        applyDomainAndMetadataSignals(tokens, normalizedPkg, isSystemApp, androidCategoryHint, scores)

        // 3C. Learned User Correction Token Boosts
        for (tok in tokens) {
            val boosts = userTokenBoosts[tok] ?: continue
            for ((catId, boostVal) in boosts) {
                val idx = DefaultCategory.entries.indexOfFirst { it.id == catId }
                if (idx in 1..16) {
                    scores[idx] += boostVal
                }
            }
        }

        // Find top-1 and top-2 categories
        var bestIdx = 17
        var secondIdx = 17
        var bestScore = -1f
        var secondScore = -1f
        var totalPositive = 0f

        for (i in 1..16) {
            val s = max(0f, scores[i])
            totalPositive += s
            if (s > bestScore) {
                secondScore = bestScore
                secondIdx = bestIdx
                bestScore = s
                bestIdx = i
            } else if (s > secondScore) {
                secondScore = s
                secondIdx = i
            }
        }

        val calibratedConfidence = if (totalPositive > 0.05f) {
            val ratio = bestScore / totalPositive
            val margin = bestScore - max(0f, secondScore)
            (0.42f + ratio * 0.45f + margin * 0.25f).coerceIn(0.15f, 0.97f)
        } else {
            0.22f
        }

        val bestCategory = DefaultCategory.entries[bestIdx]
        val secondCategory = DefaultCategory.entries.getOrNull(secondIdx)

        // Step 4 & Confidence Threshold Gate:
        // If confidence is below threshold, place in Uncategorized and offer bestCategory as a suggestion
        return if (calibratedConfidence >= CONFIDENCE_THRESHOLD) {
            ClassificationPrediction(
                categoryId = bestCategory.id,
                categoryName = bestCategory.displayName,
                confidence = calibratedConfidence,
                suggestedCategoryId = secondCategory?.takeIf { secondScore > 0.25f }?.id,
                suggestedCategoryName = secondCategory?.takeIf { secondScore > 0.25f }?.displayName,
                source = "NEURAL_TFLITE"
            )
        } else {
            ClassificationPrediction(
                categoryId = DefaultCategory.UNCATEGORIZED.id,
                categoryName = DefaultCategory.UNCATEGORIZED.displayName,
                confidence = calibratedConfidence,
                suggestedCategoryId = bestCategory.id,
                suggestedCategoryName = bestCategory.displayName,
                source = "UNCERTAIN_SUGGESTION"
            )
        }
    }

    private fun extractModelFeatures(pkg: String, label: String, isSystem: Boolean): Map<String, Float> {
        val tokens = extractCleanTokens(pkg, label)
        val map = HashMap<String, Float>(tokens.size * 4)
        for (tok in tokens) {
            val wKey = "w:$tok"
            map[wKey] = (map[wKey] ?: 0f) + 1.5f
            if (tok.length >= 4) {
                for (i in 0..(tok.length - 3)) {
                    val ng = "c3:${tok.substring(i, i + 3)}"
                    map[ng] = (map[ng] ?: 0f) + 0.5f
                }
            }
        }
        if (isSystem) {
            map["meta:system_app"] = 2.5f
        }
        return map
    }

    private fun applyDomainAndMetadataSignals(
        tokens: List<String>,
        pkg: String,
        isSystemApp: Boolean,
        androidCategoryHint: Int,
        scores: FloatArray
    ) {
        val tokenSet = tokens.toSet()

        fun boost(cat: DefaultCategory, amount: Float) {
            scores[cat.ordinal] += amount
        }

        // Android 8.0+ ApplicationInfo.category hints
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (androidCategoryHint) {
                ApplicationInfo.CATEGORY_GAME -> boost(DefaultCategory.GAMES, 1.35f)
                ApplicationInfo.CATEGORY_AUDIO -> boost(DefaultCategory.MUSIC_AUDIO, 1.25f)
                ApplicationInfo.CATEGORY_VIDEO -> boost(DefaultCategory.VIDEO_STREAMING, 1.25f)
                ApplicationInfo.CATEGORY_IMAGE -> boost(DefaultCategory.PHOTOGRAPHY, 1.20f)
                ApplicationInfo.CATEGORY_SOCIAL -> boost(DefaultCategory.SOCIAL, 1.20f)
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> boost(DefaultCategory.PRODUCTIVITY, 1.15f)
                ApplicationInfo.CATEGORY_MAPS -> boost(DefaultCategory.UTILITIES, 1.05f)
            }
        }

        // High-precision semantic token buckets
        val keywordRules: Map<DefaultCategory, Set<String>> = mapOf(
            DefaultCategory.BROWSERS to setOf("browser", "chrome", "firefox", "brave", "opera", "vivaldi", "duckduckgo", "webview", "sbrowser", "kiwibrowser"),
            DefaultCategory.INTERNET to setOf("vpn", "proxy", "wireguard", "tailscale", "dns", "speedtest", "wifi", "ping", "network", "cloudflare", "tor"),
            DefaultCategory.GAMES to setOf("game", "games", "rpg", "arcade", "puzzle", "chess", "clash", "brawl", "minecraft", "roblox", "pubg", "genshin", "shooter", "racing", "casino"),
            DefaultCategory.VIDEO_STREAMING to setOf("video", "stream", "streaming", "youtube", "netflix", "hotstar", "primevideo", "vlc", "cinema", "movie", "movies", "tv", "twitch", "plex", "crunchyroll"),
            DefaultCategory.SOCIAL to setOf("social", "instagram", "facebook", "twitter", "reddit", "linkedin", "pinterest", "snapchat", "tiktok", "threads", "bluesky", "mastodon", "community"),
            DefaultCategory.MESSAGING to setOf("messenger", "whatsapp", "telegram", "signal", "chat", "sms", "mms", "discord", "slack", "viber", "orca", "messaging"),
            DefaultCategory.BANKING to setOf("bank", "banking", "sbi", "hdfc", "icici", "axis", "kotak", "yono", "chase", "revolut", "finance", "zerodha", "kite", "groww", "stocks", "mutual", "trading", "loan"),
            DefaultCategory.UPI_PAYMENTS to setOf("upi", "pay", "payment", "payments", "paisa", "phonepe", "paytm", "bhim", "gpay", "wallet", "cred", "paypal", "mobikwik", "freecharge", "cashapp"),
            DefaultCategory.SHOPPING to setOf("shop", "shopping", "store", "mall", "amazon", "flipkart", "myntra", "meesho", "swiggy", "zomato", "blinkit", "grocery", "ebay", "aliexpress", "cart", "mart"),
            DefaultCategory.PRODUCTIVITY to setOf("mail", "gmail", "email", "calendar", "notes", "keep", "docs", "drive", "notion", "obsidian", "todo", "todoist", "office", "word", "excel", "powerpoint", "pdf", "scanner", "tasks"),
            DefaultCategory.EDUCATION to setOf("learn", "education", "study", "course", "courses", "duolingo", "khan", "coursera", "udemy", "anki", "flashcards", "wikipedia", "dictionary", "math", "exam", "school", "tutor"),
            DefaultCategory.PHOTOGRAPHY to setOf("photo", "photos", "camera", "gallery", "lightroom", "snapseed", "canva", "vsco", "picsart", "selfie", "wallpaper", "editor", "lens"),
            DefaultCategory.MUSIC_AUDIO to setOf("music", "audio", "spotify", "podcast", "podcasts", "saavn", "gaana", "audible", "mp3", "equalizer", "radio", "shazam", "player", "beats", "sound"),
            DefaultCategory.TECH_DEV to setOf("github", "git", "termux", "terminal", "code", "ide", "developer", "ssh", "adb", "shizuku", "fdroid", "python", "compiler", "chatgpt", "gemini", "ai"),
            DefaultCategory.UTILITIES to setOf("calculator", "clock", "alarm", "files", "filemanager", "explorer", "maps", "weather", "compass", "flashlight", "authenticator", "bitwarden", "password", "vault", "rar", "zip", "backup", "cleaner")
        )

        for ((category, keywords) in keywordRules) {
            var matchCount = 0
            for (tok in tokenSet) {
                if (tok in keywords) {
                    matchCount++
                }
            }
            if (matchCount > 0) {
                boost(category, 0.85f + (matchCount - 1) * 0.45f)
            }
        }

        if (isSystemApp) {
            // Only default to System Apps if no strong specialized user-facing category matched
            val maxSpecialized = (1..15).maxOf { scores[it] }
            if (maxSpecialized < 0.75f || pkg.startsWith("com.android.system") || pkg == "com.android.settings" || pkg == "com.android.vending") {
                boost(DefaultCategory.SYSTEM_APPS, 1.35f)
            } else {
                boost(DefaultCategory.SYSTEM_APPS, 0.35f)
            }
        }
    }
}
